from fastapi import FastAPI
from pydantic import BaseModel
from typing import Dict, List
import torch
import os
import threading
import numpy as np
from advanced_model import AdvancedOSRSForecaster
from dataset import get_inference_data

app = FastAPI(title="OSRS LSTM Forecaster")

# In-memory model cache so we don't reload from disk on every request.
_model_cache: Dict[int, AdvancedOSRSForecaster] = {}
_cache_lock = threading.Lock()

# Items currently being trained in the background.
_training_in_progress: set = set()
_training_lock = threading.Lock()


class BulkPredictRequest(BaseModel):
    item_histories: Dict[int, List[float]]


def _get_model(item_id: int) -> AdvancedOSRSForecaster | None:
    """Return a cached model, loading it from disk if necessary."""
    with _cache_lock:
        if item_id in _model_cache:
            return _model_cache[item_id]

    model_path = os.path.join("models", f"lstm_model_{item_id}.pth")
    if not os.path.exists(model_path):
        return None

    model = AdvancedOSRSForecaster(input_size=4, hidden_size=64, num_layers=1, dropout_prob=0.3)
    model.load_state_dict(torch.load(model_path, weights_only=True))
    model.eval()

    with _cache_lock:
        _model_cache[item_id] = model
    return model


def _train_in_background(item_id: int, prices: List[float]):
    """Train a model for the given item in a background thread, then cache it."""
    try:
        from train import train_model
        print(f"[bg-train] Training model for item {item_id}...")
        train_model(item_id, prices)

        model_path = os.path.join("models", f"lstm_model_{item_id}.pth")
        if os.path.exists(model_path):
            model = AdvancedOSRSForecaster(input_size=4, hidden_size=64, num_layers=1, dropout_prob=0.3)
            model.load_state_dict(torch.load(model_path, weights_only=True))
            model.eval()
            with _cache_lock:
                _model_cache[item_id] = model
            print(f"[bg-train] Model for item {item_id} ready.")
        else:
            print(f"[bg-train] Training for item {item_id} produced no model.")
    except Exception as e:
        print(f"[bg-train] Failed to train model for item {item_id}: {e}")
    finally:
        with _training_lock:
            _training_in_progress.discard(item_id)


def _predict_single(item_id: int, prices: List[float]) -> float:
    """
    Return the predicted percentage change for an item.

    If no trained model exists yet, returns 0.0 (neutral) and kicks off background training.
    This way, the first request for a new item is fast (returns neutral), and subsequent
    requests will have a real prediction once training finishes.
    """
    model = _get_model(item_id)

    if model is None:
        # No model yet — start training in the background if not already running.
        with _training_lock:
            if item_id not in _training_in_progress:
                _training_in_progress.add(item_id)
                thread = threading.Thread(target=_train_in_background, args=(item_id, prices), daemon=True)
                thread.start()
        return 0.0

    try:
        if not prices:
            return 0.0

        X = get_inference_data(item_id, fallback_prices=prices, window_size=30)
        if X is None:
            return 0.0

        X_t = torch.tensor(X, dtype=torch.float32)

        with torch.no_grad():
            prediction = model(X_t)

        return float(prediction.item())
    except Exception as e:
        print(f"[predict] Error predicting item {item_id}: {e}")
        return 0.0


@app.post("/predict_bulk")
def predict_bulk(req: BulkPredictRequest):
    results = {}
    for item_id, prices in req.item_histories.items():
        results[item_id] = _predict_single(item_id, prices)
    return results


@app.get("/health")
def health():
    """Simple health check for the Java client to verify the server is up."""
    return {"status": "ok", "models_loaded": len(_model_cache)}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("api:app", host="0.0.0.0", port=8000)
