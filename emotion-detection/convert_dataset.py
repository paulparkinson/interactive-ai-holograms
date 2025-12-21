"""
Convert FER-2013 (or other image datasets) to face mesh landmark format.
This script extracts 468 facial landmarks from emotion images and saves them to data.csv.

FER-2013 Dataset:
- Download from: https://www.kaggle.com/datasets/msambare/fer2013
- Or use: kaggle datasets download -d msambare/fer2013
"""

import cv2
import numpy as np
import pandas as pd
import mediapipe as mp
import os
from pathlib import Path
from tqdm import tqdm

# Initialize mediapipe face mesh
mp_face_mesh = mp.solutions.face_mesh
face_mesh = mp_face_mesh.FaceMesh(
    static_image_mode=True,
    max_num_faces=1,
    min_detection_confidence=0.5
)

def extract_face_mesh(image):
    """Extract 468 face mesh landmarks from an image."""
    # Convert to RGB
    rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    
    # Process image
    results = face_mesh.process(rgb_image)
    
    if results.multi_face_landmarks:
        face_landmarks = results.multi_face_landmarks[0]
        
        # Extract all 468 landmarks as NORMALIZED coordinates [x1, y1, x2, y2, ...]
        # Using normalized coordinates (0-1 range) makes the model frame-size independent
        face_data = []
        for landmark in face_landmarks.landmark:
            face_data.extend([landmark.x, landmark.y])
        
        return face_data
    
    return None

def convert_fer2013(dataset_path, output_csv='data.csv', append=True):
    """
    Convert FER-2013 dataset to face mesh format.
    
    Args:
        dataset_path: Path to FER-2013 folder (should contain train/test subdirs)
        output_csv: Output CSV file name
        append: If True, append to existing data.csv, if False, create new
    """
    
    # FER-2013 emotion mapping
    emotion_map = {
        'angry': 'angry',
        'disgust': 'disgust',
        'fear': 'fear',
        'happy': 'happy',
        'sad': 'sad',
        'surprise': 'surprise',
        'neutral': 'neutral'
    }
    
    all_data = []
    skipped = 0
    
    # Process train and test folders
    for split in ['train', 'test']:
        split_path = Path(dataset_path) / split
        if not split_path.exists():
            print(f"Warning: {split_path} not found, skipping...")
            continue
        
        print(f"\nProcessing {split} set...")
        
        # Iterate through emotion folders
        for emotion_folder in split_path.iterdir():
            if not emotion_folder.is_dir():
                continue
            
            emotion = emotion_folder.name.lower()
            if emotion not in emotion_map:
                print(f"Skipping unknown emotion: {emotion}")
                continue
            
            emotion_label = emotion_map[emotion]
            image_files = list(emotion_folder.glob('*.png')) + list(emotion_folder.glob('*.jpg'))
            
            print(f"  Processing {emotion_label}: {len(image_files)} images")
            
            for img_path in tqdm(image_files, desc=f"  {emotion_label}"):
                # Read image
                image = cv2.imread(str(img_path))
                if image is None:
                    skipped += 1
                    continue
                
                # Extract face mesh
                face_data = extract_face_mesh(image)
                
                if face_data:
                    # Add emotion label at the start
                    row = [emotion_label] + face_data
                    all_data.append(row)
                else:
                    skipped += 1
    
    print(f"\n✓ Successfully processed {len(all_data)} images")
    print(f"✗ Skipped {skipped} images (no face detected)")
    
    # Create column names
    columns = ['Class']
    for i in range(1, 469):
        columns.extend([f'x{i}', f'y{i}'])
    
    # Create DataFrame
    new_df = pd.DataFrame(all_data, columns=columns)
    
    # Append or create new CSV
    if append and os.path.exists(output_csv):
        print(f"\nAppending to existing {output_csv}...")
        existing_df = pd.read_csv(output_csv)
        combined_df = pd.concat([existing_df, new_df], ignore_index=True)
        combined_df.to_csv(output_csv, index=False)
        print(f"Total samples now: {len(combined_df)}")
    else:
        print(f"\nCreating new {output_csv}...")
        new_df.to_csv(output_csv, index=False)
        print(f"Total samples: {len(new_df)}")
    
    # Print emotion distribution
    print("\nEmotion distribution:")
    if append and os.path.exists(output_csv):
        df = pd.read_csv(output_csv)
        print(df['Class'].value_counts().sort_index())
    else:
        print(new_df['Class'].value_counts().sort_index())

def convert_from_csv(csv_path, output_csv='data.csv', append=True):
    """
    Convert FER-2013 from CSV format (pixels column).
    Alternative if you have the original FER-2013 CSV file.
    
    Args:
        csv_path: Path to fer2013.csv
        output_csv: Output CSV file name
        append: If True, append to existing data.csv
    """
    print(f"Loading {csv_path}...")
    df = pd.read_csv(csv_path)
    
    emotion_map = {
        0: 'angry',
        1: 'disgust',
        2: 'fear',
        3: 'happy',
        4: 'sad',
        5: 'surprise',
        6: 'neutral'
    }
    
    all_data = []
    skipped = 0
    
    print(f"Processing {len(df)} images...")
    for idx, row in tqdm(df.iterrows(), total=len(df)):
        # Get emotion
        emotion = emotion_map[row['emotion']]
        
        # Convert pixel string to image
        pixels = np.array([int(p) for p in row['pixels'].split()], dtype=np.uint8)
        image = pixels.reshape(48, 48)
        image = cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)
        
        # Extract face mesh
        face_data = extract_face_mesh(image)
        
        if face_data:
            row_data = [emotion] + face_data
            all_data.append(row_data)
        else:
            skipped += 1
    
    print(f"\n✓ Successfully processed {len(all_data)} images")
    print(f"✗ Skipped {skipped} images (no face detected)")
    
    # Create column names
    columns = ['Class']
    for i in range(1, 469):
        columns.extend([f'x{i}', f'y{i}'])
    
    # Create DataFrame
    new_df = pd.DataFrame(all_data, columns=columns)
    
    # Append or create new CSV
    if append and os.path.exists(output_csv):
        print(f"\nAppending to existing {output_csv}...")
        existing_df = pd.read_csv(output_csv)
        combined_df = pd.concat([existing_df, new_df], ignore_index=True)
        combined_df.to_csv(output_csv, index=False)
        print(f"Total samples now: {len(combined_df)}")
    else:
        print(f"\nCreating new {output_csv}...")
        new_df.to_csv(output_csv, index=False)
        print(f"Total samples: {len(new_df)}")
    
    print("\nEmotion distribution:")
    if append and os.path.exists(output_csv):
        df = pd.read_csv(output_csv)
        print(df['Class'].value_counts().sort_index())
    else:
        print(new_df['Class'].value_counts().sort_index())

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description='Convert emotion dataset to face mesh format')
    parser.add_argument('--dataset', type=str, required=True, 
                        help='Path to FER-2013 folder or CSV file')
    parser.add_argument('--output', type=str, default='data.csv',
                        help='Output CSV file (default: data.csv)')
    parser.add_argument('--replace', action='store_true',
                        help='Replace existing data instead of appending')
    parser.add_argument('--csv', action='store_true',
                        help='Input is CSV format (fer2013.csv) instead of image folders')
    
    args = parser.parse_args()
    
    if args.csv:
        convert_from_csv(args.dataset, args.output, append=not args.replace)
    else:
        convert_fer2013(args.dataset, args.output, append=not args.replace)
    
    print("\n✓ Conversion complete! You can now run training.py to train the model.")
