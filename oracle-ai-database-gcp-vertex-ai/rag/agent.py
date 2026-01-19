# ADK core imports
from google.adk.agents import Agent
from google.adk.tools.load_memory_tool import load_memory_tool

# Local tool imports
from rag.tools import corpus_tools, storage_tools, oracle_rag_tools
from rag.config import (
    AGENT_NAME,
    AGENT_MODEL,
    AGENT_OUTPUT_KEY
)


# Create the Oracle Database RAG agent
agent = Agent(
    name=AGENT_NAME,
    model=AGENT_MODEL,
    description="Agent for searching Oracle Database documentation and managing Vertex AI RAG corpora",
    instruction="""
    You are a helpful assistant that answers questions about Oracle Database and manages 
    RAG corpora in Vertex AI and Google Cloud Storage buckets.
    
    Your primary goal is to help users find information and manage resources.

    - Use emojis to make responses more friendly and readable:
      - ✅ for success
      - ❌ for errors
      - ℹ️ for info
      - 📚 for documentation
      - 🔗 for sources/links
      - 🗂️ for lists
      - 📄 for files or corpora

    You can help users with these tasks:

    1. ORACLE DATABASE QUESTIONS:
       - Use query_oracle_database(query="your question") to search Oracle Database documentation
       - Topics: Vector Search, Spatial features, JSON Duality, SELECT AI, SQL enhancements, etc.
       - Always cite sources when available
    
    2. GCS OPERATIONS:
       - Upload files to GCS buckets
       - Create, list, and get details of buckets
       - List files in buckets
    
    3. VERTEX AI RAG CORPUS MANAGEMENT:
       - Create, update, list and delete corpora
       - Import documents from GCS to a corpus
       - Query corpora for information
       
    When the user asks about Oracle Database, use the query_oracle_database tool.
    For other operations, use the appropriate Vertex AI or GCS tools.
    """,
    tools=[
        # Oracle Database RAG tool
        oracle_rag_tools.query_oracle_tool,
        
        # RAG corpus management tools
        corpus_tools.create_corpus_tool,
        corpus_tools.update_corpus_tool,
        corpus_tools.list_corpora_tool,
        corpus_tools.get_corpus_tool,
        corpus_tools.delete_corpus_tool,
        corpus_tools.import_document_tool,
        
        # RAG file management tools
        corpus_tools.list_files_tool,
        corpus_tools.get_file_tool,
        corpus_tools.delete_file_tool,
        
        # RAG query tools
        corpus_tools.query_rag_corpus_tool,
        corpus_tools.search_all_corpora_tool,
        
        # GCS bucket management tools
        storage_tools.create_bucket_tool,
        storage_tools.list_buckets_tool,
        storage_tools.get_bucket_details_tool,
        storage_tools.upload_file_gcs_tool,
        storage_tools.list_blobs_tool,
        
        # Memory tool for accessing conversation history
        load_memory_tool,
    ],
    # Output key automatically saves the agent's final response in state under this key
    output_key=AGENT_OUTPUT_KEY
)

root_agent = agent
