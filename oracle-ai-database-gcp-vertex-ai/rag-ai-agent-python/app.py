"""
Oracle RAG AI Agent - Flask REST API

Flask application providing RESTful API for Retrieval-Augmented Generation (RAG) using:
- Oracle Database 23ai for vector storage
- Vertex AI embeddings (text-embedding-004)
- Vertex AI Gemini LLM (gemini-2.5-flash)
- LangChain for orchestration

This service can be integrated with Google Cloud Dialogflow conversational agents
as a custom tool/datastore.
"""

import os
import time
import logging
from typing import List, Dict, Any
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv
import oracledb
import vertexai
from vertexai.language_models import TextEmbeddingModel
from vertexai.preview.generative_models import GenerativeModel
from langchain_google_vertexai import VertexAIEmbeddings, ChatVertexAI
from langchain_community.vectorstores.oraclevs import OracleVS
from langchain_community.vectorstores.utils import DistanceStrategy
from langchain_core.prompts import PromptTemplate
from langchain_core.runnables import RunnablePassthrough
from langchain_core.output_parsers import StrOutputParser

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Initialize Flask app
app = Flask(__name__)
CORS(app)

# Configuration from environment variables
ORACLE_USERNAME = os.getenv('ORACLE_USERNAME', 'ADMIN')
ORACLE_PASSWORD = os.getenv('ORACLE_PASSWORD')
ORACLE_DSN = os.getenv('ORACLE_DSN', 'paulparkdb_high')
ORACLE_WALLET_LOCATION = os.path.expanduser(os.getenv('ORACLE_WALLET_LOCATION', '~/wallet'))
ORACLE_WALLET_PASSWORD = os.getenv('ORACLE_WALLET_PASSWORD')
ORACLE_VECTOR_TABLE = os.getenv('ORACLE_VECTOR_TABLE', 'RAG_TAB')

VERTEX_PROJECT_ID = os.getenv('VERTEX_PROJECT_ID', 'adb-pm-prod')
VERTEX_LOCATION = os.getenv('VERTEX_LOCATION', 'us-central1')
VERTEX_EMBEDDING_MODEL = os.getenv('VERTEX_EMBEDDING_MODEL', 'text-embedding-004')
VERTEX_LLM_MODEL = os.getenv('VERTEX_LLM_MODEL', 'gemini-2.5-flash')

RAG_TOP_K = int(os.getenv('RAG_TOP_K', '10'))
RAG_PROMPT_TEMPLATE = os.getenv(
    'RAG_PROMPT_TEMPLATE',
    'Answer the question based only on the following context: {context} Question: {question}'
)

# Global variables for cached connections
db_connection = None
embeddings = None
llm = None

def init_vertex_ai():
    """Initialize Vertex AI SDK"""
    logger.info(f"Initializing Vertex AI with project: {VERTEX_PROJECT_ID}, location: {VERTEX_LOCATION}")
    vertexai.init(project=VERTEX_PROJECT_ID, location=VERTEX_LOCATION)
    
    global embeddings, llm
    embeddings = VertexAIEmbeddings(model_name=VERTEX_EMBEDDING_MODEL)
    llm = ChatVertexAI(
        model_name=VERTEX_LLM_MODEL,
        max_output_tokens=8192,
        temperature=0.7,
        top_p=0.95,
        top_k=40
    )
    logger.info("Vertex AI initialized successfully")

def get_db_connection():
    """Get Oracle Database connection"""
    global db_connection
    
    if db_connection is None or not db_connection.is_healthy():
        logger.info(f"Connecting to Oracle Database: {ORACLE_DSN}")
        
        db_connection = oracledb.connect(
            config_dir=ORACLE_WALLET_LOCATION,
            user=ORACLE_USERNAME,
            password=ORACLE_PASSWORD,
            dsn=ORACLE_DSN,
            wallet_location=ORACLE_WALLET_LOCATION,
            wallet_password=ORACLE_WALLET_PASSWORD
        )
        logger.info("Oracle Database connection established")
    
    return db_connection

def execute_rag_query(question: str) -> Dict[str, Any]:
    """
    Execute RAG query: retrieve relevant context from Oracle vector DB
    and generate response using Gemini LLM
    """
    start_time = time.time()
    logger.info(f"Executing RAG query: {question}")
    
    try:
        # Get database connection
        connection = get_db_connection()
        
        # Initialize vector store
        knowledge_base = OracleVS(
            client=connection,
            embedding_function=embeddings,
            table_name=ORACLE_VECTOR_TABLE,
            distance_strategy=DistanceStrategy.DOT_PRODUCT
        )
        
        # Build RAG chain
        prompt = PromptTemplate.from_template(RAG_PROMPT_TEMPLATE)
        retriever = knowledge_base.as_retriever(search_kwargs={"k": RAG_TOP_K})
        
        chain = (
            {"context": retriever, "question": RunnablePassthrough()}
            | prompt
            | llm
            | StrOutputParser()
        )
        
        # Execute query
        logger.debug("Invoking RAG chain")
        response = chain.invoke(question)
        
        duration = time.time() - start_time
        logger.info(f"RAG query completed in {duration:.2f} seconds")
        
        return {
            "answer": response,
            "source": "Oracle Database 23ai Vector RAG",
            "responseTimeMs": int(duration * 1000)
        }
        
    except Exception as e:
        logger.error(f"Error executing RAG query: {str(e)}", exc_info=True)
        raise

# Initialize on startup
try:
    init_vertex_ai()
    get_db_connection()
    logger.info("Application initialization complete")
except Exception as e:
    logger.error(f"Failed to initialize application: {str(e)}", exc_info=True)

@app.route('/api/v1/query', methods=['POST'])
def query():
    """
    Query the RAG system
    
    Expected JSON body:
    {
        "question": "Your question here"
    }
    
    Returns:
    {
        "answer": "Generated answer",
        "source": "Oracle Database 23ai Vector RAG",
        "responseTimeMs": 1250
    }
    """
    try:
        data = request.get_json()
        
        if not data or 'question' not in data:
            return jsonify({
                "error": "Question is required",
                "status": 400
            }), 400
        
        question = data['question'].strip()
        
        if not question:
            return jsonify({
                "error": "Question cannot be empty",
                "status": 400
            }), 400
        
        logger.info(f"Received query request: {question}")
        
        result = execute_rag_query(question)
        
        return jsonify(result), 200
        
    except Exception as e:
        logger.error(f"Error processing request: {str(e)}", exc_info=True)
        return jsonify({
            "error": f"Failed to process query: {str(e)}",
            "status": 500
        }), 500

@app.route('/api/v1/health', methods=['GET'])
def health():
    """
    Health check endpoint
    
    Returns:
    {
        "status": "UP",
        "timestamp": "2026-01-09T10:30:00Z"
    }
    """
    try:
        # Test database connection
        connection = get_db_connection()
        is_healthy = connection.is_healthy()
        
        return jsonify({
            "status": "UP" if is_healthy else "DOWN",
            "timestamp": time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        }), 200 if is_healthy else 503
        
    except Exception as e:
        logger.error(f"Health check failed: {str(e)}", exc_info=True)
        return jsonify({
            "status": "DOWN",
            "error": str(e),
            "timestamp": time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        }), 503

@app.route('/api-docs', methods=['GET'])
def api_docs():
    """
    OpenAPI specification endpoint
    """
    spec = {
        "openapi": "3.0.0",
        "info": {
            "title": "Oracle RAG AI Agent API",
            "version": "1.0.0",
            "description": "REST API for Retrieval-Augmented Generation using Oracle Database 23ai and Vertex AI"
        },
        "servers": [
            {
                "url": f"http://localhost:{os.getenv('PORT', '8080')}",
                "description": "Local server"
            },
            {
                "url": "http://34.48.146.146:8080",
                "description": "GCP VM deployment"
            }
        ],
        "paths": {
            "/api/v1/query": {
                "post": {
                    "summary": "Query the RAG system",
                    "description": "Submit a question to retrieve relevant context and generate an answer",
                    "operationId": "queryRag",
                    "requestBody": {
                        "required": True,
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "object",
                                    "required": ["question"],
                                    "properties": {
                                        "question": {
                                            "type": "string",
                                            "description": "The user's question",
                                            "example": "Tell me more about JSON Relational Duality"
                                        }
                                    }
                                }
                            }
                        }
                    },
                    "responses": {
                        "200": {
                            "description": "Successful response",
                            "content": {
                                "application/json": {
                                    "schema": {
                                        "type": "object",
                                        "properties": {
                                            "answer": {"type": "string"},
                                            "source": {"type": "string"},
                                            "responseTimeMs": {"type": "integer"}
                                        }
                                    }
                                }
                            }
                        },
                        "400": {"description": "Invalid request"},
                        "500": {"description": "Internal server error"}
                    }
                }
            },
            "/api/v1/health": {
                "get": {
                    "summary": "Health check",
                    "description": "Check if the service is healthy",
                    "operationId": "healthCheck",
                    "responses": {
                        "200": {
                            "description": "Service is healthy",
                            "content": {
                                "application/json": {
                                    "schema": {
                                        "type": "object",
                                        "properties": {
                                            "status": {"type": "string", "enum": ["UP", "DOWN"]},
                                            "timestamp": {"type": "string"}
                                        }
                                    }
                                }
                            }
                        },
                        "503": {"description": "Service is unavailable"}
                    }
                }
            }
        }
    }
    
    return jsonify(spec), 200

@app.route('/', methods=['GET'])
def index():
    """Root endpoint with API information"""
    return jsonify({
        "name": "Oracle RAG AI Agent",
        "version": "1.0.0",
        "description": "REST API for Retrieval-Augmented Generation using Oracle Database 23ai and Vertex AI",
        "endpoints": {
            "query": "/api/v1/query",
            "health": "/api/v1/health",
            "docs": "/api-docs"
        }
    }), 200

if __name__ == '__main__':
    port = int(os.getenv('PORT', '8080'))
    logger.info(f"Starting Flask application on port {port}")
    app.run(host='0.0.0.0', port=port, debug=False)
