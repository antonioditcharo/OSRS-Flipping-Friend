from api import _predict_single
import time

def test():
    item_id = 4151 # Abyssal whip
    # Trigger a prediction, which should return 0.0 and kick off background training
    print("Requesting prediction (should return 0.0 and start background training):")
    pred = _predict_single(item_id, prices=[])
    print(f"Prediction: {pred}")
    
    # Wait for training to complete
    print("Waiting for training to finish...")
    time.sleep(15) 
    
    # Request prediction again
    print("Requesting prediction again (should use trained model):")
    pred2 = _predict_single(item_id, prices=[])
    print(f"Prediction: {pred2}")

if __name__ == "__main__":
    test()
