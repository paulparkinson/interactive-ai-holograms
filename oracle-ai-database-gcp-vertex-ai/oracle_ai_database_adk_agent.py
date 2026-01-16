"""
Oracle AI Database ADK Agent with MCP Integration
Google Agent Development Kit (ADK) agent that uses:
- Oracle RAG API for vector search
- Oracle MCP Server for direct database queries
"""
import os
import asyncio
import requests
from dotenv import load_dotenv
import vertexai
from vertexai.generative_models import GenerativeModel, Tool, FunctionDeclaration
from typing import Dict, Any

# Load environment variables
load_dotenv()

class OracleRAGAgent:
    """Agent that uses Oracle Database RAG API and MCP Server for answering questions"""
    
    def __init__(self, api_url: str, project_id: str, location: str, 
                 sqlcl_path: str = "/opt/sqlcl/bin/sql",
                 wallet_path: str = None):
        """
        Initialize the Oracle RAG Agent with MCP integration
        
        Args:
            api_url: Base URL of the Oracle RAG API
            project_id: GCP project ID
            location: GCP region
            sqlcl_path: Path to SQLcl executable for MCP server
            wallet_path: Path to Oracle wallet directory
        """
        self.api_url = api_url.rstrip('/')
        self.project_id = project_id
        self.location = location
        self.sqlcl_path = sqlcl_path
        self.wallet_path = wallet_path or os.path.expanduser("~/wallet")
        self.agent = None
        self.conversation_history = []
        
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
                timeout=30  # Timeout for API calls
            )
            response.raise_for_status()
            return response.json()
        except requests.exceptions.Timeout:
            return {
                "error": "Request timed out. The API might be processing a large query.",
                "answer": "I couldn't retrieve information from the knowledge base (timeout). Please try again."
            }
        except requests.exceptions.ConnectionError as e:
            return {
                "error": f"Cannot connect to API at {self.api_url}. Is it running? {str(e)}",
                "answer": "I couldn't connect to the knowledge base API. Please verify it's running."
            }
        except requests.exceptions.RequestException as e:
            return {
                "error": f"Failed to query Oracle RAG API: {str(e)}",
                "answer": "I couldn't retrieve information from the knowledge base. Please try again."
            }
    
    def get_api_status(self) -> dict:
        """Get the status of the Oracle RAG API"""
        try:
            response = requests.get(f"{self.api_url}/status", timeout=30)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            return {
                "error": f"Failed to get API status: {str(e)}",
                "status": "unavailable"
            }
    
    async def create_agent_async(self):
        """
        Create an agent with Oracle RAG API and MCP database tools
        
        Returns:
            GenerativeModel instance configured with tools
        """
        # Initialize Vertex AI
        vertexai.init(project=self.project_id, location=self.location)
        
        # Define function declarations for RAG tool
        query_oracle_function = FunctionDeclaration(
            name="query_oracle_database",
            description="Search the Oracle Database knowledge base for information about Oracle Database features, spatial capabilities, vector search, JSON features, SQL enhancements, and other database topics.",
            parameters={
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
        )
        
        # Create tool with function declarations
        oracle_tool = Tool(
            function_declarations=[query_oracle_function]
        )
        
        # Enhanced system instructions
        instructions = """You are an expert Oracle Database AI assistant with access to a comprehensive knowledge base.

**Your Capabilities:**
- Access to Oracle Database documentation through the knowledge base
- Ability to answer questions about Oracle features, SQL, and database functionality

**When to Use Tools:**
Use `query_oracle_database` when users ask about:
- Specific Oracle Database features or functionality
- SQL syntax, commands, or best practices
- Database configuration or administration
- Performance optimization
- New features in recent Oracle versions
- Technical implementation details

**Response Style:**
- Be concise but thorough
- Cite specific features or capabilities when possible
- If knowledge base doesn't have the answer, say so clearly
- Format technical content clearly with examples when helpful

Be helpful and technically accurate."""
        
        # Create Gemini model with tools
        model = GenerativeModel(
            "gemini-2.0-flash-exp",
            tools=[oracle_tool],
            system_instruction=instructions
        )
        
        return model
    
    def execute_function_call(self, function_name: str, args: dict) -> str:
        """Execute a function call from the agent"""
        if function_name == "query_oracle_database":
            query = args.get("query", "")
            top_k = args.get("top_k", 5)
            result = self.query_oracle_rag(query, top_k)
            
            if "error" in result:
                return f"Error: {result['answer']}"
            
            response = f"{result['answer']}\n\n"
            response += f"[Source: {len(result.get('context_chunks', []))} document chunks, "
            response += f"processed in {result.get('total_time', 0):.2f}s]"
            return response
        
        return f"Unknown function: {function_name}"
    
    def query_sync(self, user_input: str) -> Dict[str, Any]:
        """
        Query the agent with function calling
        
        Args:
            user_input: User's question or request
            
        Returns:
            Agent response
        """
        try:
            # Start chat with the model
            chat = self.agent.start_chat()
            
            # Send message
            response = chat.send_message(user_input)
            
            # Handle function calls
            max_iterations = 5
            iteration = 0
            
            while response.candidates[0].content.parts[0].function_call and iteration < max_iterations:
                iteration += 1
                function_call = response.candidates[0].content.parts[0].function_call
                function_name = function_call.name
                function_args = dict(function_call.args)
                
                # Execute the function
                function_response = self.execute_function_call(function_name, function_args)
                
                # Send function response back to model
                from vertexai.generative_models import Part
                response = chat.send_message(
                    Part.from_function_response(
                        name=function_name,
                        response={"result": function_response}
                    )
                )
            
            # Get final text response
            final_answer = response.text
            
            return {"output": final_answer}
            
        except Exception as e:
            return {"output": f"Error during agent reasoning: {str(e)}"}
    
    def run_cli(self):
        """Run interactive CLI interface"""
        print("=" * 70)
        print("Oracle Database AI Agent (powered by Google ADK)")
        print("=" * 70)
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
        print("Type your questions about Oracle Database (or 'quit' to exit)")
    async def run_cli_async(self):
        """Run interactive CLI interface"""
        print("=" * 80)
        print("Oracle Database AI Agent with RAG")
        print("=" * 80)
        print(f"RAG API URL: {self.api_url}")
        print(f"Project: {self.project_id}")
        print(f"Region: {self.location}")
        print()
        
        # Check API status
        status = self.get_api_status()
        if "error" not in status:
            print(f"✓ Knowledge base: {status['document_count']} documents")
            print(f"✓ RAG API Status: {status['status']}")
        else:
            print(f"⚠️  Warning: {status.get('error', 'RAG API unavailable')}")
        
        print()
        print("🔧 Initializing agent...")
        
        try:
            self.agent = await self.create_agent_async()
            print("✓ Agent initialized successfully!")
            print()
            print("Available capabilities:")
            print("  - Search Oracle documentation (RAG)")
            print()
            print("Type your questions about Oracle Database (or 'quit' to exit)")
            print("-" * 80)
            print()
            
            # Interactive loop
            while True:
                try:
                    user_input = input("You: ").strip()
                    
                    if not user_input:
                        continue
                        
                    if user_input.lower() in ['quit', 'exit', 'q']:
                        print("\nGoodbye!")
                        break
                    
                    # Process query
                    response = self.query_sync(user_input)
                    print(f"\nAgent: {response['output']}\n")
                    
                except KeyboardInterrupt:
                    print("\n\nInterrupted. Goodbye!")
                    break
                except Exception as e:
                    print(f"\nError: {str(e)}\n")
                    import traceback
                    traceback.print_exc()
                    continue
                    
        except Exception as e:
            print(f"\n❌ Failed to initialize agent: {str(e)}")
            import traceback
            traceback.print_exc()

async def main():
    """Main entry point"""
    # Configuration
    api_url = os.getenv("ORACLE_RAG_API_URL", "http://localhost:8501")
    project_id = os.getenv("GCP_PROJECT_ID", "adb-pm-prod")
    location = os.getenv("GCP_REGION", "us-central1")
    sqlcl_path = os.getenv("SQLCL_PATH", "/opt/sqlcl/bin/sql")
    wallet_path = os.getenv("TNS_ADMIN", os.path.expanduser("~/wallet"))
    
    # Create and run agent
    agent = OracleRAGAgent(
        api_url=api_url,
        project_id=project_id,
        location=location,
        sqlcl_path=sqlcl_path,
        wallet_path=wallet_path
    )
    await agent.run_cli_async()

if __name__ == "__main__":
    asyncio.run(main())
