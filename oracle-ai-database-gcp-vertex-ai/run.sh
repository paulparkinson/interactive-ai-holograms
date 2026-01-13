#!/bin/bash
# Load environment variables from .env file
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# Run Streamlit app on port 8502 to avoid conflict with FastAPI on 8501
streamlit run rag_app_ui.py --server.address 0.0.0.0 --server.port 8502
