"""
Oracle AI Database ADK Agent - RAG Only (No MCP)
Uses Google Agent Development Kit (ADK) with LlmAgent for vector RAG queries only.

This version:
- Uses modern ADK (google.adk.agents.LlmAgent)
- Only does documentation search (no database/MCP)
- Should work reliably (no MCP schema bugs)
"""
import os
import asyncio
import requests
from dotenv import load_dotenv
import vertexai
from google.adk.agents import LlmAgent
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.adk.artifacts.in_memory_artifact_service import InMemoryArtifactService
from google.adk.tools import BaseTool
from google.genai import types

# Load environment variables
load_dotenv()


class OracleRAGTool(BaseTool):
    """Custom ADK tool for querying Oracle RAG API"""
    
    def __init__(self, api_url: str):
        """Initialize the RAG tool
        
        Args:
            api_url: Base URL of the Oracle RAG API
        """
        super().__init__(
            name="query_oracle_database",
            description="Search the Oracle Database knowledge base for information about Oracle Database features, spatial capabilities, vector search, JSON features, SQL enhancements, and other database topics. Use this for technical questions that require specific documentation or feature details."
        )
        self.api_url = api_url.rstrip('/')
    
    async def execute(self, query: str, top_k: int = 5) -> str:
        """Execute the RAG query
        
        Args:
            query: The question to ask
            top_k: Number of similar chunks to retrieve
            
        Returns:
            String with the answer
        """
        try:
            print(f"  → Querying RAG API: {query[:60]}...")
            
            response = requests.post(
                f"{self.api_url}/query",
                json={"query": query, "top_k": top_k},
                timeout=120
            )
            
            if response.status_code != 200:
                error_detail = response.text
                return f"API error ({response.status_code}): {error_detail[:200]}"
            
            response.raise_for_status()
            result = response.json()
            
            if "error" in result:
                return f"Error: {result['answer']}"
            
            answer = f"{result['answer']}\n\n"
            answer += f"[Source: {len(result.get('context_chunks', []))} document chunks, "
            answer += f"processed in {result.get('total_time', 0):.2f}s]"
            return answer
            
        except requests.exceptions.Timeout:
            return "Knowledge base query timed out. Please try again."
        except requests.exceptions.ConnectionError:
            return f"Cannot connect to the knowledge base API at {self.api_url}."
        except Exception as e:
            return f"Failed to query the knowledge base: {str(e)}"


class OracleADKRAGAgent:
    """ADK Agent for Oracle Database documentation search (RAG only, no MCP)"""
    
    def __init__(self, api_url: str, project_id: str, location: str):
        """
        Initialize the ADK RAG Agent
        
        Args:
            api_url: Base URL of the Oracle RAG API
            project_id: GCP project ID
            location: GCP region
        """
        self.api_url = api_url.rstrip('/')
        self.project_id = project_id
        self.location = location
        self.agent = None
        self.runner = None
        self.session_service = InMemorySessionService()
        self.artifacts_service = InMemoryArtifactService()
        self.session = None
        
    def get_api_status(self) -> dict:
        """Get the status of the Oracle RAG API"""
        try:
            response = requests.get(f"{self.api_url}/status", timeout=30)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            return {"error": str(e), "status": "unavailable"}
    
    async def create_agent_async(self):
        """Create ADK agent with Oracle RAG tool"""
        print("  → Initializing ADK session service...")
        
        # Initialize Vertex AI globally
        vertexai.init(project=self.project_id, location=self.location)
        
        # Create session
        self.session = await self.session_service.create_session(
            app_name="Oracle AI Database RAG Agent",
            user_id="user_123",
            state={}
        )
        
        # Create custom RAG tool
        rag_tool = OracleRAGTool(self.api_url)
        
        # Define agent instruction
        instruction = """You are an expert Oracle Database AI assistant with access to comprehensive documentation.

**Your Capability:**
Use the `query_oracle_database` tool to search Oracle Database documentation for information about:
- Database features and functionality
- SQL syntax, commands, and best practices
- Vector search, spatial data, JSON capabilities
- Performance optimization and configuration
- New features in recent Oracle versions
- Technical implementation details

**When to Use the Tool:**
Use `query_oracle_database` when users ask about:
- Specific Oracle Database features ("What is vector search?")
- How to do something in Oracle ("How do I create a JSON table?")
- SQL syntax or commands
- Database administration or configuration
- Best practices and optimization

**Response Style:**
- Be concise but thorough
- Cite specific features or capabilities when possible
- If the documentation doesn't have the answer, say so clearly
- Format technical content with examples when helpful

Be helpful and technically accurate."""
        
        prVertex AI initialized globally, just pass model name
        self.agent = LlmAgent(
            model="gemini-2.0-flash-exp",
            name="oracle_rag_assistant",
            instruction=instruction,
            tools=[rag_tool]
            vertexai=True,
            project=self.project_id,
            location=self.location
        )
        
        print("  ✓ Agent created with RAG tool")
        
        # Create runner
        self.runner = Runner(
            app_name="Oracle AI Database RAG Agent",
            agent=self.agent,
            artifact_service=self.artifacts_service,
            session_service=self.session_service
        )
        
        print("  ✓ Runner initialized")
        return self.agent
    
    async def query_async(self, user_input: str) -> str:
        """
        Query the agent asynchronously using ADK Runner
        
        Args:
            user_input: User's question
            
        Returns:
            Agent response as string
        """
        if not self.runner or not self.session:
            raise RuntimeError("Agent not initialized. Call create_agent_async() first.")
        
        try:
            # Create message content
            content = types.Content(
                role="user",
                parts=[types.Part(text=user_input)]
            )
            
            # Run agent with message
            events = self.runner.run_async(
                session_id=self.session.id,
                user_id="user_123",
                new_message=content
            )
            
            # Collect response parts
            response_parts = []
            async for event in events:
                if event.content.role == "model" and event.content.parts:
                    for part in event.content.parts:
                        if part.text:
                            response_parts.append(part.text)
            
            return "\n".join(response_parts) if response_parts else "No response generated"
            
        except Exception as e:
            import traceback
            traceback.print_exc()
            return f"Error during agent reasoning: {str(e)}"
    
    async def cleanup_async(self):
        """Cleanup resources"""
        if self.runner:
            await self.runner.close()
    
    async def run_cli_async(self):
        """Run interactive CLI interface with ADK"""
        print("=" * 80)
        print("Oracle Database ADK RAG Agent (Documentation Search)")
        print("=" * 80)
        print(f"API URL: {self.api_url}")
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
        print("🔧 Initializing ADK agent with RAG tool...")
        
        try:
            await self.create_agent_async()
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
                    
                    # Process query using ADK runner
                    response = await self.query_async(user_input)
                    print(f"\nAgent: {response}\n")
                    
                except KeyboardInterrupt:
                    print("\n\nInterrupted. Goodbye!")
                    break
                except Exception as e:
                    print(f"\nError: {str(e)}\n")
                    import traceback
                    traceback.print_exc()
                    continue
            
            # Cleanup
            await self.cleanup_async()
                    
        except Exception as e:
            print(f"\n❌ Failed to initialize agent: {str(e)}")
            import traceback
            traceback.print_exc()
            await self.cleanup_async()


async def main():
    """Main entry point"""
    # Configuration
    api_url = os.getenv("ORACLE_RAG_API_URL", "http://localhost:8501")
    project_id = os.getenv("GCP_PROJECT_ID", "adb-pm-prod")
    location = os.getenv("GCP_REGION", "us-central1")
    
    print("Starting Oracle ADK RAG Agent...\n")
    
    # Create and run agent
    agent = OracleADKRAGAgent(api_url, project_id, location)
    await agent.run_cli_async()


if __name__ == "__main__":
    asyncio.run(main())
