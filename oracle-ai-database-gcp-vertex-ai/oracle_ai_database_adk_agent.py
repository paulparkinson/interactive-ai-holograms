"""
Oracle AI Database ADK Agent with MCP Integration
Google Agent Development Kit (ADK) agent that uses:
- Oracle RAG API for vector search
- Oracle MCP Server for direct database queries
"""
import os
import asyncio
import requests
import json
import subprocess
from dotenv import load_dotenv
import vertexai
from vertexai.generative_models import GenerativeModel, Tool, FunctionDeclaration, Part
from typing import Dict, Any, List, Optional

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
        self.mcp_process = None
        self.mcp_tools = []
        self.next_mcp_id = 1
        
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
    
    async def start_mcp_server(self):
        """Start the SQLcl MCP server"""
        try:
            env = os.environ.copy()
            env["TNS_ADMIN"] = self.wallet_path
            
            self.mcp_process = await asyncio.create_subprocess_exec(
                self.sqlcl_path, "-mcp",
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env=env
            )
            
            # Wait a bit for server to start
            await asyncio.sleep(2)
            
            # Initialize MCP session
            init_request = {
                "jsonrpc": "2.0",
                "id": self.next_mcp_id,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {},
                    "clientInfo": {
                        "name": "oracle_ai_agent",
                        "version": "1.0.0"
                    }
                }
            }
            self.next_mcp_id += 1
            
            # Send initialize request
            self.mcp_process.stdin.write((json.dumps(init_request) + "\n").encode())
            await self.mcp_process.stdin.drain()
            
            # Read response with timeout
            try:
                response_line = await asyncio.wait_for(
                    self.mcp_process.stdout.readline(),
                    timeout=5.0
                )
            except asyncio.TimeoutError:
                print("  ⚠️  MCP server initialization timed out")
                return False
            
            # List available tools
            tools_request = {
                "jsonrpc": "2.0",
                "id": self.next_mcp_id,
                "method": "tools/list",
                "params": {}
            }
            self.next_mcp_id += 1
            
            self.mcp_process.stdin.write((json.dumps(tools_request) + "\n").encode())
            await self.mcp_process.stdin.drain()
            
            # Read tools response with timeout
            try:
                tools_response = await asyncio.wait_for(
                    self.mcp_process.stdout.readline(),
                    timeout=5.0
                )
                tools_data = json.loads(tools_response.decode())
                
                if "result" in tools_data and "tools" in tools_data["result"]:
                    self.mcp_tools = tools_data["result"]["tools"]
                    return True
            except asyncio.TimeoutError:
                print("  ⚠️  MCP tools list request timed out")
                return False
            
            return False
            
        except Exception as e:
            print(f"  ⚠️  Failed to start MCP server: {str(e)}")
            return False
    
    async def call_mcp_tool(self, tool_name: str, arguments: dict) -> str:
        """Call an MCP tool"""
        try:
            request = {
                "jsonrpc": "2.0",
                "id": self.next_mcp_id,
                "method": "tools/call",
                "params": {
                    "name": tool_name,
                    "arguments": arguments
                }
            }
            self.next_mcp_id += 1
            
            self.mcp_process.stdin.write((json.dumps(request) + "\n").encode())
            await self.mcp_process.stdin.drain()
            
            # Read response
            response_line = await self.mcp_process.stdout.readline()
            response_data = json.loads(response_line.decode())
            
            if "result" in response_data:
                return json.dumps(response_data["result"])
            elif "error" in response_data:
                return f"Error: {response_data['error']}"
            
            return "No response from MCP tool"
            
        except Exception as e:
            return f"Error calling MCP tool: {str(e)}"
    
    def stop_mcp_server(self):
        """Stop the MCP server"""
        if self.mcp_process:
            self.mcp_process.terminate()
    
    async def create_agent_async(self):
        """
        Create an agent with Oracle RAG API and MCP database tools
        
        Returns:
            GenerativeModel instance configured with tools
        """
        # Initialize Vertex AI
        vertexai.init(project=self.project_id, location=self.location)
        
        # Start MCP server and get tools
        print("  → Starting MCP server...")
        mcp_started = await self.start_mcp_server()
        
        function_declarations = []
        
        # Add RAG function
        function_declarations.append(FunctionDeclaration(
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
                        "description": "Number of relevant document chunks to retrieve (1-20).",
                        "default": 5
                    }
                },
                "required": ["query"]
            }
        ))
        
        # Add MCP tools if server started successfully
        if mcp_started and self.mcp_tools:
            print(f"  → Found {len(self.mcp_tools)} MCP tools")
            for mcp_tool in self.mcp_tools[:5]:  # Limit to first 5 tools to avoid overwhelming the model
                # Convert MCP tool schema to Gemini function declaration
                tool_name = mcp_tool.get("name", "")
                tool_desc = mcp_tool.get("description", "")
                tool_schema = mcp_tool.get("inputSchema", {})
                
                # Simplify schema for Gemini
                properties = {}
                required = []
                
                if "properties" in tool_schema:
                    for prop_name, prop_def in tool_schema["properties"].items():
                        properties[prop_name] = {
                            "type": prop_def.get("type", "string"),
                            "description": prop_def.get("description", "")
                        }
                
                if "required" in tool_schema:
                    required = tool_schema["required"]
                
                function_declarations.append(FunctionDeclaration(
                    name=f"mcp_{tool_name}",
                    description=f"[MCP Tool] {tool_desc}",
                    parameters={
                        "type": "object",
                        "properties": properties,
                        "required": required
                    }
                ))
        
        # Create tool with all function declarations
        oracle_tool = Tool(function_declarations=function_declarations)
        
        # Enhanced system instructions
        instructions = """You are an expert Oracle Database AI assistant with access to:

1. **Knowledge Base (query_oracle_database)**: Search documentation about Oracle features
2. **MCP Database Tools (mcp_*)**: Direct database operations (list connections, run SQL, check schema)

**When to use each:**
- For "what is" or "how to" questions → use query_oracle_database  
- For "show me data", "list tables", "query database" → use mcp_* tools
- For database operations, use mcp_list-connections first, then mcp_connect, then other operations

Be concise, helpful, and technically accurate."""
        
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
        
        # Check if it's an MCP tool
        if function_name.startswith("mcp_"):
            actual_tool_name = function_name[4:]  # Remove "mcp_" prefix
            # Need to call async function, so return a placeholder
            # This will be handled in the async version
            loop = asyncio.get_event_loop()
            return loop.run_until_complete(self.call_mcp_tool(actual_tool_name, args))
        
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
            import traceback
            traceback.print_exc()
            return {"output": f"Error during agent reasoning: {str(e)}"}
    
    def cleanup(self):
        """Cleanup resources"""
        self.stop_mcp_server()
    
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
        print("Oracle Database AI Agent with RAG + MCP")
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
        print("🔧 Initializing agent with MCP tools...")
        
        try:
            self.agent = await self.create_agent_async()
            print("✓ Agent initialized successfully!")
            print()
            print("Available capabilities:")
            print("  - Search Oracle documentation (RAG)")
            if self.mcp_tools:
                print(f"  - {len(self.mcp_tools)} MCP database tools (list-connections, connect, run-sql, etc.)")
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
            
            # Cleanup
            self.cleanup()
                    
        except Exception as e:
            print(f"\n❌ Failed to initialize agent: {str(e)}")
            import traceback
            traceback.print_exc()
            self.cleanup()

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
