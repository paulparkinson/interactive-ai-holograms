"""
Oracle AI Database ADK Agent - Direct RAG Implementation
Uses Google Agent Development Kit (ADK) with direct Oracle Vector Store integration.

This version:
- Connects directly to Oracle Database with vector storage
- Implements RAG using langchain + Vertex AI embeddings
- Uses ADK LlmAgent with custom RAG tool
- Same functionality as oracle_ai_database_gemini_rag.ipynb notebook
"""
import os
import asyncio
from dotenv import load_dotenv
import vertexai
import oracledb
from google.adk.agents import LlmAgent
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.adk.artifacts.in_memory_artifact_service import InMemoryArtifactService
from google.adk.tools import BaseTool
from google.genai import types
from langchain_google_genai import GoogleGenerativeAIEmbeddings
from langchain_community.vectorstores.oraclevs import OracleVS
from langchain_community.vectorstores.utils import DistanceStrategy

# Load environment variables
load_dotenv()


class OracleRAGTool(BaseTool):
    """Custom ADK tool for querying Oracle Vector Store directly"""
    
    def __init__(self, knowledge_base: OracleVS):
        """Initialize the RAG tool
        
        Args:
            knowledge_base: Oracle Vector Store instance
        """
        super().__init__(
            name="query_oracle_database",
            description="Search the Oracle Database knowledge base for information about Oracle Database features, spatial capabilities, vector search, JSON features, SQL enhancements, and other database topics. Use this for technical questions that require specific documentation or feature details."
        )
        self.knowledge_base = knowledge_base
    
    async def execute(self, query: str, top_k: int = 5) -> str:
        """Execute the RAG query
        
        Args:
            query: The question to ask
            top_k: Number of similar chunks to retrieve
            
        Returns:
            String with relevant context from documentation
        """
        try:
            print(f"  → Searching vector store: {query[:60]}...")
            
            # Perform similarity search
            result_chunks = self.knowledge_base.similarity_search(query, k=top_k)
            
            if not result_chunks:
                return "No relevant documentation found for this query."
            
            # Format context from chunks
            context_parts = []
            for i, chunk in enumerate(result_chunks, 1):
                context_parts.append(f"[{i}] {chunk.page_content}")
            
            context = "\n\n".join(context_parts)
            return f"Found {len(result_chunks)} relevant documentation sections:\n\n{context}"
            
        except Exception as e:
            import traceback
            traceback.print_exc()
            return f"Failed to query the knowledge base: {str(e)}"


class OracleADKRAGAgent:
    """ADK Agent for Oracle Database documentation search with direct vector store"""
    
    def __init__(self, project_id: str, location: str):
        """
        Initialize the ADK RAG Agent
        
        Args:
            project_id: GCP project ID
            location: GCP region
        """
        self.project_id = project_id
        self.location = location
        self.agent = None
        self.runner = None
        self.session_service = InMemorySessionService()
        self.artifacts_service = InMemoryArtifactService()
        self.session = None
        self.connection = None
        self.knowledge_base = None
        
    def connect_database(self):
        """Connect to Oracle Database and initialize vector store"""
        print("  → Connecting to Oracle Database...")
        
        # Load credentials from environment
        un = os.getenv("DB_USERNAME")
        pw = os.getenv("DB_PASSWORD")
        dsn = os.getenv("DB_DSN")
        wallet_path = os.getenv("DB_WALLET_DIR")
        wpwd = os.getenv("DB_WALLET_PASSWORD", "")
        
        if not all([un, pw, dsn, wallet_path]):
            raise ValueError("Missing database credentials in .env file")
        
        # Connect to database
        self.connection = oracledb.connect(
            config_dir=wallet_path,
            user=un,
            password=pw,
            dsn=dsn,
            wallet_location=wallet_path,
            wallet_password=wpwd
        )
        
        print(f"  ✓ Connected to {dsn}")
        
        # Check how many documents are in the store
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) FROM RAG_TAB")
            count = cursor.fetchone()[0]
            print(f"  ✓ Found {count} document chunks in RAG_TAB")
        
        # Initialize Vertex AI embeddings with retry logic
        print("  → Initializing Vertex AI embeddings...")
        try:
            embeddings = GoogleGenerativeAIEmbeddings(model="text-embedding-004")
            
            # Connect to existing vector store
            print("  → Connecting to vector store RAG_TAB...")
            self.knowledge_base = OracleVS(
                client=self.connection,
                embedding_function=embeddings,
                table_name="RAG_TAB",
                distance_strategy=DistanceStrategy.DOT_PRODUCT
            )
            
            print(f"  ✓ Vector store ready")
            
        except Exception as e:
            print(f"  ⚠️  Error initializing vector store: {str(e)}")
            print("  → Please ensure you've run: gcloud auth application-default login")
            raise
        
        return self.knowledge_base
    
    async def create_agent_async(self):
        """Create ADK agent with Oracle RAG tool"""
        print("  → Initializing ADK session service...")
        
        # Initialize Vertex AI globally
        vertexai.init(project=self.project_id, location=self.location)
        
        # Set environment variables for ADK/GenAI SDK to use Vertex AI
        os.environ['GOOGLE_CLOUD_PROJECT'] = self.project_id
        os.environ['GOOGLE_CLOUD_LOCATION'] = self.location
        os.environ['GOOGLE_GENAI_USE_VERTEXAI'] = 'true'
        
        # Connect to database and initialize vector store
        self.connect_database()
        
        # Create session
        self.session = await self.session_service.create_session(
            app_name="Oracle AI Database RAG Agent",
            user_id="user_123",
            state={}
        )
        
        # Create custom RAG tool with vector store
        rag_tool = OracleRAGTool(self.knowledge_base)
        
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
        
        print("  → Creating ADK LlmAgent with RAG tool...")
        
        # Create agent with RAG tool
        # Using gemini-2.0-flash-001 (stable version, not experimental)
        # This has better rate limits than -exp variants
        from google.genai.types import GenerateContentConfig
        
        self.agent = LlmAgent(
            model="gemini-2.0-flash-001",
            name="oracle_rag_assistant",
            instruction=instruction,
            tools=[rag_tool],
            generate_content_config=GenerateContentConfig(
                temperature=0.2,
                max_output_tokens=2048,
            )
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
        if self.connection:
            self.connection.close()
            print("  ✓ Database connection closed")
    
    async def run_cli_async(self):
        """Run interactive CLI interface with ADK"""
        print("=" * 80)
        print("Oracle Database ADK RAG Agent (Direct Vector Store)")
        print("=" * 80)
        print(f"Project: {self.project_id}")
        print(f"Region: {self.location}")
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
    # Configuration from environment
    project_id = os.getenv("GCP_PROJECT_ID", "adb-pm-prod")
    location = os.getenv("GCP_REGION", "us-central1")
    
    print("Starting Oracle ADK RAG Agent...\n")
    
    # Create and run agent
    agent = OracleADKRAGAgent(project_id, location)
    await agent.run_cli_async()


if __name__ == "__main__":
    asyncio.run(main())
