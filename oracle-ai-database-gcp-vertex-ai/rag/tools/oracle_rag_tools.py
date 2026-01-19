"""
Oracle Database RAG Tools for ADK

Provides tools for querying Oracle Database documentation via REST API.
"""
import os
import requests
from google.adk.tools import FunctionTool
from typing import Dict, Any, Optional


def query_oracle_database(
    query: str,
    top_k: Optional[int] = None
) -> Dict[str, Any]:
    """
    Search the Oracle Database knowledge base for information about Oracle Database 
    features, spatial capabilities, vector search, JSON features, SQL enhancements, 
    and other database topics.
    
    Args:
        query: The question to ask about Oracle Database
        top_k: Number of similar chunks to retrieve (default: 5)
        
    Returns:
        A dictionary containing the search results with answer and sources
    """
    if top_k is None:
        top_k = 5
        
    api_url = os.getenv('RAG_API_URL', 'http://localhost:8501')
    
    try:
        response = requests.post(
            f"{api_url}/query",
            json={"query": query, "top_k": top_k},
            timeout=30
        )
        response.raise_for_status()
        result = response.json()
        
        # Format the response nicely
        answer = result.get('answer', 'No answer found')
        sources = result.get('sources', [])
        
        formatted_response = f"📚 **Answer:**\n{answer}\n"
        
        if sources:
            formatted_response += f"\n🔗 **Sources:**\n"
            for i, source in enumerate(sources, 1):
                formatted_response += f"  {i}. {source}\n"
        
        return {
            "status": "success",
            "answer": answer,
            "sources": sources,
            "message": formatted_response
        }
        
    except requests.exceptions.RequestException as e:
        return {
            "status": "error",
            "error_message": str(e),
            "message": f"❌ Error querying database: {str(e)}"
        }


# Create FunctionTool from the function
query_oracle_tool = FunctionTool(query_oracle_database)
