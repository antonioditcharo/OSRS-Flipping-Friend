import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np
from sklearn.model_selection import train_test_split
from dataset import preprocess_data
from advanced_model import AdvancedOSRSForecaster
import os

class LogCoshLoss(nn.Module):
    def __init__(self):
        super(LogCoshLoss, self).__init__()

    def forward(self, y_pred, y_true):
        x = y_pred - y_true
        return torch.mean(torch.log(torch.cosh(x + 1e-12)))

def train_model(item_id: int, prices: list, window_size: int = 30, epochs: int = 100, patience: int = 5):
    X, y = preprocess_data(item_id, fallback_prices=prices, window_size=window_size)
    
    if X is None or len(X) == 0:
        print("Not enough data to train.")
        return None
        
    # X shape is (N, window_size, features=4)
    # y shape is (N,)
    
    # Split train/val
    X_train, X_val, y_train, y_val = train_test_split(X, y, test_size=0.2, shuffle=False)
    
    # Convert to tensors
    X_train_t = torch.tensor(X_train, dtype=torch.float32)
    y_train_t = torch.tensor(y_train, dtype=torch.float32).unsqueeze(1)
    X_val_t = torch.tensor(X_val, dtype=torch.float32)
    y_val_t = torch.tensor(y_val, dtype=torch.float32).unsqueeze(1)
    
    model = AdvancedOSRSForecaster(input_size=4, hidden_size=64, num_layers=1, dropout_prob=0.3)
    
    criterion = LogCoshLoss()
    optimizer = optim.Adam(model.parameters(), lr=0.001, weight_decay=0.01)
    
    best_val_loss = float('inf')
    patience_counter = 0
    best_model_state = None
    
    # We need batch size > 1 for BatchNorm, if we have very little data we might fail
    if len(X_train) < 2 or len(X_val) < 2:
        print("Not enough data to support BatchNorm (needs batch > 1).")
        return None

    print(f"Starting training for item {item_id}...")
    for epoch in range(epochs):
        model.train()
        optimizer.zero_grad()
        
        # Forward pass
        outputs = model(X_train_t)
        loss = criterion(outputs, y_train_t)
        
        # Backward pass
        loss.backward()
        optimizer.step()
        
        # Validation
        model.eval()
        with torch.no_grad():
            val_outputs = model(X_val_t)
            val_loss = criterion(val_outputs, y_val_t)
            
        if (epoch + 1) % 10 == 0 or epoch == 0:
            print(f"Epoch {epoch+1}/{epochs} | Train Loss: {loss.item():.6f} | Val Loss: {val_loss.item():.6f}")
        
        if val_loss.item() < best_val_loss:
            best_val_loss = val_loss.item()
            patience_counter = 0
            best_model_state = model.state_dict()
        else:
            patience_counter += 1
            if patience_counter >= patience:
                print(f"Early stopping triggered at epoch {epoch+1} with Val Loss: {val_loss.item():.6f}")
                break
                
    if best_model_state:
        model.load_state_dict(best_model_state)
        
    # Save the model
    os.makedirs("models", exist_ok=True)
    model_path = os.path.join("models", f"lstm_model_{item_id}.pth")
    torch.save(model.state_dict(), model_path)
    print(f"Model saved to {model_path}")
    return model
