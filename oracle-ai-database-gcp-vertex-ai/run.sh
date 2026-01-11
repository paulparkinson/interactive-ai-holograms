#!/bin/bash
# Load environment variables from .env file
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# Run Streamlit app with external access enabled
streamlit run rag_app_ui.py --server.address 0.0.0.0 --server.port 8501
