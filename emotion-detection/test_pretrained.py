"""
Emotion detection using pre-trained deep learning models.
Uses DeepFace for high accuracy (60-65%+) emotion detection.
"""
import cv2
import numpy as np
import time
from collections import deque
import cvzone

# Initialize emotion detector
print("Loading pre-trained emotion detection model...")
print("Using DeepFace for reliable high-accuracy detection...")

try:
    from deepface import DeepFace
    print("✓ DeepFace loaded successfully")
except:
    print("Installing DeepFace...")
    import subprocess
    subprocess.run(["pip", "install", "deepface", "tf-keras"])
    from deepface import DeepFace
    print("✓ DeepFace installed and loaded")

# DeepFace emotion labels
EMOTION_LABELS = ['angry', 'disgust', 'fear', 'happy', 'sad', 'surprise', 'neutral']

# Initialize video capture
cap = cv2.VideoCapture(1)
print("Camera initialized. Press 'q' to quit.")

# Face detection
face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')

# Temporal smoothing
prediction_history = deque(maxlen=10)

# FPS calculation
prev_time = 0

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
    
    # Convert to grayscale for face detection
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    
    # Detect faces
    faces = face_cascade.detectMultiScale(gray, 1.3, 5)
    
    # Display info
    cvzone.putTextRect(frame, f'FPS: {fps:.1f}', (10, 30), scale=1, thickness=1, colorR=(255, 255, 255))
    cvzone.putTextRect(frame, f'Model: DeepFace (High Accuracy)', (10, 60), scale=1, thickness=1, colorR=(255, 255, 255))
    
    if len(faces) > 0:
        # Get largest face
        face = max(faces, key=lambda f: f[2] * f[3])
        x, y, w, h = face
        
        # Draw face rectangle
        cv2.rectangle(frame, (x, y), (x+w, y+h), (0, 255, 0), 2)
        
        # Position feedback
        frame_center_x = frame.shape[1] / 2
        face_center_x = x + w/2
        
        position_feedback = []
        if w < 120:
            position_feedback.append("Move closer")
        elif w > 300:
            position_feedback.append("Move back")
        
        if face_center_x < frame_center_x - 100:
            position_feedback.append("Move right")
        elif face_center_x > frame_center_x + 100:
            position_feedback.append("Move left")
        
        if position_feedback:
            cvzone.putTextRect(frame, f'Position: {", ".join(position_feedback)}', (10, 90), 
                             scale=1, thickness=1, colorR=(0, 255, 255))
        else:
            cvzone.putTextRect(frame, 'Position: OPTIMAL', (10, 90), 
                             scale=1, thickness=2, colorR=(0, 255, 0))
        
        # Extract face for emotion detection
        face_roi = frame[max(0,y):min(frame.shape[0],y+h), max(0,x):min(frame.shape[1],x+w)]
        
        if face_roi.size > 0:
            try:
                # Get emotion prediction
                result = DeepFace.analyze(face_roi, actions=['emotion'], enforce_detection=False, silent=True)
                
                # Handle response format
                if isinstance(result, list):
                    result = result[0]
                
                emotions = result['emotion']
                
                # Convert to probability array
                emotion_probs = np.array([emotions[e] / 100.0 for e in EMOTION_LABELS])
                
                # Add to history for smoothing
                prediction_history.append(emotion_probs)
                
                # Average predictions
                if len(prediction_history) > 0:
                    avg_probs = np.mean(prediction_history, axis=0)
                else:
                    avg_probs = emotion_probs
                
                # Get top 3 emotions
                top_indices = np.argsort(avg_probs)[-3:][::-1]
                
                # Main emotion
                main_idx = top_indices[0]
                main_emotion = EMOTION_LABELS[main_idx]
                main_confidence = avg_probs[main_idx] * 100
                
                # Color based on confidence
                if main_confidence > 60:
                    conf_color = (0, 255, 0)  # Green
                elif main_confidence > 40:
                    conf_color = (0, 255, 255)  # Yellow
                else:
                    conf_color = (0, 165, 255)  # Orange
                
                # Display main emotion
                cvzone.putTextRect(frame, f'{main_emotion.upper()}: {main_confidence:.1f}%', 
                                 (10, 150), scale=2.5, thickness=3, colorR=conf_color)
                
                # Display top 3 with bars
                y_pos = 230
                bar_colors = [(0, 255, 0), (0, 255, 255), (0, 165, 255)]
                for i, idx in enumerate(top_indices):
                    emotion = EMOTION_LABELS[idx]
                    confidence = avg_probs[idx] * 100
                    bar_width = int(confidence * 3)
                    
                    # Draw bar
                    cv2.rectangle(frame, (10, y_pos - 15), (10 + bar_width, y_pos), bar_colors[i], -1)
                    cvzone.putTextRect(frame, f'{emotion}: {confidence:.1f}%', 
                                     (10, y_pos), scale=1, thickness=1, colorR=(255, 255, 255))
                    y_pos += 40
                
                # Print to console
                print(f'{main_emotion}: {main_confidence:.1f}%')
                
            except Exception as e:
                cvzone.putTextRect(frame, f'Detection in progress...', (10, 150), 
                                 scale=1, thickness=1, colorR=(0, 255, 255))
    
    else:
        # No face detected
        prediction_history.clear()
        cvzone.putTextRect(frame, 'FACE: NOT DETECTED', (10, 90), 
                         scale=1, thickness=2, colorR=(0, 0, 255))
        cvzone.putTextRect(frame, 'Position face in center of camera', (10, 130), 
                         scale=1, thickness=1, colorR=(0, 0, 255))
    
    # Stack frames
    all_frames = cvzone.stackImages([real_frame, frame], 2, 0.70)
    cv2.imshow('Emotion Detection (Pre-trained CNN) - Press Q to quit', all_frames)
    
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()
print("\nSession ended.")
