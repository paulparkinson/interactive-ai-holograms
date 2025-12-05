#!/usr/bin/env bash
set -euo pipefail

echo "This script will create a .env and bring up the stack (Oracle DB, vector worker, Spring app)."

read -p "Container runtime (docker or podman, default: docker): " CONTAINER_RUNTIME
CONTAINER_RUNTIME=${CONTAINER_RUNTIME:-docker}

# Validate container runtime
if [[ "$CONTAINER_RUNTIME" != "docker" && "$CONTAINER_RUNTIME" != "podman" ]]; then
  echo "Error: CONTAINER_RUNTIME must be 'docker' or 'podman'"
  exit 1
fi

echo "Using $CONTAINER_RUNTIME as container runtime"

read -p "Oracle DB admin user (default: PDBADMIN): " ORACLE_USER
ORACLE_USER=${ORACLE_USER:-PDBADMIN}

read -s -p "Oracle DB password (will be used for the container 'ORACLE_PWD'): " ORACLE_PWD
echo
if [ -z "$ORACLE_PWD" ]; then
  echo "Password cannot be empty."; exit 1
fi

read -p "Oracle DB service name (default: FREEPDB1): " ORACLE_SERVICE
ORACLE_SERVICE=${ORACLE_SERVICE:-FREEPDB1}

read -p "Host path for Oracle data (absolute, default: /data/oracle): " ORACLE_DATA_DIR
ORACLE_DATA_DIR=${ORACLE_DATA_DIR:-/data/oracle}

read -p "Download default text model for PDFs/text? (y/n): " INSTALL_TEXT_MODEL
INSTALL_TEXT_MODEL=${INSTALL_TEXT_MODEL:-n}

read -p "Download image embedding model for images? (y/n): " INSTALL_IMAGE_MODEL
INSTALL_IMAGE_MODEL=${INSTALL_IMAGE_MODEL:-n}

mkdir -p "$ORACLE_DATA_DIR"
mkdir -p ./models

if [[ "$INSTALL_TEXT_MODEL" =~ ^[Yy] ]]; then
  echo "Default text model: sentence-transformers/all-MiniLM-L6-v2 (ONNX)"
  read -p "Text model URL (press Enter for default, or provide custom URL): " TEXT_MODEL_URL
  # Default: Optimum-exported sentence transformer from Hugging Face (public, no auth needed)
  TEXT_MODEL_URL=${TEXT_MODEL_URL:-https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx}
  if [ -n "$TEXT_MODEL_URL" ]; then
    echo "Downloading text ONNX model from $TEXT_MODEL_URL ..."
    wget --no-check-certificate -O ./models/text_model.onnx "$TEXT_MODEL_URL" || echo "Warning: text model download failed; will use fallback embeddings."
  else
    echo "Skipping text model download."
  fi
fi

if [[ "$INSTALL_IMAGE_MODEL" =~ ^[Yy] ]]; then
  echo "Default image model: OpenAI CLIP ViT-B/32 (ONNX)"
  read -p "Image model URL (press Enter for default, or provide custom URL): " IMAGE_MODEL_URL
  # Default: CLIP ViT-B/32 visual encoder ONNX from public source (no auth needed)
  # Note: You may need to find a reliable public ONNX export; this is a placeholder URL
  IMAGE_MODEL_URL=${IMAGE_MODEL_URL:-https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/vision_model.onnx}
  if [ -n "$IMAGE_MODEL_URL" ]; then
    echo "Downloading image ONNX model from $IMAGE_MODEL_URL ..."
    wget --no-check-certificate -O ./models/image_model.onnx "$IMAGE_MODEL_URL" || echo "Warning: image model download failed; will use fallback embeddings."
  else
    echo "Skipping image model download."
  fi
fi

read -p "Use Oracle 26ai+ native VECTOR search? (y/n, default: y): " USE_VECTOR
USE_VECTOR=${USE_VECTOR:-y}
if [[ "$USE_VECTOR" =~ ^[Yy]$ ]]; then
  USE_ORACLE_VECTOR_SEARCH="true"
  echo "Will use Oracle VECTOR type and VECTOR_DISTANCE() for search"
else
  USE_ORACLE_VECTOR_SEARCH="false"
  echo "Will use Python-based cosine similarity (legacy CLOB storage)"
fi

cat > .env <<EOF
ORACLE_PWD=${ORACLE_PWD}
ORACLE_USER=${ORACLE_USER}
ORACLE_SERVICE=${ORACLE_SERVICE}
ORACLE_DATA_DIR=${ORACLE_DATA_DIR}
MODEL_PATH_TEXT=/models/text_model.onnx
MODEL_PATH_IMAGE=/models/image_model.onnx
USE_ORACLE_VECTOR_SEARCH=${USE_ORACLE_VECTOR_SEARCH}
CONTAINER_RUNTIME=${CONTAINER_RUNTIME}
EOF

echo ".env written. Starting ${CONTAINER_RUNTIME} compose (build if necessary)."

sg ${CONTAINER_RUNTIME} -c "${CONTAINER_RUNTIME} compose up -d --build"

echo "All services started."
echo "- Oracle DB: localhost:1521"
echo "- Python app: http://localhost:8001"
echo "- Spring app: http://localhost:8080"
echo "- Vector search mode: ${USE_ORACLE_VECTOR_SEARCH}"

echo "Note: The vector worker will attempt to connect to Oracle using the provided credentials."
echo "If the Oracle service name in your DB image differs, update .env ORACLE_SERVICE accordingly."
