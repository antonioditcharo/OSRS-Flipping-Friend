import torch
import torch.nn as nn

class AdvancedOSRSForecaster(nn.Module):
    def __init__(self, input_size=4, hidden_size=128, num_layers=2, dropout_prob=0.3):
        """
        Input features: [price_change, volume, volatility, time_of_day]
        """
        super(AdvancedOSRSForecaster, self).__init__()
        
        self.lstm = nn.LSTM(
            input_size=input_size, 
            hidden_size=hidden_size, 
            num_layers=num_layers, 
            batch_first=True,
            dropout=dropout_prob if num_layers > 1 else 0
        )
        
        # Self-attention layer to focus on important historical steps
        self.attention = nn.MultiheadAttention(embed_dim=hidden_size, num_heads=4, batch_first=True)
        
        self.fc_layers = nn.Sequential(
            nn.Linear(hidden_size, 64),
            nn.BatchNorm1d(64),
            nn.LeakyReLU(),
            nn.Dropout(dropout_prob),
            
            nn.Linear(64, 32),
            nn.BatchNorm1d(32),
            nn.LeakyReLU(),
            nn.Dropout(dropout_prob),
            
            nn.Linear(32, 1)
        )

    def forward(self, x):
        # x shape: (batch_size, seq_len, input_size)
        
        lstm_out, _ = self.lstm(x)
        # lstm_out shape: (batch_size, seq_len, hidden_size)
        
        # Apply attention
        attn_out, _ = self.attention(lstm_out, lstm_out, lstm_out)
        
        # Take the last time step after attention
        last_step = attn_out[:, -1, :] 
        
        # Pass through dense layers
        # Handle batch_size = 1 for BatchNorm
        if last_step.size(0) == 1 and self.training:
            self.eval()
            out = self.fc_layers(last_step)
            self.train()
            return out
            
        return self.fc_layers(last_step)
