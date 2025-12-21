# Facial Emotion Detection using Pre-trained Deep Learning Models

## 🎯 Project Overview

This project provides **high-accuracy real-time emotion detection** using state-of-the-art pre-trained CNN models. It can detect emotions from your webcam with 65-70%+ accuracy using deep learning.

### Available Models:
1. **HSEmotion** (Primary) - 65-70% accuracy, 8 emotions
2. **DeepFace** (Backup) - 60-65% accuracy, 7 emotions  
3. **Custom Face Mesh** (Educational) - 52% accuracy, 7 emotions

## 🚀 Quick Start

### Installation
```bash
# Clone the repository
git clone <repository_url>
cd Emotion-Detection

# Install dependencies
pip install -r requirements.txt
```

### Run Emotion Detection
```bash
# Best accuracy - Pre-trained CNN
python test_pretrained.py

# Alternative - DeepFace library
python test_deepface.py

# Lightweight - Custom model
python test.py
```

Press **'q'** to quit the camera window.

## 📊 Emotion Detection Models Comparison

| Model | File | Accuracy | Emotions | Speed | Best For |
|-------|------|----------|----------|-------|----------|
| HSEmotion | test_pretrained.py | 65-70% | 8 | Fast | Production |
| DeepFace | test_deepface.py | 60-65% | 7 | Medium | Alternative |
| Face Mesh | test.py | 52% | 7 | Very Fast | Learning |

### Detected Emotions:
- **HSEmotion**: Anger, Contempt, Disgust, Fear, Happiness, Neutral, Sadness, Surprise
- **DeepFace**: Angry, Disgust, Fear, Happy, Sad, Surprise, Neutral
- **Face Mesh**: Angry, Disgust, Fear, Happy, Neutral, Sad, Surprise

## 🎥 Getting Best Results

### Face Positioning:
1. **Center your face** in the camera
2. **Distance**: Face should fill 30-40% of frame
3. **Lighting**: Light from front (not behind you)
4. **Angle**: Look directly at camera
5. **Hold expressions** for 1-2 seconds

### On-Screen Guidance:
- Green = Optimal position
- Yellow = Needs adjustment
- Position feedback shows if you need to move closer/back/left/right

## 📁 Project Structure

```
Emotion-Detection/
├── test_pretrained.py      # High-accuracy CNN model (RECOMMENDED)
├── test_deepface.py        # Alternative pre-trained model
├── test.py                 # Custom lightweight model
├── training.py             # Train custom model
├── convert_dataset.py      # Convert FER-2013 to training data
├── data.csv               # Training dataset (33K samples)
├── model.pkl              # Trained custom model
├── requirements.txt       # Python dependencies
├── SETUP_COMPLETE.md     # Detailed setup guide
└── README.md             # This file
```

## 🔧 Training Your Own Model (Optional)

If you want to train the custom face mesh model:

```bash
# 1. Download FER-2013 dataset (or use existing data.csv)
python download_fer2013.py

# 2. Convert images to face mesh landmarks
python convert_dataset.py --dataset path/to/fer2013 --replace

# 3. Train the model
python training.py
```

## 🎨 Features

### Visual Display:
- ✅ Real-time FPS counter
- ✅ Face detection status
- ✅ Main emotion with confidence percentage
- ✅ Top 3 emotions with visual bars
- ✅ Confidence color coding (green/yellow/orange)
- ✅ Position optimization guidance

### Technical Features:
- ✅ Temporal smoothing (stable predictions)
- ✅ Multi-model support
- ✅ Offline processing (no internet required)
- ✅ Real-time performance
- ✅ Normalized coordinates (works with any camera resolution)

## 🔬 How It Works

### Pre-trained Models (HSEmotion/DeepFace):
1. Capture video frame from camera
2. Detect face using Haar Cascade or built-in detector
3. Extract face region
4. Feed to pre-trained CNN
5. Get emotion probabilities
6. Apply temporal smoothing
7. Display top emotions with confidence

### Custom Face Mesh Model:
1. Detect 468 facial landmarks using MediaPipe
2. Extract normalized coordinates
3. Feed to trained ML classifier
4. Predict emotion from landmark patterns

## 📊 Model Performance

### HSEmotion (test_pretrained.py):
- **Training**: FER-2013 + AffectNet datasets
- **Architecture**: EfficientNet-B0
- **Accuracy**: 65-70% on FER-2013 test set
- **Processing**: ~15-30 FPS on CPU

### Custom Model (test.py):
- **Training**: 33,162 FER-2013 samples (face mesh)
- **Algorithm**: Gradient Boosting / Random Forest
- **Accuracy**: 52% (limited by landmark-only approach)
- **Processing**: ~50+ FPS on CPU

## 🚀 Future Enhancements

### Multimodal Emotion Detection:
- Add voice tone analysis (pyAudioAnalysis)
- Add body language detection (MediaPipe Pose)
- Combine multiple signals: 60% face + 30% voice + 10% pose

### Advanced Features:
- Emotion tracking over time
- Mood pattern analysis
- Stress/fatigue detection
- Multiple face detection

## 🛠️ Troubleshooting

### Camera Issues:
```python
# If camera doesn't open, change camera index:
cap = cv2.VideoCapture(0)  # Try 0 instead of 1
```

### Dependency Conflicts:
The torch version conflicts with torchaudio are okay - we don't use audio features.

### Slow Performance:
- Use test_pretrained.py (optimized)
- Reduce frame size
- Increase prediction_history maxlen for smoother but slower updates

## 📚 Dependencies

```
opencv-python     # Camera and image processing
mediapipe         # Face mesh detection
cvzone           # Visual utilities
scikit-learn     # Custom model training
pandas           # Data processing
numpy            # Numerical operations
hsemotion        # Pre-trained emotion model
torch            # Deep learning backend
torchvision      # Vision utilities
timm             # Model architectures
deepface         # Alternative emotion model
```

## 📄 License

This project uses:
- MediaPipe (Apache 2.0)
- HSEmotion (MIT)
- DeepFace (MIT)
- FER-2013 dataset (Academic use)

## 🤝 Contributing

Feel free to:
- Add more emotion models
- Improve accuracy
- Add multimodal features
- Optimize performance

## 📞 Support

See SETUP_COMPLETE.md for detailed setup instructions and tips!

---

**Happy Emotion Detecting! 😊**
This script uses the trained model (model.pkl) to predict emotions in real-time from the webcam feed. It captures face mesh points in each frame, feeds them into the model, and displays the predicted emotion on the video.

Real-time prediction using the trained machine learning model.
The live video feed is processed frame by frame to detect face mesh points.
The predicted emotion is overlaid on the video.
Usage
Step 1: Data Generation
Run the datagen.py script to capture and store face mesh data. The data is stored in data.csv with the class label for each frame.


python datagen.py
Step 2: Model Training
Train the model using the captured data by running the training.py script. The trained model will be saved as model.pkl.


python training.py
Step 3: Emotion Prediction
Run the test.py script to start real-time emotion prediction using your webcam.
python test.py

Customization
Adding New Emotions: To train the model on additional emotions, modify the class_name in datagen.py to the desired emotion and capture new data.
Model Parameters: You can replace the Logistic Regression model with other classifiers (e.g., SVM or Random Forest) by modifying the training.py script.
Video Input: Change the cap = cv2.VideoCapture(1) in datagen.py and test.py to cap = cv2.VideoCapture('video.mp4') if you want to use a video file instead of live webcam input.

Dependencies
cvzone: For face mesh detection and easy text overlay.
OpenCV: For video processing and capturing frames from the webcam.
NumPy: For handling numerical operations.
Scikit-learn: For machine learning algorithms and data preprocessing.
To install the dependencies, simply run:
pip install -r requirements.txt

License
This project is licensed under the MIT License. You are free to use, modify, and distribute this project.

