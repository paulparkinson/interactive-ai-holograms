import cvzone
import pickle
import mediapipe as mp
import cv2
import numpy as np
import time
from collections import deque

# Initialize mediapipe face mesh
mp_face_mesh = mp.solutions.face_mesh
mp_drawing = mp.solutions.drawing_utils
face_mesh = mp_face_mesh.FaceMesh(static_image_mode=False, max_num_faces=1, min_detection_confidence=0.5)

cap = cv2.VideoCapture(1)

with open('model.pkl','rb') as f:
   Behaviour_model = pickle.load(f)

# FPS calculation
prev_time = 0
frame_count = 0

# Temporal smoothing - average predictions over multiple frames
prediction_history = deque(maxlen=15)  # Average over last 15 frames
emotion_classes = Behaviour_model.classes_


# taking video frame by frame
while cap.isOpened():
    rt,frame = cap.read()
    if not rt:
        break
    frame = cv2.resize(frame,(720,480))
    frame_count += 1

    real_frame = frame.copy()

    # Calculate FPS
    curr_time = time.time()
    fps = 1 / (curr_time - prev_time) if prev_time > 0 else 0
    prev_time = curr_time

    # Convert to RGB for mediapipe
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    results = face_mesh.process(rgb_frame)
    
    # Display debug info - using better colors (BGR format: Blue, Green, Red)
    cvzone.putTextRect(frame, f'FPS: {fps:.1f}', (10, 30), scale=1, thickness=1, colorR=(255, 255, 255))  # White
    cvzone.putTextRect(frame, f'Frame: {frame.shape[1]}x{frame.shape[0]}', (10, 60), scale=1, thickness=1, colorR=(255, 255, 255))  # White
    
    if results.multi_face_landmarks:
        face_landmarks = results.multi_face_landmarks[0]
        
        # Calculate face bounding box for position feedback
        h, w = frame.shape[:2]
        x_coords = [lm.x for lm in face_landmarks.landmark]
        y_coords = [lm.y for lm in face_landmarks.landmark]
        
        face_width = (max(x_coords) - min(x_coords)) * w
        face_height = (max(y_coords) - min(y_coords)) * h
        face_center_x = (max(x_coords) + min(x_coords)) / 2
        face_center_y = (max(y_coords) + min(y_coords)) / 2
        
        # Face position feedback
        posiprobabilities = Behaviour_model.predict_proba([face_data])[0]
            
            # Add to history for temporal smoothing
            prediction_history.append(probabilities)
            
            # Average predictions over last N frames for stability
            if len(prediction_history) > 0:
                avg_probabilities = np.mean(prediction_history, axis=0)
            else:
                avg_probabilities = probabilities
            
            # Get top 3 predictions from averaged probabilities
            top_indices = np.argsort(avg_probabilities)[-3:][::-1]
            
            # Display main emotion with confidence
            main_idx = top_indices[0]
            main_emotion = emotion_classes[main_idx]
            main_confidence = avg_probabilities[main_idx] * 100
            
            # Color coding for confidence level
            if main_confidence > 60:
                conf_color = (0, 255, 0)  # Green - high confidence
            elif main_confidence > 40:
                conf_color = (0, 255, 255)  # Yellow - medium confidence
            else:
                conf_color = (0, 165, 255)  # Orange - low confidence
            
            cvzone.putTextRect(frame, f'{main_emotion.upper()}: {main_confidence:.1f}%', 
                             (10, 140), scale=2, thickness=3, colorR=conf_color)
            
            # Display top 3 emotions with confidence bars
            y_pos = 200
            bar_colors = [(0, 255, 0), (0, 255, 255), (0, 165, 255)]  # Green, Yellow, Orange
            for i, idx in enumerate(top_indices):
                emotion = emotion_classes[idx]
                confidence = avg_probabilities[idx] * 100
                bar_width = int(confidence * 3)  # Scale for visualization
                
                # Draw confidence bar
                cv2.rectangle(frame, (10, y_pos - 15), (10 + bar_width, y_pos), bar_colors[i], -1)
                cvzone.putTextRect(frame, f'{emotion}: {confidence:.1f}%', 
                                 (10, y_pos), scale=1, thickness=1, colorR=(255, 255, 255))
                y_pos += 35
            
            print(f'{main_emotion}: {main_confidence:.1f}% | All: {[(emotion_classes[i], avg_probabilities[i]*100) for i in range(len(emotion_classes))
        
        try:
            # feeding newpoints to model for prediction
            result = Behaviour_model.predict([face_data])
            probabilities = Behaviour_model.predict_proba([face_data])[0]
            
            # Get top 3 predictions
            top_indices = np.argsort(probabilities)[-3:][::-1]
            emotion_classes = Behaviour_model.classes_
            
            # Display main emotion with confidence
            main_emotion = result[0]
            main_confidence = probabilities[list(emotion_classes).index(main_emotion)] * 100
            cvzone.putTextRect(frame, f'{main_emotion.upper()}: {main_confidence:.1f}%', 
                             (10, 140), scale=2, thickness=3, colorR=(255, 0, 255))
              # Red
    else:
        # No face detected - clear prediction history
        prediction_history.clear()
        cvzone.putTextRect(frame, 'FACE: NOT DETECTED - Position face in center', (10, 90), scale=1, thickness=2, colorR=(0, 0, 255))  # Red
                emotion = emotion_classes[idx]
                confidence = probabilities[idx] * 100
                bar_width = int(confidence * 3)  # Scale for visualization
                
                # Draw confidence bar
                cv2.rectangle(frame, (10, y_pos - 15), (10 + bar_width, y_pos), (0, 255, 0), -1)
                cvzone.putTextRect(frame, f'{emotion}: {confidence:.1f}%', 
                                 (10, y_pos), scale=1, thickness=1)
                y_pos += 35
            
            print(f'{main_emotion}: {main_confidence:.1f}% | Top 3: {[(emotion_classes[i], probabilities[i]*100) for i in top_indices]}')
            
        except Exception as e:
            print(f"Prediction error: {e}")
            cvzone.putTextRect(frame, f'ERROR: {str(e)[:30]}', (10, 140), scale=1, thickness=1, colorR=(0, 0, 255))
    else:
        # No face detected
        cvzone.putTextRect(frame, 'FACE: NOT DETECTED', (10, 90), scale=1, thickness=2, colorR=(0, 0, 255))
            
    all_frames = cvzone.stackImages([real_frame,frame],2,0.70)
    cv2.imshow('Emotion Detection - Press Q to quit',all_frames)
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()