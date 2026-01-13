"""
Oracle AI Database Full ADK Agent
Full Google Agent Development Kit (ADK) implementation with agent reasoning,
multi-step processing, conversation flow, and future MCP server integration
"""
import os
import requests
from dotenv import load_dotenv
from google.cloud import aiplatform
from vertexai.preview import reasoning_engines
from typing import Dict, Any

# Load environment variables
load_dotenv()

class OracleRAGFullAgent:
    """Full ADK Agent with reasoning engine for Oracle Database RAG"""
    
    def __init__(self, api_url: str, project_id: str, location: str):
        """
        Initialize the Full Oracle RAG Agent
        
        Args:
            api_url: Base URL of the Oracle RAG API
            project_id: GCP project ID
            location: GCP region
        """
        self.api_url = api_url.rstrip('/')
        self.project_id = project_id
        self.location = location
        self.agent = None
        self.conversation_history = []
        
        # Initialize Vertex AI
        aiplatform.init(project=project_id, location=location)
        
        print("🤖 Initializing ADK Reasoning Engine...")
        self.agent = self.create_agent()
        print("✓ ADK Agent ready!\n")
        
    def query_oracle_rag(self, query: str, top_k: int = 5) -> dict:
        """
        Query the Oracle RAG knowledge base
        
        Args:
            query: The question to ask
            top_k: Number of similar chunks to retrieve
            
        Returns:
            Dictionary with answer and metadata
        """
        try:
            response = requests.post(
                f"{self.api_url}/query",
                json={"query": query, "top_k": top_k},
                timeout=120
            )
            response.raise_for_status()
            return response.json()
        except requests.exceptions.Timeout:
            return {
                "error": "Request timed out",
                "answer": "Knowledge base query timed out. Please try again."
            }
        except requests.exceptions.ConnectionError as e:
            return {
                "error": f"Cannot connect to API at {self.api_url}",
                "answer": "Cannot connect to the knowledge base API."
            }
        except requests.exceptions.RequestException as e:
            return {
                "error": f"API error: {str(e)}",
                "answer": "Failed to query the knowledge base."
            }
    
    def get_api_status(self) -> dict:
        """Get the status of the Oracle RAG API"""
        try:
            response = requests.get(f"{self.api_url}/status", timeout=30)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            return {"error": str(e), "status": "unavailable"}
    
    def create_agent(self):
        """
        Create a full ADK reasoning engine agent with Oracle RAG tools
        
        Returns:
            Reasoning engine instance with full agent capabilities
        """
        # Tool function implementations (closures that capture self)
        def query_oracle_database(query: str, top_k: int = 5) -> str:
            """
            Query the Oracle Database knowledge base
            
            Args:
                query: The search query about Oracle Database
                top_k: Number of document chunks to retrieve (default: 5)
                
            Returns:
                Answer with context metadata
            """
            print(f"  🔧 Tool called: query_oracle_database(query='{query[:50]}...', top_k={top_k})")
            result = self.query_oracle_rag(query, top_k)
            
            if "error" in result:
                return f"Error: {result['answer']}"
            
            # Format response with metadata
            response = f"{result['answer']}\n\n"
            response += f"[Source: {len(result.get('context_chunks', []))} document chunks, "
            response += f"processed in {result.get('total_time', 0):.2f}s]"
            
            return response
        
        def check_knowledge_base_status() -> str:
            """
            Check the Oracle Database knowledge base status
            
            Returns:
                Status information including document count and connectivity
            """
            print(f"  🔧 Tool called: check_knowledge_base_status()")
            status = self.get_api_status()
            
            if "error" in status:
                return f"Knowledge base unavailable: {status['error']}"
            
            return (
                f"Knowledge Base Status:\n"
                f"- Status: {status['status']}\n"
                f"- Documents: {status['document_count']} chunks\n"
                f"- Database: {'Connected' if status['database_connected'] else 'Disconnected'}\n"
                f"- Models: {'Loaded' if status['models_loaded'] else 'Not loaded'}"
            )
        
        # Define tools in ADK format
        tools = [
            {
                "function_declarations": [
                    {
                        "name": "query_oracle_database",
                        "description": "Search the Oracle Database knowledge base for information about Oracle Database features, spatial capabilities, vector search, JSON features, SQL enhancements, and other database topics. Use this for technical questions that require specific documentation or feature details.",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "query": {
                                    "type": "string",
                                    "description": "The detailed question or search query about Oracle Database"
                                },
                                "top_k": {
                                    "type": "integer",
                                    "description": "Number of relevant document chunks to retrieve (1-20). Use higher values for broad topics, lower for specific questions.",
                                    "default": 5
                                }
                            },
                            "required": ["query"]
                        }
                    },
                    {
                        "name": "check_knowledge_base_status",
                        "description": "Check the status and availability of the Oracle Database knowledge base system, including document count, database connectivity, and model status. Use when users ask about system health or availability.",
                        "parameters": {
                            "type": "object",
                            "properties": {}
                        }
                    }
                ]
            }
        ]
        
        # Enhanced agent instructions for full reasoning
        instructions = """You are an expert Oracle Database AI assistant with access to a comprehensive knowledge base.

**Your Capabilities:**
- Access to Oracle Database documentation through the knowledge base
- Ability to perform multi-step reasoning to answer complex questions
- Maintain conversation context across multiple queries
- Decide when to use the knowledge base vs. your general knowledge

**When to Use Tools:**
1. Use `query_oracle_database` when users ask about:
   - Specific Oracle Database features or functionality
   - SQL syntax, commands, or best practices
   - Database configuration or administration
   - Performance optimization
   - New features in recent Oracle versions
   - Technical implementation details

2. Use `check_knowledge_base_status` when users ask about:
   - System availability
   - How many documents are available
   - Whether the knowledge base is working

**Multi-Step Reasoning:**
- For complex questions, break them into sub-queries
- Make multiple tool calls if needed to gather comprehensive information
- Synthesize information from multiple sources
- Ask clarifying questions if the user's intent is unclear

**Conversation Flow:**
- Remember context from previous questions in the conversation
- Reference earlier responses when relevant
- Build upon previous answers for follow-up questions

**Response Style:**
- Be concise but thorough
- Cite specific features or capabilities when possible
- If knowledge base doesn't have the answer, say so clearly and use general knowledge cautiously
- Format technical content clearly with examples when helpful

**Future Capabilities (in development):**
- Integration with MCP (Model Context Protocol) servers for extended functionality
- Additional data sources and tools
- Enhanced multi-modal capabilities"""
        
        # Create ADK LangchainAgent
        agent = reasoning_engines.LangchainAgent(
            model="gemini-2.0-flash-exp",
            tools=tools,
            agent_executor_kwargs={
                "return_intermediate_steps": True,
                "max_iterations": 10,  # Allow multi-step reasoning
                "early_stopping_method": "generate"
            },
            model_kwargs={
                "temperature": 0.7,
                "max_output_tokens": 4096,  # Higher for detailed responses
                "top_p": 0.95,
                "top_k": 40
            }
        )
        
        # Set up tool routing with our implementations
        agent.set_up(
            {
                "query_oracle_database": query_oracle_database,
                "check_knowledge_base_status": check_knowledge_base_status
            },
            instructions=instructions
        )
        
        return agent
    
    def query(self, user_input: str) -> Dict[str, Any]:
        """
        Query the agent with full reasoning capabilities
        
        Args:
            user_input: User's question or request
            
        Returns:
            Agent response with reasoning steps
        """
        try:
            print("🤔 Agent reasoning...\n")
            
            # Query the ADK agent
            response = self.agent.query(
                input=user_input,
                config={
                    "return_intermediate_steps": True
                }
            )
            
            # Store in conversation history for context
            self.conversation_history.append({
                "user": user_input,
                "agent": response
            })
            
            return response
            
        except Exception as e:
            return {
                "output": f"Error during agent reasoning: {str(e)}",
                "intermediate_steps": []
            }
    
    def run_cli(self):
        """Run interactive CLI with full ADK agent"""
        print("=" * 80)
        print("Oracle Database Full ADK Agent (Multi-Step Reasoning & Conversation)")
        print("=" * 80)
        print(f"API URL: {self.api_url}")
        print(f"Project: {self.project_id}")
        print(f"Region: {self.location}")
        print()
        
        # Check API status
        status = self.get_api_status()
        if "error" not in status:
            print(f"✓ Knowledge base: {status['document_count']} documents")
            print(f"✓ Status: {status['status']}")
        else:
            print(f"⚠️  Warning: {status.get('error', 'API unavailable')}")
        
        print()
        print("Ask questions about Oracle Database. The agent will use reasoning to answer.")
        print("Commands: 'quit' to exit, 'history' to see conversation, 'clear' to reset")
        print("-" * 80)
        print()
        
        while True:
            try:
                user_input = input("\n🧑 You: ").strip()
                
                if not user_input:
                    continue
                    
                if user_input.lower() in ['quit', 'exit', 'q']:
                    print("\nGoodbye!")
                    break
                
                if user_input.lower() == 'history':
                    print("\n📜 Conversation History:")
                    for i, entry in enumerate(self.conversation_history, 1):
                        print(f"\n[{i}] User: {entry['user'][:80]}...")
                        print(f"    Agent: {entry['agent'].get('output', '')[:80]}...")
                    print()
                    continue
                
                if user_input.lower() == 'clear':
                    self.conversation_history = []
                    print("✓ Conversation history cleared\n")
                    continue
                
                # Query the agent with full reasoning
                response = self.query(user_input)
                
                # Display intermediate steps if available
                if response.get("intermediate_steps"):
                    print("\n📝 Reasoning Steps:")
                    for step in response["intermediate_steps"]:
                        if len(step) >= 2:
                            action, observation = step[0], step[1]
                            print(f"  → {action}")
                
                # Display final answer
                print(f"\n🤖 Agent: {response.get('output', 'No response generated')}\n")
                
            except KeyboardInterrupt:
                print("\n\nGoodbye!")
                break
            except Exception as e:
                print(f"\n❌ Error: {str(e)}\n")

def main():
    """Main entry point"""
    # Configuration
    api_url = os.getenv("ORACLE_RAG_API_URL", "http://34.48.146.146:8501")
    project_id = os.getenv("GCP_PROJECT_ID", "adb-pm-prod")
    location = os.getenv("GCP_REGION", "us-central1")
    
    print("Starting Full ADK Agent...\n")
    
    # Create and run full agent
    agent = OracleRAGFullAgent(api_url, project_id, location)
    agent.run_cli()

if __name__ == "__main__":
    main()
