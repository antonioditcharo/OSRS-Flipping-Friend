"""Legacy feature builder for the LSTM microservice.

**Superseded by ``data.py``.** Kept because ``api.py`` imports it and the service has an
installer, a launcher and a CI job around it. The two leaks it carried are closed here so
that anything still running cannot train on a contaminated target; its remaining faults --
a training loop taking one gradient step per epoch, no test fold, no baseline -- are not
worth repairing in place. Use ``data.py`` and ``evaluate.py`` for anything new.

The measured verdict on the model this feeds is in ``FINDINGS.md``: scored against the
analytical rule it is supposed to improve on, it earns weight zero.
"""

import pandas as pd
import numpy as np
import requests
import datetime

from wiki_api import BASE, HEADERS

#: Bars ahead the label looks. Matches ``data.HORIZON``, the module that supersedes this one.
LABEL_HORIZON = 12

def fetch_wiki_data(item_id: int):
    url = f"{BASE}/timeseries?timestep=5m&id={item_id}"
    response = requests.get(url, headers=HEADERS)
    if response.status_code == 200:
        data = response.json().get("data", [])
        return data
    return []

def preprocess_data(item_id: int, fallback_prices: list = None, window_size: int = 30):
    """
    Returns X, y for training using Wiki API data.
    """
    data = fetch_wiki_data(item_id)
    if not data and fallback_prices:
        # Fallback if API fails
        df = pd.DataFrame({'avgHighPrice': fallback_prices, 'avgLowPrice': fallback_prices, 'avgHighVolume': 100, 'avgLowVolume': 100, 'timestamp': range(len(fallback_prices))})
    elif not data:
        return None, None
    else:
        df = pd.DataFrame(data)
    
    if len(df) < window_size + 2:
        return None, None
        
    for col in ['avgHighPrice', 'avgLowPrice', 'avgHighVolume', 'avgLowVolume']:
        if col not in df.columns:
            df[col] = 0

    # Forward fill only. .bfill() filled a missing price from the *next* observation, which
    # is the future arriving inside the feature window -- and it does so hardest on thin
    # items, where prices go missing most.
    df['price'] = df['avgHighPrice'].replace(0, pd.NA).ffill()
    df = df[df['price'].notna()].reset_index(drop=True)
    df['volume'] = df['avgHighVolume'].fillna(0) + df['avgLowVolume'].fillna(0)
    
    # Features
    df['price_change'] = df['price'].pct_change()
    df['volatility'] = df['price_change'].rolling(window=10).std().fillna(0)
    df['time_of_day'] = pd.to_datetime(df['timestamp'], unit='s').dt.hour / 24.0
    
    # Target (we predict smoothed future price change)
    # The move that FOLLOWS the window, and nothing inside it.
    #
    # This was price_change.ewm(span=5, adjust=False).mean().shift(-1) -- a *trailing*
    # exponential mean shifted back by one, so the label was a weighted average reaching
    # backwards over the bars before it. Against the window it was paired with, 44% of
    # that weight landed on bars handed to the model as input and 15% on the very last
    # bar of the window: nearly half the answer inside the question. Training drove the
    # loss down by learning to copy an input to the output, and nothing in the run could
    # have said so. Pinned by test_no_leak.py.
    df['target'] = df['price'].shift(-LABEL_HORIZON) / df['price'] - 1.0
    
    df = df.dropna().reset_index(drop=True)
    
    features = ['price_change', 'volume', 'volatility', 'time_of_day']
    feature_data = df[features].values
    target_data = df['target'].values
    
    X, y = [], []
    for i in range(len(feature_data) - window_size):
        X.append(feature_data[i : i + window_size])
        y.append(target_data[i + window_size])
        
    return np.array(X), np.array(y)
    
def get_inference_data(item_id: int, fallback_prices: list = None, window_size: int = 30):
    """
    Returns the latest window_size candles for inference.
    """
    data = fetch_wiki_data(item_id)
    if not data and fallback_prices:
        df = pd.DataFrame({'avgHighPrice': fallback_prices, 'avgLowPrice': fallback_prices, 'avgHighVolume': 100, 'avgLowVolume': 100, 'timestamp': range(len(fallback_prices))})
    elif not data:
        return None
    else:
        df = pd.DataFrame(data)
        
    for col in ['avgHighPrice', 'avgLowPrice', 'avgHighVolume', 'avgLowVolume']:
        if col not in df.columns:
            df[col] = 0

    # Forward fill only. .bfill() filled a missing price from the *next* observation, which
    # is the future arriving inside the feature window -- and it does so hardest on thin
    # items, where prices go missing most.
    df['price'] = df['avgHighPrice'].replace(0, pd.NA).ffill()
    df = df[df['price'].notna()].reset_index(drop=True)
    df['volume'] = df['avgHighVolume'].fillna(0) + df['avgLowVolume'].fillna(0)
    
    df['price_change'] = df['price'].pct_change()
    df['volatility'] = df['price_change'].rolling(window=10).std().fillna(0)
    df['time_of_day'] = pd.to_datetime(df['timestamp'], unit='s').dt.hour / 24.0
    
    df = df.dropna().reset_index(drop=True)
    
    if len(df) < window_size:
        return None
        
    features = ['price_change', 'volume', 'volatility', 'time_of_day']
    feature_data = df[features].values[-window_size:]
    
    return np.array([feature_data])
