# The sequence model, measured

*4 September 2026. Reproduce with `python experiment.py` in this directory's venv.*

## Verdict

**The LSTM earns weight zero and must not be wired in.** Scored out of sample against the analytical
rule it is supposed to improve on, it does not improve on it — as a replacement or as a correction.
Under the gate `LearnedFillModel` already applies to every learned component in this system (beat the
analytical baseline out of sample or be dropped entirely), this model is dropped.

That is a result, not a failure to get one. Before this the question could not be asked, because the
pipeline that would have answered it was measuring a target that contained its own inputs.

## What the old pipeline was measuring

`dataset.py` built its label as `price_change.ewm(span=5, adjust=False).mean().shift(-1)`. That reads
like "the smoothed next move". It is a *trailing* exponential mean shifted back by one, so it reaches
backwards over the bars that precede it — and against the window it was paired with:

| where the label's weight landed | share |
|---|---|
| bars after the window — the future it was meant to predict | 56% |
| **bars inside the feature window — handed to the model as input** | **44%** |
| of which, the single last bar of the window | 15% |

Nearly half the answer was inside the question. Training drove the loss down, validation loss
followed it down, and both were measuring how well the model could copy a number it had been given.
Nothing in the run could have said so: there was no test fold, no baseline, and the loss was LogCosh
on a target whose mean is approximately zero — so *predicting zero* scores well and a model that had
learned nothing at all would have produced a small, respectable number.

Four more defects sat behind that one. Prices were back-filled (`.bfill()` fills a gap from the
*next* observation, which is the future arriving inside the window, and it hits thin items hardest).
Nothing was scaled, so volume at 10²–10⁶ sat beside price change at ~10⁻³ and the input gate saw one
feature. Time of day was `hour / 24`, a ramp running 0.958 → 0.0 across midnight. And the training
loop took **one** `optimizer.step()` per epoch on the full tensor, capped at 100 epochs and
early-stopped at 5 — somewhere between five and a hundred gradient updates in total.

## The measurement

200 liquid items, 63,187 windows, 36 five-minute bars in, 12 bars (one hour) ahead. Four rolling
origins, each trained on everything before a point in time and scored on what follows, with training
samples whose labels reach past the boundary purged and a further hour embargoed. Label standard
deviation 0.041.

| model | MAE | RMSE | direction | pred sd |
|---|---|---|---|---|
| zero (random walk) | 0.01609 | 0.03587 | — | 0 |
| persistence (last hour repeats) | 0.02842 | 0.05974 | **36.1%** | 0.036 |
| reversion, full give-back | 0.01693 | 0.03573 | 68.1% | 0.031 |
| **reversion × 0.3** | **0.01544** | **0.03277** | **68.1%** | 0.009 |
| sequence model, from scratch | 0.01628 | 0.03541 | 57.4% | 0.005 |
| reversion × 0.3 + sequence correction | 0.01552 | 0.03274 | 65.8% | 0.009 |
| *the correction on its own* | — | — | **52.6%** | 0.001 |

Read the last row first. Asked the only question worth its complexity — *what does the analytical
rule get wrong?* — the model calls direction at **52.6%**, which is a coin. Adding it makes mean
absolute error slightly worse and leaves RMSE unchanged, and it drags directional accuracy down from
68.1% to 65.8%. Its predictions have a standard deviation of 0.001 against a residual of 0.03: it has
learned to predict approximately nothing, which is the correct thing to learn when there is nothing
there, and is also evidence the training is working rather than diverging.

## What was worth learning

Two things, and neither of them is the model.

**Momentum is inverted on this market.** Betting that the last hour's move continues is right 36% of
the time — so betting *against* it is right 64%. The same fact from the other side: a price above its
own trailing median falls back **68.1%** of the time over the next hour. That is a large, stable
effect measured across 200 items and four folds, and it is the first direct evidence for the design
decision at the centre of `PriceForecast` — "these are consumables with a production cost and a use,
not equities" — which until now rested on an argument.

**Direction is predictable; magnitude is not.** Reverting the *whole* excursion loses to doing
nothing on mean absolute error (0.01693 against 0.01609) while being directionally right 68% of the
time. Reverting about a third of it beats the random walk on both MAE (−4.0%) and RMSE (−8.6%) with
identical direction. A forecast here should be confident about which way and diffident about how far.

This is *not* a finding that `PriceForecast` over-reverts. The baselines here are crude proxies for
it, at a one-hour horizon on different data; `PriceForecast` fits its reversion rate per item and its
bands are separately measured as well calibrated (50% band holds 54%, 80% holds 81%). What it does
say is that the shape of the effect is real and worth being careful about.

## Caveats a later attempt should know

- **Thirty hours per item.** The wiki's `/timeseries` returns 365 points, so the whole dataset spans
  about a day and a quarter. A sequence model has been given breadth (200 items) and no depth, and
  depth is where a sequence model earns its keep. `PriceArchive` was built to accumulate exactly
  this; the honest time to try again is when it holds months rather than when someone feels like it.
- **One horizon.** One hour. A model might have an edge at four hours and none at one.
- **No order book.** Nobody has one; the feed publishes what traded.
- **The reversion baselines are proxies**, not a port of `PriceForecast`. Porting it would create a
  second implementation of a rule that already exists in Java, which is how the two quietly stop
  agreeing. The Java side already scores itself in `ForecastCalibrationTest`.
