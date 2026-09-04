"""Does the sequence model add anything the analytical rule does not already have?

Run as a residual correction rather than a replacement, because that is how every learned component
in this system is allowed to act: ``final = analytical + w · model``, with ``w`` earned from
out-of-sample skill and floored at zero. ``LearnedFillModel`` enforces exactly this on the fill side.
A model asked to predict the price from scratch has to rediscover mean reversion before it can add
anything, and then be scored as though rediscovering it were an achievement. A model asked to predict
what reversion *gets wrong* is asked the only question worth its complexity.

Three arms, each scored on folds none of them has seen:

- the analytical rule alone, which is the incumbent;
- the sequence model alone, predicting the label from scratch;
- the sequence model predicting the analytical rule's residual, added back on.

A longer training budget than the walk-forward default, so that a loss cannot be blamed on the
optimiser having been switched off.
"""

import numpy as np

import data
import evaluate
import sequence_model

REVERSION_STRENGTH = 0.3


def main(item_count: int = 200, epochs: int = 30) -> None:
    ids = data.liquid_item_ids(item_count)
    x, y, items, anchor, label_time, distinct = data.assemble(ids)
    print(f"{len(x):,} windows across {distinct} items, "
          f"{data.WINDOW} bars in, {data.HORIZON} bars ahead, label sd {y.std():.5f}\n")

    results = []
    for fold, (train, test) in enumerate(
            evaluate.purged_splits(anchor, label_time, 4, data.HORIZON)):
        train_x, test_x, _, _ = data.standardise(x[train], x[test])
        rows = evaluate.score_baselines(x[test], y[test])

        # From scratch.
        direct = sequence_model.fit_predict(
            train_x, y[train], items[train], test_x, items[test], distinct, epochs=epochs)
        if direct is not None:
            rows.append(evaluate._metrics("sequence", direct, y[test]))

        # As a correction to the rule. The analytical prediction is computed on both folds from raw
        # features, and the model is trained on what is left over after it.
        analytical_train = evaluate.baseline_reversion(x[train], REVERSION_STRENGTH)
        analytical_test = evaluate.baseline_reversion(x[test], REVERSION_STRENGTH)
        residual = sequence_model.fit_predict(
            train_x, (y[train] - analytical_train).astype(np.float32), items[train],
            test_x, items[test], distinct, epochs=epochs)
        if residual is not None:
            rows.append(evaluate._metrics("reversion+model", analytical_test + residual, y[test]))
            # How much the correction actually moved the answer. A weight the gate would grant is
            # meaningless if the thing being weighted is numerically nothing.
            rows.append(evaluate._metrics("model residual only", residual,
                                          (y[test] - analytical_test)))

        print(f"fold {fold}: train {len(train)}, test {len(test)}")
        print(evaluate.report(rows))
        print()
        results.append(rows)

    print("=== pooled across folds ===")
    print(evaluate.report(evaluate.pooled(results)))


if __name__ == "__main__":
    main()
