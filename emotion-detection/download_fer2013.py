"""
Helper script to download FER-2013 dataset from Kaggle.

Instructions:
1. Install Kaggle CLI: pip install kaggle
2. Get Kaggle API credentials:
   - Go to https://www.kaggle.com/settings
   - Click "Create New API Token"
   - Save kaggle.json to ~/.kaggle/ (or C:\\Users\\YourName\\.kaggle\\ on Windows)
3. Run this script: python download_fer2013.py
"""

import os
import subprocess
import zipfile
from pathlib import Path

def download_fer2013():
    """Download FER-2013 dataset from Kaggle."""
    
    print("FER-2013 Dataset Downloader")
    print("=" * 50)
    
    # Check if kaggle is installed
    try:
        import kaggle
        print("✓ Kaggle CLI is installed")
    except ImportError:
        print("✗ Kaggle CLI not found. Installing...")
        subprocess.run(["pip", "install", "kaggle"], check=True)
        print("✓ Kaggle CLI installed")
    
    # Check for API credentials
    kaggle_path = Path.home() / ".kaggle" / "kaggle.json"
    if not kaggle_path.exists():
        print("\n⚠ Kaggle API credentials not found!")
        print("\nTo download from Kaggle, you need to:")
        print("1. Go to https://www.kaggle.com/settings")
        print("2. Click 'Create New API Token'")
        print("3. Save kaggle.json to:", kaggle_path.parent)
        print("\nAlternatively, manually download from:")
        print("https://www.kaggle.com/datasets/msambare/fer2013")
        return False
    
    print("✓ Kaggle credentials found")
    
    # Create data directory
    data_dir = Path("fer2013_data")
    data_dir.mkdir(exist_ok=True)
    
    print(f"\nDownloading FER-2013 to {data_dir}...")
    
    try:
        # Download dataset
        subprocess.run([
            "kaggle", "datasets", "download", 
            "-d", "msambare/fer2013",
            "-p", str(data_dir),
            "--unzip"
        ], check=True)
        
        print("✓ Download complete!")
        print(f"\nDataset location: {data_dir.absolute()}")
        print("\nNext step: Run conversion script")
        print(f"  python convert_dataset.py --dataset {data_dir / 'fer2013'}")
        
        return True
        
    except subprocess.CalledProcessError as e:
        print(f"\n✗ Download failed: {e}")
        print("\nAlternative: Download manually from:")
        print("https://www.kaggle.com/datasets/msambare/fer2013")
        print(f"Extract to: {data_dir.absolute()}")
        return False

if __name__ == "__main__":
    download_fer2013()
