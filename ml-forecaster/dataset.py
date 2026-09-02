import pandas as pd
import numpy as np
import requests
import datetime

def fetch_wiki_data(item_id: int):
    url = f"https://prices.runescape.wiki/api/v1/osrs/timeseries?timestep=5m&id={item_id}"
    headers = {
        'User-Agent': 'FlippingFriend_ML_Forecaster'
    }
    response = requests.get(url, headers=headers)
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

    df['price'] = df['avgHighPrice'].replace(0, pd.NA).ffill().bfill()
    df['volume'] = df['avgHighVolume'].fillna(0) + df['avgLowVolume'].fillna(0)
    
    # Features
    df['price_change'] = df['price'].pct_change()
    df['volatility'] = df['price_change'].rolling(window=10).std().fillna(0)
    df['time_of_day'] = pd.to_datetime(df['timestamp'], unit='s').dt.hour / 24.0
    
    # Target (we predict smoothed future price change)
    df['target'] = df['price_change'].ewm(span=5, adjust=False).mean().shift(-1)
    
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

    df['price'] = df['avgHighPrice'].replace(0, pd.NA).ffill().bfill()
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
