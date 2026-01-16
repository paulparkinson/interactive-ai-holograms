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
from google.adk.agents import LlmAgent
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.adk.artifacts.in_memory_artifact_service import InMemoryArtifactService
from google.adk.tools.mcp_tool.mcp_toolset import McpToolset, StdioConnectionParams, StdioServerParameters
from google.genai import types

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
        # If running on the same machine as the API, prefer localhost to avoid network issues
        if "34.48.146.146" in api_url:
            print(f"⚠️  Note: Replacing public IP in API URL with localhost for local connection reliability.")
            api_url = api_url.replace("34.48.146.146", "localhost")
            
        self.api_url = api_url.rstrip('/')
        self.project_id = project_id
        self.location = location
        self.sqlcl_path = sqlcl_path
        self.wallet_path = wallet_path or os.path.expanduser("~/wallet")
        
        # Session and artifact services for ADK
        self.session_service = InMemorySessionService()
        self.artifacts_service = InMemoryArtifactService()
        
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
        Create an ADK agent with Oracle RAG API and MCP database tools
        
        Returns:
            LlmAgent instance configured with both RAG and MCP tools
        """
        # Configure Oracle MCP server parameters
        oracle_mcp_params = StdioConnectionParams(
            server_params=StdioServerParameters(
                command=self.sqlcl_path,
                args=["-mcp"],
                env={"TNS_ADMIN": self.wallet_path}
            )
        )
        
        # Define agent instructions
        instructions = """You are a helpful assistant specializing in Oracle Database.

You have access to two complementary tools:

1. **Oracle RAG Knowledge Base (query_oracle_rag_kb)**: Use this to search documentation and find 
   information about Oracle Database features, capabilities, spatial functions, vector search, 
   and other conceptual/documentation topics.

2. **Oracle MCP Database Server**: Use these tools for direct database operations like:
   - Running SQL queries (list-connections, connect, run-sql, run-sqlcl)
   - Checking database schema (schema-information)
   - Viewing connection status and database metadata
   - Executing database commands

**When to use each tool:**
- For "what is" or "how to" questions → use query_oracle_rag_kb
- For "show me data", "list tables", "query database" → use MCP database tools
- The MCP server connection is: paulparkdb_mcp
- Always connect to paulparkdb_mcp before running database queries

Be concise, helpful, and technically accurate. Combine information from both sources when appropriate."""
        
        # Create ADK agent with MCP toolset
        agent = LlmAgent(
            model="gemini-2.0-flash-exp",
            name="oracle_ai_assistant",
            instruction=instructions,
            tools=[
                McpToolset(connection_params=oracle_mcp_params),
                self._create_rag_tool()
            ]
        )
        
        return agent
    
    def _create_rag_tool(self):
        """Create custom RAG tool as a Python function for ADK"""
        def query_oracle_rag_kb(query: str, top_k: int = 5) -> str:
            """
            Search the Oracle Database knowledge base for documentation and feature information.
            
            Args:
                query: The question or search query about Oracle Database
                top_k: Number of relevant document chunks to retrieve (1-20)
            
            Returns:
                Answer with context from documentation
            """
            result = self.query_oracle_rag(query, top_k)
            if "error" in result:
                return f"Error accessing knowledge base: {result['answer']}"
            
            answer = result['answer']
            chunks = len(result.get('context_chunks', []))
            time_taken = result.get('total_time', 0)
            
            return f"{answer}\n\n[Source: Retrieved from {chunks} documentation chunks in {time_taken:.2f}s]"
        
        return query_oracle_rag_kb
    
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
        """Run interactive CLI interface with ADK agent"""
        print("=" * 80)
        print("Oracle Database AI Agent with MCP (powered by Google ADK)")
        print("=" * 80)
        print(f"RAG API URL: {self.api_url}")
        print(f"SQLcl Path: {self.sqlcl_path}")
        print(f"Wallet Path: {self.wallet_path}")
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
        print("🔧 Initializing ADK agent with MCP tools...")
        
        try:
            agent = await self.create_agent_async()
            print("✓ Agent initialized successfully!")
            print()
            print("Available capabilities:")
            print("  - Search Oracle documentation (RAG)")
            print("  - Query database directly (MCP: paulparkdb_mcp)")
            print("  - List connections, run SQL, check schema")
            print()
            print("Type your questions about Oracle Database (or 'quit' to exit)")
            print("-" * 80)
            print()
            
            # Create session
            session = await self.session_service.create_session(
                state={},
                app_name="oracle_ai_agent",
                user_id="cli_user"
            )
            
            # Use 'id' or 'session_id' depending on ADK version
            session_id = getattr(session, "session_id", getattr(session, "id", None))
            if not session_id:
                # Fallback for debug
                print(f"Warning: Could not determine session ID from session object: {dir(session)}")
                session_id = str(session) # Last resort
            
            # Create runner
            runner = Runner(
                agent=agent,
                app_name="oracle_ai_agent",
                session_service=self.session_service,
                artifact_service=self.artifacts_service
            )
            
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
                    response = await runner.run_async(
                        session_id=session_id,
                        new_message=user_input
                    )
                    
                    print(f"\nAgent: {response.output}\n")
                    
                except KeyboardInterrupt:
                    print("\n\nInterrupted. Goodbye!")
                    break
                except Exception as e:
                    print(f"\nError: {str(e)}\n")
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
