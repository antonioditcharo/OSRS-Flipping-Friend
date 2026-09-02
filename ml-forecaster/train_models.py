import os
import torch
import torch.nn as nn
import numpy as np
import lightgbm as lgb
import onnxmltools
from onnxmltools.convert import convert_lightgbm
from onnxconverter_common.data_types import FloatTensorType

# Define paths
MODELS_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'companion', 'src', 'main', 'resources', 'models')
os.makedirs(MODELS_DIR, exist_ok=True)

# 1. Bidirectional LSTM / TCN for Momentum & Volatility
class MomentumLSTM(nn.Module):
    def __init__(self, input_dim=4, hidden_dim=64, num_layers=2):
        super(MomentumLSTM, self).__init__()
        self.lstm = nn.LSTM(input_dim, hidden_dim, num_layers=num_layers, batch_first=True, bidirectional=True)
        self.attention = nn.MultiheadAttention(embed_dim=hidden_dim * 2, num_heads=4, batch_first=True)
        self.fc = nn.Linear(hidden_dim * 2, 2) # output: [Drift, Variance]
        
    def forward(self, x):
        lstm_out, _ = self.lstm(x)
        # Self-attention
        attn_out, _ = self.attention(lstm_out, lstm_out, lstm_out)
        # Pool the last timestep
        last_step = attn_out[:, -1, :]
        return self.fc(last_step)

print("Exporting Momentum LSTM model...")
lstm_model = MomentumLSTM()
lstm_model.eval()
# Dummy input: (batch_size, seq_len, num_features). We will use batch_size=1 for export
# but ONNX supports dynamic batch sizes if specified.
dummy_input = torch.randn(1, 12, 4) # e.g. 1 hour of 5-min bars, 4 features
torch.onnx.export(
    lstm_model, 
    dummy_input, 
    os.path.join(MODELS_DIR, "momentum_v1.onnx"),
    input_names=['input'],
    output_names=['output'],
    dynamic_axes={'input': {0: 'batch_size'}, 'output': {0: 'batch_size'}}
)

# 2. LightGBM for Fill Classification
print("Exporting Fill Probability GBDT model...")
X_cls = np.random.rand(100, 4).astype(np.float32)
y_cls = np.random.randint(0, 2, 100)
lgb_cls = lgb.LGBMClassifier(n_estimators=10, random_state=42)
lgb_cls.fit(X_cls, y_cls)

initial_types = [['input', FloatTensorType([None, 4])]]
onnx_cls = convert_lightgbm(lgb_cls, initial_types=initial_types, target_opset=15)
with open(os.path.join(MODELS_DIR, "fill_prob_v1.onnx"), "wb") as f:
    f.write(onnx_cls.SerializeToString())

# 3. LightGBM Regression for Queue Wait Times
print("Exporting Queue Wait Duration GBDT model...")
X_reg = np.random.rand(100, 4).astype(np.float32)
y_reg = np.random.rand(100) * 100 # duration in minutes
lgb_reg = lgb.LGBMRegressor(n_estimators=10, random_state=42)
lgb_reg.fit(X_reg, y_reg)

onnx_reg = convert_lightgbm(lgb_reg, initial_types=initial_types, target_opset=15)
with open(os.path.join(MODELS_DIR, "queue_wait_v1.onnx"), "wb") as f:
    f.write(onnx_reg.SerializeToString())

print("All models exported successfully.")
