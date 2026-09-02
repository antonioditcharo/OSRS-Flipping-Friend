import torch
import torch.nn as nn

class OSRS_LSTM(nn.Module):
    def __init__(self, input_size=1, hidden_size=64, num_layers=1, dropout_prob=0.3):
        super(OSRS_LSTM, self).__init__()
        
        # LSTM block
        # batch_first=True means input shape is (batch, seq_len, features)
        self.lstm = nn.LSTM(input_size=input_size, hidden_size=hidden_size, num_layers=num_layers, batch_first=True)
        self.lstm_bn = nn.BatchNorm1d(hidden_size)
        self.lstm_relu = nn.LeakyReLU()
        self.lstm_dropout = nn.Dropout(dropout_prob)
        
        # Dense Block 1
        self.fc1 = nn.Linear(hidden_size, 32)
        self.fc1_bn = nn.BatchNorm1d(32)
        self.fc1_relu = nn.LeakyReLU()
        self.fc1_dropout = nn.Dropout(dropout_prob)
        
        # Dense Block 2
        self.fc2 = nn.Linear(32, 16)
        self.fc2_bn = nn.BatchNorm1d(16)
        self.fc2_relu = nn.LeakyReLU()
        self.fc2_dropout = nn.Dropout(dropout_prob)
        
        # Output Layer
        self.out = nn.Linear(16, 1)

    def forward(self, x):
        # x shape: (batch_size, seq_len, input_size)
        
        lstm_out, _ = self.lstm(x)
        # We only want the output from the final time step
        # lstm_out shape: (batch, seq_len, hidden_size)
        last_step = lstm_out[:, -1, :] 
        
        # If batch size is 1, batch norm can fail during training if not handled,
        # but typically batch size > 1.
        x = self.lstm_bn(last_step)
        x = self.lstm_relu(x)
        x = self.lstm_dropout(x)
        
        x = self.fc1(x)
        x = self.fc1_bn(x)
        x = self.fc1_relu(x)
        x = self.fc1_dropout(x)
        
        x = self.fc2(x)
        x = self.fc2_bn(x)
        x = self.fc2_relu(x)
        x = self.fc2_dropout(x)
        
        return self.out(x)
