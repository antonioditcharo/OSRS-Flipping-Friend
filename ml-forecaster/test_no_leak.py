"""The label must not contain the answer.

This is the most important test in the ML directory, and it is a test rather than a code review
because the leak that was here was invisible to review: ``price_change.ewm(span=5,
adjust=False).mean().shift(-1)`` reads like "the smoothed next move" and is in fact a weighted
average that reaches *backwards* into the feature window. Against the window it was paired with, 44%
of the label's weight sat on bars the model was handed as input and 15% of it on the very last bar of
that window — nearly half the answer inside the question.

Nothing in the old training run could have caught that. The loss went down, the validation loss went
down with it, and both were measuring how well the model could copy a number it had been given. The
only way to see it is to look at the label and the features together, which is what happens here.

Run directly (``python test_no_leak.py``) or under pytest.
"""

import math

import numpy as np

import data


def _synthetic(n: int = 400, seed: int = 0):
    """A random walk with a clock. No structure to find, which is what makes leakage visible."""
    rng = np.random.default_rng(seed)
    price = 1000.0
    bars = []
    for i in range(n):
        price *= math.exp(rng.normal(0, 0.004))
        bars.append({
            "timestamp": 1_700_000_000 + i * 300,
            "avgHighPrice": int(round(price)),
            "avgLowPrice": int(round(price * 0.995)),
            "highPriceVolume": int(rng.integers(50, 500)),
            "lowPriceVolume": int(rng.integers(50, 500)),
        })
    return bars


ALPHA = 2.0 / (5 + 1)
#: Rows between the last bar of the old feature window and the bar its label was centred on.
#: ``preprocess_data`` took ``X = rows[i : i+w]`` and ``y = target[i+w]``, and ``target`` was already
#: shifted back by one — so the label's newest ingredient sat two rows past the window's last bar.
OLD_LABEL_OFFSET = 2


def _old_label_weights(depth: int = 200):
    """How the old label's weight was distributed over the bars it averaged.

    Returned as ``(after_window, inside_window)``. The arithmetic rather than a correlation, because
    a correlation on one synthetic series can be argued with and this cannot: with
    ``s[j] = a*x[j] + (1-a)*s[j-1]`` the label is ``sum_k a(1-a)^k x[j-k]``, and everything from
    ``k = OLD_LABEL_OFFSET`` onwards names a bar the model was shown.
    """
    after = sum(ALPHA * (1 - ALPHA) ** k for k in range(OLD_LABEL_OFFSET))
    inside = sum(ALPHA * (1 - ALPHA) ** k for k in range(OLD_LABEL_OFFSET, depth))
    return after, inside


def test_the_old_label_contained_its_own_features():
    """Demonstrates the leak, so the fix below is measured against something rather than asserted."""
    after, inside = _old_label_weights()

    assert abs(after + inside - 1.0) < 1e-6, "the weights must be a distribution"
    assert inside > 0.4, (
        f"the old label drew {inside:.1%} of its weight from inside the feature window; if this "
        "figure has moved, the reproduction has drifted from the original rule")
    largest_inside = ALPHA * (1 - ALPHA) ** OLD_LABEL_OFFSET
    assert largest_inside > 0.14, (
        f"the single largest in-window ingredient was {largest_inside:.1%}, on the last bar the "
        "model was shown")


def test_the_label_is_strictly_in_the_future():
    """The fix. On a random walk the label must be unrelated to anything inside the window."""
    bars = _synthetic()
    built = data.build_windows(bars, 0)
    assert built is not None
    windows, labels, _, _, _ = built

    # Feature 0 is log_return. Every bar of the window is checked, not only the last: a leak that
    # reached back three bars would slip past a test that only looked at one.
    worst = 0.0
    for offset in range(windows.shape[1]):
        correlation = abs(np.corrcoef(windows[:, offset, 0], labels)[0, 1])
        worst = max(worst, correlation)

    assert worst < 0.25, (
        f"a label on a random walk must not be predictable from its own window, got {worst:.3f}")


def test_the_label_is_the_move_that_follows_the_window():
    """Says what the label is, in arithmetic, so a future edit cannot quietly redefine it."""
    bars = _synthetic(seed=3)
    built = data.build_windows(bars, 0)
    windows, labels, _, anchors, label_times = built

    highs = {bar["timestamp"]: bar["avgHighPrice"] for bar in bars}
    for index in (0, len(labels) // 2, len(labels) - 1):
        expected = math.log(highs[label_times[index]] / highs[anchors[index]])
        assert abs(labels[index] - expected) < 1e-6, (
            f"label {index} is not log(price at label time / price at anchor)")
        assert label_times[index] > anchors[index], "the label must be after the features"
        gap = (label_times[index] - anchors[index]) // 300
        assert gap == data.HORIZON, f"expected a {data.HORIZON}-bar gap, got {gap}"


def test_prices_are_never_carried_backwards():
    """A back-fill is the future arriving inside the window, and it hits thin items hardest."""
    bars = _synthetic(n=200, seed=5)
    # A hole at the start, before anything has traded, and one in the middle.
    for index in (0, 1, 2, 100, 101):
        bars[index]["avgHighPrice"] = 0
        bars[index]["avgLowPrice"] = 0

    series = data._clean(bars)

    assert series is not None
    # The leading hole is dropped rather than filled from later bars, so the first surviving price is
    # the first one that actually traded.
    assert series["high"][0] == bars[3]["avgHighPrice"]
    # The middle hole carries the last real price forward.
    filled_at = list(series["time"]).index(bars[100]["timestamp"])
    assert series["high"][filled_at] == bars[99]["avgHighPrice"]


def test_scaling_uses_only_the_training_fold():
    """Fitting a scaler on everything and splitting afterwards is a leak wearing a lab coat."""
    train = np.random.default_rng(1).normal(0, 1, (500, 4, 3)).astype(np.float32)
    # A test fold on a completely different scale. If its statistics were used, it would come back
    # centred; if only the training fold's were, it must stay far from zero.
    test = (np.random.default_rng(2).normal(50, 10, (200, 4, 3))).astype(np.float32)

    scaled_train, scaled_test, mean, std = data.standardise(train, test)

    assert abs(scaled_train.mean()) < 0.05, "the training fold should come back centred"
    assert scaled_test.mean() > 5, (
        "the test fold must be scaled by the training fold's statistics, not its own; got "
        f"{scaled_test.mean():.2f}")


def test_time_of_day_does_not_jump_at_midnight():
    """The old feature ran 0.958 -> 0.0 across midnight, placing 23:00 and 00:00 furthest apart."""
    midnight = 1_700_000_000 - (1_700_000_000 % 86400)
    before = midnight - 300
    series = data._clean([
        {"timestamp": t, "avgHighPrice": 1000, "avgLowPrice": 995,
         "highPriceVolume": 10, "lowPriceVolume": 10}
        for t in range(before - 300 * 60, before + 300 * 60, 300)
    ])
    features = data._features(series)
    times = list(series["time"])

    sin_before, cos_before = features[times.index(before)][5:7]
    sin_after, cos_after = features[times.index(midnight)][5:7]
    distance = math.hypot(sin_after - sin_before, cos_after - cos_before)

    assert distance < 0.05, (
        f"five minutes apart must be five minutes apart across midnight too, got {distance:.3f}")


if __name__ == "__main__":
    for name, test in sorted(globals().items()):
        if name.startswith("test_") and callable(test):
            test()
            print(f"ok  {name}")
    print("\nall leakage checks passed")
