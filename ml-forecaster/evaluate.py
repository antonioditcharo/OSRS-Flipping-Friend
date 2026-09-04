"""Walk-forward evaluation with a purge, an embargo, and baselines that have to be beaten.

The old training script had a train/validation split and nothing else. The validation fold chose
when to stop, and its loss was then reported as the result — so the one number anybody saw had been
optimised against. There was no test fold and no baseline of any kind, and the loss was LogCosh on a
target whose mean is approximately zero, which means **predicting zero scores well**. A model that
had learned nothing at all would have produced a small, respectable-looking number.

Three things are needed before a forecast means anything, and none of them was present.

**A fold the model has never touched.** Here: rolling origins, each training on everything before a
point in time and scoring on what follows.

**A purge and an embargo.** A label reaches ``HORIZON`` bars past its features, so a training sample
taken right up to the split has already seen into the test period. Those samples are dropped
(purged), and a further gap is left (embargoed) so that overlapping windows either side of the
boundary do not share bars. Without this the test fold is contaminated by construction, and the
contamination flatters exactly the short horizons a flipper cares about.

**Baselines.** Three, because each one kills a different illusion:

- *zero* — the random walk. If the model cannot beat "the price will be what it is now" it has
  learned nothing about direction, whatever its loss looks like.
- *persistence* — last hour's move continues. Beats zero on trending items and loses on reverting
  ones; a model that only learned momentum will not beat it.
- *reversion* — a pull back toward the trailing median, which is the rule
  ``com.flippingfriend.model.PriceForecast`` already uses in production and which is already
  measured there (2-hour mean absolute error 0.0298 against a random walk's 0.0317). This is the
  incumbent. Anything that cannot beat it has no business being wired in.

Direction is reported alongside error because they are different questions and a flipper acts on the
first. A model can win on mean absolute error by shrinking every prediction toward zero while being
no better than a coin at saying which way.
"""

from typing import Callable, Dict, List

import numpy as np

import data


def _metrics(name: str, predicted: np.ndarray, actual: np.ndarray) -> Dict[str, float]:
    error = predicted - actual
    # Direction is only defined where both sides have one. A forecast of exactly zero is not a wrong
    # call about which way the price goes, it is a refusal to make one — scoring it as wrong reported
    # the random walk at 0% accuracy, which reads like an achievement rather than an abstention.
    called = (np.abs(predicted) > 1e-9) & (np.abs(actual) > 1e-9)
    directional = (float((np.sign(predicted[called]) == np.sign(actual[called])).mean())
                   if called.any() else float("nan"))
    return {
        "name": name,
        "mae": float(np.mean(np.abs(error))),
        "rmse": float(np.sqrt(np.mean(error ** 2))),
        "direction": directional,
        "called": float(called.mean()),
        "predicted_sd": float(np.std(predicted)),
        "n": int(len(actual)),
    }


def baseline_zero(x: np.ndarray) -> np.ndarray:
    """The random walk: the best guess for any horizon is the price right now."""
    return np.zeros(len(x))


def baseline_persistence(x: np.ndarray) -> np.ndarray:
    """The last hour's move, repeated.

    Takes **raw** features, not standardised ones. Scored on standardised inputs this reported a mean
    absolute error of 0.53 against a random walk's 0.015 — not because momentum is thirty-five times
    worse than doing nothing, but because the numbers were in standard deviations while the label was
    in log points. A baseline in the wrong units is not a baseline, it is a distraction that makes
    anything look good.
    """
    # log_return is feature 0; the last HORIZON bars of it sum to the move just gone.
    return x[:, -data.HORIZON:, 0].sum(axis=1)


def baseline_reversion(x: np.ndarray, strength: float = 1.0) -> np.ndarray:
    """A pull back toward the item's own recent level, which is what PriceForecast does.

    Raw features. Feature 4 is the log distance from the trailing median, and the prediction is minus
    that: over the horizon the price gives back its excursion. ``strength`` scales how much of it,
    since a full give-back in one hour is the most aggressive form of the claim and the incumbent
    reverts at a rate fitted per item.
    """
    return -strength * x[:, -1, 4]


def purged_splits(anchor: np.ndarray, label_time: np.ndarray, folds: int, embargo_bars: int):
    """Rolling origins, each with the boundary cleared of samples that straddle it.

    Yields ``(train_index, test_index)``. A training sample survives only if its label landed before
    the test fold opened, plus an embargo — the label reaching past the split is precisely how a
    walk-forward test leaks.
    """
    n = len(anchor)
    embargo_seconds = embargo_bars * 300
    # Origins spread over the second half, so the first model still has half the history to learn on.
    for fold in range(folds):
        split = int(n * (0.5 + 0.5 * fold / folds))
        stop = int(n * (0.5 + 0.5 * (fold + 1) / folds))
        if stop - split < 200:
            continue
        opens_at = anchor[split]
        train = np.where(label_time < opens_at - embargo_seconds)[0]
        train = train[train < split]
        test = np.arange(split, stop)
        if len(train) < 500 or len(test) < 100:
            continue
        yield train, test


def score_baselines(raw_x: np.ndarray, y: np.ndarray) -> List[Dict[str, float]]:
    """Every baseline reads raw features, because the label is in raw units."""
    return [
        _metrics("zero", baseline_zero(raw_x), y),
        _metrics("persistence", baseline_persistence(raw_x), y),
        _metrics("reversion", baseline_reversion(raw_x, 1.0), y),
        _metrics("reversion x0.3", baseline_reversion(raw_x, 0.3), y),
    ]


def report(rows: List[Dict[str, float]]) -> str:
    lines = [f"{'model':<16}{'MAE':>10}{'RMSE':>10}{'direction':>11}{'called':>9}"
             f"{'pred sd':>10}{'n':>8}"]
    for row in rows:
        direction = ("     n/a" if row["direction"] != row["direction"]
                     else f"{row['direction'] * 100:>7.1f}%")
        lines.append(
            f"{row['name']:<16}{row['mae']:>10.5f}{row['rmse']:>10.5f}{direction:>11}"
            f"{row['called'] * 100:>8.0f}%{row['predicted_sd']:>10.5f}{row['n']:>8}")
    return "\n".join(lines)


def pooled(results: List[List[Dict[str, float]]]) -> List[Dict[str, float]]:
    """One row per model, weighted by fold size, so a large fold is not equal to a small one."""
    by_name: Dict[str, List[Dict[str, float]]] = {}
    for fold in results:
        for row in fold:
            by_name.setdefault(row["name"], []).append(row)

    out = []
    for name, rows in by_name.items():
        total = sum(row["n"] for row in rows)
        called = [row for row in rows if row["direction"] == row["direction"]]
        called_total = sum(row["n"] for row in called)
        out.append({
            "name": name,
            "mae": sum(row["mae"] * row["n"] for row in rows) / total,
            "rmse": sum(row["rmse"] * row["n"] for row in rows) / total,
            "direction": (sum(row["direction"] * row["n"] for row in called) / called_total
                          if called_total else float("nan")),
            "called": sum(row["called"] * row["n"] for row in rows) / total,
            "predicted_sd": sum(row["predicted_sd"] * row["n"] for row in rows) / total,
            "n": total,
        })
    return out


def evaluate(x: np.ndarray, y: np.ndarray, anchor: np.ndarray, label_time: np.ndarray,
             items: np.ndarray, item_count: int,
             fit_predict: Callable[..., np.ndarray], folds: int = 4,
             embargo_bars: int = data.HORIZON) -> List[Dict[str, float]]:
    """Runs the baselines and one candidate model across every purged fold."""
    results = []
    for fold, (train, test) in enumerate(
            purged_splits(anchor, label_time, folds, embargo_bars)):
        train_x, test_x, mean, std = data.standardise(x[train], x[test])
        # Baselines on the raw fold, the model on the standardised one. Both are scored against the
        # same untouched labels.
        rows = score_baselines(x[test], y[test])
        predicted = fit_predict(train_x, y[train], items[train], test_x, items[test], item_count)
        if predicted is not None:
            rows.append(_metrics("sequence", predicted, y[test]))
        print(f"\nfold {fold}: train {len(train)}, test {len(test)}")
        print(report(rows))
        results.append(rows)
    return results
