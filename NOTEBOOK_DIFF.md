# Notebook Comparison: database-rag-updated.ipynb vs database-rag-updated-v2.ipynb

## Differences Between Original and v2

### **Overall Structure**
- **Original**: 34 cells | **v2**: 34 cells
- **Original**: OCI-focused RAG | **v2**: Vertex AI-focused RAG

---

## **1. Installation & Setup** ⭐ NEW IN V2

**v2 Added (Cells 1-3):**
```markdown
# Workflow with explicit kernel restart instructions
⚠️ Important workflow note
1. Run the Install/Setup cell
2. Restart kernel when it finishes
3. Run install cell again if packages changed
4. Restart kernel one more time
5. Then run rest of notebook
```

**v2 Install Cell:**
```python
# Installs LangChain 1.x packages
pip install -U langchain langchain-core langchain-community 
  langchain-text-splitters langchain-huggingface langchain-google-vertexai
```

**Original**: No install cell, assumes pre-installed packages

---

## **2. Core Imports - Major Differences**

| Component | Original | v2 |
|-----------|----------|-----|
| **LLM Provider** | `from langchain_community.llms import OCIGenAI`<br>`from langchain_community.chat_models.oci_generative_ai import ChatOCIGenAI` | `from langchain_google_vertexai import ChatVertexAI, VertexAI, VertexAIEmbeddings` |
| **Cloud SDK** | `import oci` | `from google.cloud import aiplatform` |
| **Embeddings** | `from langchain_huggingface import HuggingFaceEmbeddings`<br>(sentence-transformers/all-MiniLM-L6-v2) | `from langchain_google_vertexai import VertexAIEmbeddings`<br>(text-embedding-004) |
| **LangChain Version** | Community packages (older) | Core + Google Vertex (1.x) |
| **Extra Imports** | `from PyPDF2 import PdfReader`<br>`from dotenv import load_dotenv` | `from PyPDF2 import PdfReader`<br>(no dotenv) |

---

## **3. Database Connection**

**Both use same pattern** but v2 has cleaner path:

**Original:**
```python
connection = oracledb.connect(
    config_dir = '../wallet',      # Relative path
    wallet_location = '../wallet'
)
```

**v2:**
```python
connection = oracledb.connect(
    config_dir='~/wallet',         # Home directory path
    wallet_location='~/wallet'
)
```

---

## **4. Embedding & Vector Storage**

**Original:**
```python
model_4db = HuggingFaceEmbeddings(
    model_name="sentence-transformers/all-MiniLM-L6-v2"
)

knowledge_base = OracleVS.from_documents(
    docs, model_4db, client=connection, 
    table_name="RAG_TAB",
    distance_strategy=DistanceStrategy.DOT_PRODUCT
)
```

**v2:**
```python
embeddings = VertexAIEmbeddings(
    model_name="text-embedding-004"
)

knowledge_base = OracleVS.from_documents(
    docs, embeddings, client=connection,
    table_name="RAG_TAB", 
    distance_strategy=DistanceStrategy.DOT_PRODUCT
)
```

---

## **5. LLM Configuration**

**Original - OCI GenAI:**
```python
from langchain.chat_models import ChatVertexAI  # Actually uses OCI

llm = VertexAI(
    model_name="gemini-1.5-flash-002",
    max_output_tokens=8192,
    temperature=1,
    top_p=0.8,
    top_k=40
)
```

**v2 - Vertex AI:**
```python
from langchain_google_vertexai import ChatVertexAI

llm = ChatVertexAI(
    model_name="gemini-2.0-flash-exp",
    max_output_tokens=8192,
    temperature=0.7,
    top_p=0.95,
    top_k=40,
    verbose=True
)
```

**Key Change**: v2 uses **Gemini 2.0 Flash** (newer model)

---

## **6. Vertex AI Initialization**

**Original:**
```python
PROJECT_ID = "proud-research-451713-i5"
REGION = "us-east4"
vertexai.init(project=PROJECT_ID, location=REGION)
```

**v2:**
```python
PROJECT_ID = "your-project-id"  # Placeholder
REGION = "us-east4"
vertexai.init(project=PROJECT_ID, location=REGION)
```

---

## **7. Prompt Template & Chain**

**Both identical structure**, using LangChain LCEL:
```python
chain = (
    {"context": retriever, "question": RunnablePassthrough()}
    | prompt
    | llm
    | StrOutputParser()
)
```

---

## **8. Documentation & Clarity**

**Original:**
- Numbered steps: "1. Run the RAG...", "2. This next code..."
- Descriptive paragraph format

**v2:**
- Markdown headers: "## Step 1:", "## Step 2:"
- Cleaner, more scannable structure
- Better formatted with emojis (🎉, ✓)

---

## Summary of Key Changes

| Feature | Original | v2 |
|---------|----------|-----|
| **Cloud Platform** | OCI (Oracle Cloud) | GCP (Google Cloud) |
| **LLM** | Gemini 1.5 Flash via OCI | Gemini 2.0 Flash via Vertex AI |
| **Embeddings** | HuggingFace (local) | Vertex AI (cloud) |
| **LangChain Version** | Community packages | LangChain 1.x with Google |
| **Setup** | No install cell | Explicit install + restart |
| **Wallet Path** | `../wallet` | `~/wallet` |
| **Documentation** | Paragraph style | Header-based steps |
| **Cell Count** | 34 | 34 |

**Bottom line:** v2 is a complete rewrite for **Google Cloud Platform + Vertex AI** while maintaining the same Oracle 23ai vector database backend.
