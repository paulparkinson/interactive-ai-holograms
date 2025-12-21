"""
Alternative: Emotion detection using DeepFace library
This is a backup option if HSEmotion has issues
DeepFace supports multiple backends and models
"""
import cv2
import numpy as np
import time
from collections import deque
import cvzone

print("Installing DeepFace if needed...")
try:
    from deepface import DeepFace
except:
    import subprocess
    subprocess.run(["pip", "install", "deepface"])
    from deepface import DeepFace

# Initialize video capture
cap = cv2.VideoCapture(1)
print("Camera initialized. Using DeepFace emotion detection.")
print("Press 'q' to quit.")

# Temporal smoothing
prediction_history = deque(maxlen=8)

# FPS calculation
prev_time = 0

# DeepFace emotion order
EMOTIONS = ['angry', 'disgust', 'fear', 'happy', 'sad', 'surprise', 'neutral']

while True:
    ret, frame = cap.read()
    if not ret:
        break
    
    frame = cv2.resize(frame, (720, 480))
    real_frame = frame.copy()
    
    # Calculate FPS
    curr_time = time.time()
    fps = 1 / (curr_time - prev_time) if prev_time > 0 else 0
    prev_time = curr_time
    
    # Display info
    cvzone.putTextRect(frame, f'FPS: {fps:.1f}', (10, 30), scale=1, thickness=1, colorR=(255, 255, 255))
    cvzone.putTextRect(frame, f'Model: DeepFace (Alternative)', (10, 60), scale=1, thickness=1, colorR=(255, 255, 255))
    
    try:
        # Analyze emotion (DeepFace processes entire frame)
        result = DeepFace.analyze(real_frame, actions=['emotion'], enforce_detection=False, silent=True)
        
        # Handle both single face and multiple faces
        if isinstance(result, list):
            result = result[0]  # Take first face
        
        emotions = result['emotion']
        
        # Convert to probability array
        emotion_probs = np.array([emotions[e] / 100.0 for e in EMOTIONS])
        
        # Add to history
        prediction_history.append(emotion_probs)
        
        # Average predictions
        if len(prediction_history) > 0:
            avg_probs = np.mean(prediction_history, axis=0)
        else:
            avg_probs = emotion_probs
        
        # Get top 3
        top_indices = np.argsort(avg_probs)[-3:][::-1]
        
        # Main emotion
        main_idx = top_indices[0]
        main_emotion = EMOTIONS[main_idx]
        main_confidence = avg_probs[main_idx] * 100
        
        # Color based on confidence
        if main_confidence > 60:
            conf_color = (0, 255, 0)
        elif main_confidence > 40:
            conf_color = (0, 255, 255)
        else:
            conf_color = (0, 165, 255)
        
        cvzone.putTextRect(frame, 'FACE: DETECTED', (10, 90), scale=1, thickness=2, colorR=(0, 255, 0))
        
        # Display main emotion
        cvzone.putTextRect(frame, f'{main_emotion.upper()}: {main_confidence:.1f}%', 
                         (10, 150), scale=2.5, thickness=3, colorR=conf_color)
        
        # Display top 3
        y_pos = 230
        bar_colors = [(0, 255, 0), (0, 255, 255), (0, 165, 255)]
        for i, idx in enumerate(top_indices):
            emotion = EMOTIONS[idx]
            confidence = avg_probs[idx] * 100
            bar_width = int(confidence * 3)
            
            cv2.rectangle(frame, (10, y_pos - 15), (10 + bar_width, y_pos), bar_colors[i], -1)
            cvzone.putTextRect(frame, f'{emotion}: {confidence:.1f}%', 
                             (10, y_pos), scale=1, thickness=1, colorR=(255, 255, 255))
            y_pos += 40
        
        print(f'{main_emotion}: {main_confidence:.1f}%')
        
    except Exception as e:
        prediction_history.clear()
        cvzone.putTextRect(frame, 'FACE: NOT DETECTED', (10, 90), scale=1, thickness=2, colorR=(0, 0, 255))
        cvzone.putTextRect(frame, 'Position face in center', (10, 130), scale=1, thickness=1, colorR=(0, 0, 255))
    
    # Stack frames
    all_frames = cvzone.stackImages([real_frame, frame], 2, 0.70)
    cv2.imshow('Emotion Detection (DeepFace) - Press Q to quit', all_frames)
    
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()
print("\nSession ended.")
