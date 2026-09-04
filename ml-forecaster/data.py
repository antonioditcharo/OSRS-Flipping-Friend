"""Leak-free features and labels for the price forecaster.

This replaces ``dataset.py``, which could not be repaired in place because the fault was not in any
one line. Five separate defects, each of which invalidates the stage after it:

**The target leaked.**  ``price_change.ewm(span=5, adjust=False).mean().shift(-1)`` is a *trailing*
exponential mean shifted back by one, so the label is a weighted average reaching backwards over the
price changes that precede it. Against the window the old pipeline paired it with, **44% of the
label's weight sits on bars the model is handed as input** — 15% of it on the very last bar of the
window — and only 56% on the future the model is supposed to be predicting. Nearly half the answer
was inside the question. A model that learned to copy its inputs would have scored well, and nothing
in the training run could have told the difference.

**The prices were back-filled.**  ``.replace(0, pd.NA).ffill().bfill()`` fills a missing price from
the *next* observation when there is no earlier one. That is the future arriving inside the feature
window, quietly, on exactly the thin items where prices go missing.

**Nothing was scaled.**  Volume (10²–10⁶) sat beside price change (~10⁻³) and volatility (~10⁻³) with
no normalisation. An LSTM input gate fed that sees one feature and three rounding errors.

**Time of day was a ramp.**  ``hour / 24`` runs 0.958 → 0.0 across midnight, so the model was told
that 23:00 and 00:00 are the two furthest-apart hours of the day.

**The fallback fabricated a clock.**  When the API returned nothing, ``timestamp`` became
``range(len(prices))``, making ``time_of_day`` the hour of epoch second 0, 1, 2 … — a feature
computed from the row number.

What is here instead: forward-only labels with a gap, no back-fill, volume log-transformed, cyclical
time, and standardisation whose statistics come from the training fold alone.
"""

import json
import math
import os
import time
from typing import Dict, List, Optional, Tuple

import numpy as np
import requests

from wiki_api import BASE, HEADERS

CACHE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cache")

#: Bars of history the model sees. 36 five-minute bars is three hours, which is about as far back as
#: a five-minute bar carries information about the next hour on this data.
WINDOW = 36

#: Bars ahead the label looks. 12 is one hour: long enough to be a flip leg, short enough that the
#: label is still about the same market.
HORIZON = 12

#: Requests per second against the wiki. Their only ask is a descriptive User-Agent; not hammering
#: them is ours.
REQUEST_DELAY_SECONDS = 0.25

#: Units that must cross in a five-minute bar before an item counts as traded rather than sighted.
MIN_BAR_VOLUME = 40

#: Below this the two percent tax eats any edge a forecast could find, so modelling it is wasted.
MIN_PRICE = 100


def _cache_path(name: str) -> str:
    os.makedirs(CACHE_DIR, exist_ok=True)
    return os.path.join(CACHE_DIR, name)


def liquid_item_ids(count: int) -> List[int]:
    """The busiest items by five-minute volume, which is where a forecast is worth having."""
    path = _cache_path("liquid_items.json")
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as handle:
            cached = json.load(handle)
        if len(cached) >= count:
            return cached[:count]

    response = requests.get(f"{BASE}/5m", headers=HEADERS, timeout=30)
    response.raise_for_status()
    data = response.json().get("data", {})

    scored = []
    for item_id, bar in data.items():
        if not isinstance(bar, dict):
            continue
        volume = (bar.get("highPriceVolume") or 0) + (bar.get("lowPriceVolume") or 0)
        high = bar.get("avgHighPrice") or 0
        # Both tests, because either one alone picks the wrong market. Ranking on turnover selects
        # whales: a 112m item that trades once in five minutes outranks a 500 gp item that trades
        # five thousand times, and one print is not a price series. Ranking on unit count selects
        # feathers, where no forecast can pay the tax. So: a real flow of units, at a price worth
        # flipping, ordered by the turnover that makes an item worth modelling.
        if volume >= MIN_BAR_VOLUME and high >= MIN_PRICE:
            scored.append((volume * high, int(item_id)))

    scored.sort(reverse=True)
    ids = [item_id for _, item_id in scored[:max(count, 500)]]
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(ids, handle)
    return ids[:count]


def fetch_series(item_id: int) -> List[dict]:
    """Five-minute bars for one item, cached on disk so a rerun costs nothing."""
    path = _cache_path(f"series_{item_id}.json")
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle)

    response = requests.get(
        f"{BASE}/timeseries", params={"timestep": "5m", "id": item_id},
        headers=HEADERS, timeout=30)
    response.raise_for_status()
    bars = response.json().get("data", [])
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(bars, handle)
    time.sleep(REQUEST_DELAY_SECONDS)
    return bars


def load_fixture(path: str) -> List[dict]:
    """The checked-in wiki candles the Java calibration test scores against. No volume column."""
    bars = []
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#") or line.startswith("timestamp"):
                continue
            timestamp, high, low = line.split(",")
            bars.append({
                "timestamp": int(timestamp),
                "avgHighPrice": int(high),
                "avgLowPrice": int(low),
                "highPriceVolume": 0,
                "lowPriceVolume": 0,
            })
    return bars


def _clean(bars: List[dict]) -> Optional[Dict[str, np.ndarray]]:
    """Bars to arrays, carrying gaps forward only.

    A missing print is filled from the last one *before* it and never from the one after: a
    back-fill is the future leaking into the window, and it leaks hardest on thin items where
    prices go missing most.
    """
    if not bars:
        return None

    times, highs, lows, volumes = [], [], [], []
    last_high = None
    last_low = None
    for bar in bars:
        high = bar.get("avgHighPrice") or 0
        low = bar.get("avgLowPrice") or 0
        high = high if high > 0 else last_high
        low = low if low > 0 else last_low
        if high is None or low is None:
            # Nothing has traded yet, so there is nothing to carry forward. Drop rather than invent.
            continue
        last_high, last_low = high, low
        times.append(bar["timestamp"])
        highs.append(float(high))
        lows.append(float(low))
        volumes.append(float((bar.get("highPriceVolume") or 0) + (bar.get("lowPriceVolume") or 0)))

    if len(times) < WINDOW + HORIZON + 2:
        return None
    return {
        "time": np.array(times, dtype=np.int64),
        "high": np.array(highs),
        "low": np.array(lows),
        "volume": np.array(volumes),
    }


FEATURE_NAMES = [
    "log_return",
    "spread",
    "log_volume",
    "volatility",
    "distance_from_median",
    "sin_hour",
    "cos_hour",
]


def _features(series: Dict[str, np.ndarray]) -> np.ndarray:
    """Per-bar features, every one of them computed from that bar and earlier ones only."""
    high = series["high"]
    n = len(high)

    log_return = np.zeros(n)
    log_return[1:] = np.log(high[1:] / high[:-1])

    # The book's own width, which says how much of a move is real and how much is the spread.
    spread = (high - series["low"]) / np.maximum(high, 1.0)

    # Volume spans four orders of magnitude across items and two within one. The log is what makes
    # "twice as busy as usual" the same distance everywhere.
    log_volume = np.log1p(series["volume"])

    volatility = np.zeros(n)
    for i in range(1, n):
        lo = max(0, i - 12)
        volatility[i] = np.std(log_return[lo:i + 1])

    # Where the price sits against its own recent level, which is the quantity a mean-reverting
    # model acts on. Trailing median, so nothing after i is used.
    distance = np.zeros(n)
    for i in range(n):
        lo = max(0, i - 48)
        median = np.median(high[lo:i + 1])
        distance[i] = math.log(high[i] / median) if median > 0 else 0.0

    hours = (series["time"] % 86400) / 86400.0
    sin_hour = np.sin(2 * math.pi * hours)
    cos_hour = np.cos(2 * math.pi * hours)

    return np.column_stack(
        [log_return, spread, log_volume, volatility, distance, sin_hour, cos_hour])


def build_windows(bars: List[dict], item_index: int) -> Optional[Tuple[np.ndarray, ...]]:
    """Sequences, labels, item ids and label times for one item.

    The label is ``log(high[t+HORIZON] / high[t])`` — forward only, unsmoothed, and containing no
    value the feature window has seen. Smoothing it, which is what the old pipeline did, is what put
    two thirds of the answer into the question.
    """
    series = _clean(bars)
    if series is None:
        return None

    features = _features(series)
    high = series["high"]
    times = series["time"]
    n = len(high)

    windows, labels, label_times, anchors = [], [], [], []
    for end in range(WINDOW, n - HORIZON):
        future = high[end + HORIZON - 1]
        now = high[end - 1]
        if now <= 0 or future <= 0:
            continue
        windows.append(features[end - WINDOW:end])
        labels.append(math.log(future / now))
        label_times.append(times[end + HORIZON - 1])
        anchors.append(times[end - 1])

    if not windows:
        return None
    return (
        np.array(windows, dtype=np.float32),
        np.array(labels, dtype=np.float32),
        np.full(len(windows), item_index, dtype=np.int64),
        np.array(anchors, dtype=np.int64),
        np.array(label_times, dtype=np.int64),
    )


def assemble(item_ids: List[int], fixtures: Optional[List[str]] = None):
    """Every item's windows, concatenated and sorted by the time the features end.

    Sorted by anchor time because every split downstream is a split in time. A shuffled split on
    overlapping windows is not a test, it is a memory check.
    """
    all_x, all_y, all_item, all_anchor, all_label_time = [], [], [], [], []
    index = 0
    for item_id in item_ids:
        try:
            bars = fetch_series(item_id)
        except Exception as failed:  # noqa: BLE001 - one bad item must not end the run
            print(f"  skipped {item_id}: {failed}")
            continue
        built = build_windows(bars, index)
        if built is None:
            continue
        x, y, item, anchor, label_time = built
        all_x.append(x)
        all_y.append(y)
        all_item.append(item)
        all_anchor.append(anchor)
        all_label_time.append(label_time)
        index += 1

    for path in fixtures or []:
        built = build_windows(load_fixture(path), index)
        if built is None:
            continue
        x, y, item, anchor, label_time = built
        all_x.append(x)
        all_y.append(y)
        all_item.append(item)
        all_anchor.append(anchor)
        all_label_time.append(label_time)
        index += 1

    if not all_x:
        raise RuntimeError("no usable series")

    x = np.concatenate(all_x)
    y = np.concatenate(all_y)
    item = np.concatenate(all_item)
    anchor = np.concatenate(all_anchor)
    label_time = np.concatenate(all_label_time)

    order = np.argsort(anchor, kind="stable")
    return x[order], y[order], item[order], anchor[order], label_time[order], index


def standardise(train_x: np.ndarray, *others: np.ndarray):
    """Centre and scale using the training fold's statistics, and nobody else's.

    Fitting a scaler on everything and then splitting is the most common way a time-series result
    turns out to be nothing: the test fold's mean and spread are part of what the model was told.
    """
    flat = train_x.reshape(-1, train_x.shape[-1])
    mean = flat.mean(axis=0)
    std = flat.std(axis=0)
    std[std < 1e-8] = 1.0

    def apply(values: np.ndarray) -> np.ndarray:
        return ((values - mean) / std).astype(np.float32)

    return (apply(train_x),) + tuple(apply(other) for other in others) + (mean, std)
