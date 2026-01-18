#!/bin/bash
# Fix langchain installation for ADK agent

echo "Installing complete langchain ecosystem..."

# Uninstall all langchain packages
pip uninstall -y langchain langchain-core langchain-community langchain-text-splitters langchain-google-vertexai

# Reinstall with proper versions
pip install langchain==0.3.18
pip install langchain-core==0.3.51
pip install langchain-community==0.3.18
pip install langchain-text-splitters==0.3.4
pip install langchain-google-vertexai==2.0.18

echo "✓ Complete - try running the agent again"
