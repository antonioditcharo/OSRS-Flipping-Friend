"""The sequence model, rebuilt so that training it means something.

``AdvancedOSRSForecaster`` was a reasonable architecture — an LSTM with four-head self-attention —
wrapped in a training loop that could not have fitted it. What is different here:

**It is actually trained.** The old loop ran one ``optimizer.step()`` per epoch on the whole tensor,
capped at 100 epochs and early-stopped at 5, so the model got somewhere between five and a hundred
gradient updates in total. This uses mini-batches, so an epoch is hundreds of steps.

**LayerNorm, not BatchNorm1d.** ``BatchNorm1d`` forced a hack in ``forward`` that called
``self.eval()`` and ``self.train()`` mid-pass when the batch was one — mutating module state during a
forward pass, which also silently turned dropout off for that call. LayerNorm normalises within a
sample and does not care about the batch, so the hack disappears rather than being fixed.

**One pooled model, not four thousand.** The old design trained a separate network per item and had
managed exactly one, for the Abyssal whip. Four thousand per-item models cannot share anything: what
the market teaches about one rune is unavailable to the next. This is a single cross-sectional model
with a learned embedding per item, so an item with thin history borrows the shape of the whole
market and its embedding carries only what is genuinely its own.

**Huber, not LogCosh on a raw target.** They are near-identical curves; the problem was never the
curve. It was that a near-zero-mean target makes "predict zero" a good score, so the loss cannot tell
a model that learned something from one that learned nothing. That is fixed in ``evaluate.py``, by
scoring against baselines rather than against a number. The loss here is Huber only because it is
less distracted by the occasional 30% print that a thin item produces.
"""

from typing import Optional

import numpy as np
import torch
import torch.nn as nn

import data

DEVICE = torch.device("cpu")


class SequenceForecaster(nn.Module):
    """LSTM over the window, attention across it, and an item embedding alongside."""

    def __init__(self, feature_count: int, item_count: int, hidden: int = 64,
                 embedding: int = 8, layers: int = 1, dropout: float = 0.2):
        super().__init__()
        self.lstm = nn.LSTM(feature_count, hidden, num_layers=layers, batch_first=True,
                            dropout=dropout if layers > 1 else 0.0)
        self.norm = nn.LayerNorm(hidden)
        self.attention = nn.MultiheadAttention(hidden, num_heads=4, batch_first=True,
                                               dropout=dropout)
        # What this item does that the market as a whole does not. Small on purpose: a wide
        # embedding on a few hundred items is a lookup table with a memory of the training fold.
        self.item = nn.Embedding(item_count, embedding)
        self.head = nn.Sequential(
            nn.Linear(hidden + embedding, 64),
            nn.LayerNorm(64),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(64, 1),
        )

    def forward(self, sequence: torch.Tensor, item: torch.Tensor) -> torch.Tensor:
        encoded, _ = self.lstm(sequence)
        encoded = self.norm(encoded)
        attended, _ = self.attention(encoded, encoded, encoded)
        # Mean over the window rather than the last step alone: the last five-minute bar of a thin
        # item is frequently one trade, and reading the answer off it is how a forecast becomes a
        # restatement of the most recent print.
        pooled = attended.mean(dim=1)
        return self.head(torch.cat([pooled, self.item(item)], dim=1)).squeeze(1)


def fit_predict(train_x: np.ndarray, train_y: np.ndarray, train_items: np.ndarray,
                test_x: np.ndarray, test_items: np.ndarray, item_count: int,
                epochs: int = 12, batch_size: int = 256, seed: int = 0) -> Optional[np.ndarray]:
    """Trains on one fold and predicts the next. Deterministic for a seed."""
    torch.manual_seed(seed)
    np.random.seed(seed)

    if len(train_x) < 500:
        return None

    # The last tenth of the training fold, in time order, decides when to stop. Taken from the end
    # rather than at random because a random tenth of overlapping windows is a memory test.
    cut = int(len(train_x) * 0.9)
    x = torch.tensor(train_x[:cut], device=DEVICE)
    y = torch.tensor(train_y[:cut], device=DEVICE)
    items = torch.tensor(train_items[:cut], device=DEVICE)
    val_x = torch.tensor(train_x[cut:], device=DEVICE)
    val_y = torch.tensor(train_y[cut:], device=DEVICE)
    val_items = torch.tensor(train_items[cut:], device=DEVICE)

    model = SequenceForecaster(train_x.shape[-1], item_count).to(DEVICE)
    loss_fn = nn.HuberLoss(delta=0.01)
    optimiser = torch.optim.AdamW(model.parameters(), lr=1e-3, weight_decay=1e-4)

    best = float("inf")
    best_state = None
    patience = 0
    for _ in range(epochs):
        model.train()
        order = torch.randperm(len(x))
        for start in range(0, len(order), batch_size):
            batch = order[start:start + batch_size]
            optimiser.zero_grad()
            loss = loss_fn(model(x[batch], items[batch]), y[batch])
            loss.backward()
            # An LSTM on financial returns will occasionally see a 30% print and take a step large
            # enough to undo an epoch. Clipping is not tuning, it is the difference between training
            # and diverging.
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimiser.step()

        model.eval()
        with torch.no_grad():
            validation = loss_fn(model(val_x, val_items), val_y).item()
        if validation < best - 1e-9:
            best = validation
            best_state = {k: v.clone() for k, v in model.state_dict().items()}
            patience = 0
        else:
            patience += 1
            if patience >= 3:
                break

    if best_state is not None:
        model.load_state_dict(best_state)

    model.eval()
    with torch.no_grad():
        return model(torch.tensor(test_x, device=DEVICE),
                     torch.tensor(test_items, device=DEVICE)).cpu().numpy()


def main(item_count: int = 200) -> None:
    import evaluate

    print(f"fetching up to {item_count} liquid items…")
    ids = data.liquid_item_ids(item_count)
    x, y, items, anchor, label_time, distinct = data.assemble(ids)
    print(f"{len(x):,} windows across {distinct} items, "
          f"{data.WINDOW} bars in, {data.HORIZON} bars ahead")
    print(f"label sd {y.std():.5f}, mean {y.mean():+.6f}")

    results = evaluate.evaluate(x, y, anchor, label_time, items, distinct, fit_predict)
    print("\n=== pooled across folds ===")
    print(evaluate.report(evaluate.pooled(results)))


if __name__ == "__main__":
    main()
