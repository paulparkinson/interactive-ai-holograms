import io
import os
import json
import uuid
from typing import Optional, List

from fastapi import FastAPI, UploadFile, File, Form
import numpy as np
from PIL import Image

try:
    import oracledb
except Exception:
    oracledb = None

try:
    import onnxruntime as ort
except Exception:
    ort = None

try:
    from transformers import AutoTokenizer
except Exception:
    AutoTokenizer = None

try:
    from openai import OpenAI
except Exception:
    OpenAI = None

from tika import parser

app = FastAPI()

MODEL_PATH_TEXT = os.environ.get("MODEL_PATH_TEXT", "/models/text_model.onnx")
MODEL_PATH_IMAGE = os.environ.get("MODEL_PATH_IMAGE", "/models/image_model.onnx")
OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "")
OPENAI_MODEL = os.environ.get("OPENAI_MODEL", "gpt-4o-mini")

# Vector mode: "oracle_native", "oracle_hybrid", or "python"
# - oracle_native: Use VECTOR_EMBEDDING() for database-side embeddings (requires ONNX model import)
# - oracle_hybrid: Python ONNX embeddings + Oracle TO_VECTOR() storage + VECTOR_DISTANCE() search (default)
# - python: Python ONNX + JSON storage + NumPy cosine similarity (Oracle 19c+ compatible)
VECTOR_MODE = os.environ.get("VECTOR_MODE", "oracle_hybrid")
if VECTOR_MODE not in ["oracle_native", "oracle_hybrid", "python"]:
    print(f"Warning: Invalid VECTOR_MODE '{VECTOR_MODE}', defaulting to 'oracle_hybrid'")
    VECTOR_MODE = "oracle_hybrid"

# Legacy support: convert old USE_ORACLE_VECTOR_SEARCH boolean to new mode
if "USE_ORACLE_VECTOR_SEARCH" in os.environ and "VECTOR_MODE" not in os.environ:
    use_oracle = os.environ.get("USE_ORACLE_VECTOR_SEARCH", "true").lower() == "true"
    VECTOR_MODE = "oracle_hybrid" if use_oracle else "python"
    print(f"Converted USE_ORACLE_VECTOR_SEARCH={use_oracle} to VECTOR_MODE={VECTOR_MODE}")

# For backward compatibility
USE_ORACLE_VECTOR_SEARCH = VECTOR_MODE in ["oracle_native", "oracle_hybrid"]

# Default database connection (from docker-compose)
ORACLE_HOST = os.environ.get("ORACLE_HOST", "oracle")
ORACLE_PORT = int(os.environ.get("ORACLE_PORT", "1521"))
ORACLE_SERVICE = os.environ.get("ORACLE_SERVICE", "FREEPDB1")
ORACLE_USER = os.environ.get("ORACLE_USER", "PDBADMIN")
ORACLE_PWD = os.environ.get("ORACLE_PWD", "oracle")
ORACLE_WALLET_PASSWORD = os.environ.get("ORACLE_WALLET_PASSWORD", "")

# Current active database connection
current_db_config = {
    "name": "default",
    "host": ORACLE_HOST,
    "port": ORACLE_PORT,
    "service": ORACLE_SERVICE,
    "user": ORACLE_USER,
    "pwd": ORACLE_PWD
}

def load_database_configs():
    """Load all database configurations from environment variables.
    Format: DB_<NAME>_HOST, DB_<NAME>_PORT, DB_<NAME>_SERVICE, DB_<NAME>_USER, DB_<NAME>_PWD
    For wallet connections: DB_<NAME>_WALLET_DIR, DB_<NAME>_WALLET_SERVICE, DB_<NAME>_USER, DB_<NAME>_PWD
    """
    configs = {
        "default": {
            "name": "default",
            "host": ORACLE_HOST,
            "port": ORACLE_PORT,
            "service": ORACLE_SERVICE,
            "user": ORACLE_USER,
            "pwd": ORACLE_PWD,
            "wallet_dir": None,
            "wallet_service": None
        }
    }
    
    # Find all DB_*_USER entries (both wallet and non-wallet connections)
    db_names = set()
    for key in os.environ.keys():
        if key.startswith("DB_") and key.endswith("_USER"):
            # Extract name: DB_PROD_USER -> PROD
            name = key[3:-5]
            db_names.add(name)
    
    # Load each database config
    for name in db_names:
        user = os.environ.get(f"DB_{name}_USER")
        pwd = os.environ.get(f"DB_{name}_PWD")
        
        # Check if this is a wallet connection
        wallet_dir = os.environ.get(f"DB_{name}_WALLET_DIR")
        wallet_service = os.environ.get(f"DB_{name}_WALLET_SERVICE")
        
        if wallet_dir and wallet_service and user and pwd:
            # Wallet-based connection
            configs[name.lower()] = {
                "name": name.lower(),
                "host": None,
                "port": None,
                "service": wallet_service,
                "user": user,
                "pwd": pwd,
                "wallet_dir": wallet_dir,
                "wallet_service": wallet_service
            }
        else:
            # Regular host-based connection
            host = os.environ.get(f"DB_{name}_HOST")
            port = int(os.environ.get(f"DB_{name}_PORT", "1521"))
            service = os.environ.get(f"DB_{name}_SERVICE")
            
            if host and service and user and pwd:
                configs[name.lower()] = {
                    "name": name.lower(),
                    "host": host,
                    "port": port,
                    "service": service,
                    "user": user,
                    "pwd": pwd,
                    "wallet_dir": None,
                    "wallet_service": None
                }
    
    return configs

sess_text = None
sess_image = None
tokenizer = None

if ort:
    if os.path.exists(MODEL_PATH_TEXT):
        try:
            sess_text = ort.InferenceSession(MODEL_PATH_TEXT)
            print("ONNX text model loaded")
            # Try to load tokenizer for sentence-transformers models
            if AutoTokenizer:
                try:
                    tokenizer = AutoTokenizer.from_pretrained("sentence-transformers/all-MiniLM-L6-v2")
                    print("Tokenizer loaded")
                except Exception as e:
                    print("Tokenizer loading failed:", e)
        except Exception as e:
            print("Failed loading ONNX text model:", e)
            sess_text = None
    else:
        print("ONNX text model not found; falling back to simple embeddings for text")
    if os.path.exists(MODEL_PATH_IMAGE):
        try:
            sess_image = ort.InferenceSession(MODEL_PATH_IMAGE)
            print("ONNX image model loaded")
        except Exception as e:
            print("Failed loading ONNX image model:", e)
            sess_image = None
    else:
        print("ONNX image model not found; falling back to simple embeddings for images")
else:
    print("ONNX runtime not available; falling back to simple embeddings for all inputs")


def simple_hash_embedding(text: str, dim: int = 512):
    h = np.frombuffer(text.encode('utf-8', errors='ignore'), dtype=np.uint8).astype(np.float32)
    if h.size == 0:
        return np.zeros(dim, dtype=np.float32).tolist()
    v = np.resize(h, dim)
    v = (v % 256) / 255.0
    return v.tolist()


def preprocess_image_for_clip(image_bytes: bytes, target_size: int = 224):
    """Preprocess image bytes to match common CLIP-like ONNX inputs.

    Steps:
    - Open image with PIL and convert to RGB
    - Resize shorter side to 256, then center-crop to target_size x target_size
    - Convert to float32, scale to [0,1]
    - Normalize with CLIP mean/std
    - Transpose to (C, H, W) and add batch dim
    Returns `np.ndarray` shape (1,3,target_size,target_size) dtype float32
    """
    try:
        img = Image.open(io.BytesIO(image_bytes)).convert('RGB')
        # resize shorter side to 256, then center crop to 224
        short, long = min(img.size), max(img.size)
        # use thumbnail-like resize while preserving aspect ratio
        resize_size = 256
        img = img.resize((resize_size, int(resize_size * img.size[1] / img.size[0])) if img.size[0] < img.size[1] else (int(resize_size * img.size[0] / img.size[1]), resize_size), Image.BICUBIC)
        # center crop
        width, height = img.size
        left = (width - target_size) / 2
        top = (height - target_size) / 2
        right = (width + target_size) / 2
        bottom = (height + target_size) / 2
        img = img.crop((left, top, right, bottom))
        arr = np.array(img).astype(np.float32) / 255.0
        # CLIP normalization
        mean = np.array([0.48145466, 0.4578275, 0.40821073], dtype=np.float32)
        std = np.array([0.26862954, 0.26130258, 0.27577711], dtype=np.float32)
        arr = (arr - mean) / std
        # HWC -> CHW
        arr = arr.transpose(2, 0, 1)
        arr = np.expand_dims(arr, axis=0).astype(np.float32)
        return arr
    except Exception as e:
        print("Image preprocessing failed:", e)
        raise


def embed_text(text: str):
    # Ensure text is a string
    if not isinstance(text, str):
        try:
            text = str(text) if text is not None else ''
        except Exception:
            text = ''
    if not text or not text.strip():
        text = 'empty'
    
    if sess_text is not None and tokenizer is not None:
        try:
            # Tokenize text
            encoded = tokenizer(text, padding=True, truncation=True, max_length=128, return_tensors="np")
            # Run ONNX inference with tokenized inputs
            onnx_inputs = {
                "input_ids": encoded["input_ids"].astype(np.int64),
                "attention_mask": encoded["attention_mask"].astype(np.int64)
            }
            # Add token_type_ids if model expects it
            if "token_type_ids" in encoded:
                onnx_inputs["token_type_ids"] = encoded["token_type_ids"].astype(np.int64)
            out = sess_text.run(None, onnx_inputs)
            # Mean pooling: average token embeddings weighted by attention mask
            token_embeddings = out[0]  # shape: (batch_size, seq_len, hidden_dim)
            attention_mask = encoded["attention_mask"]
            input_mask_expanded = np.expand_dims(attention_mask, -1).astype(np.float32)
            sum_embeddings = np.sum(token_embeddings * input_mask_expanded, axis=1)
            sum_mask = np.clip(np.sum(input_mask_expanded, axis=1), a_min=1e-9, a_max=None)
            vec = (sum_embeddings / sum_mask)[0]
            vec = vec.astype(np.float32)
            return vec.tolist()
        except Exception as e:
            print("ONNX text model inference failed, falling back:", e)
    return simple_hash_embedding(text)


def embed_image(image_bytes: bytes):
    # Try to run an image ONNX model if provided. Use CLIP-like preprocessing by default.
    if sess_image is not None:
        try:
            inp_name = sess_image.get_inputs()[0].name
            try:
                tensor = preprocess_image_for_clip(image_bytes, target_size=224)
            except Exception:
                # fallback to naive conversion
                tensor = np.frombuffer(image_bytes, dtype=np.uint8).astype(np.float32)
                # make a deterministic length
                tensor = np.resize(tensor, (1, 3, 224, 224))
            inputs = {inp_name: tensor}
            out = sess_image.run(None, inputs)
            vec = np.asarray(out[0][0]).astype(np.float32)
            return vec.tolist()
        except Exception as e:
            print("ONNX image model inference failed, falling back:", e)
    # fallback: hash the bytes to produce a deterministic vector
    try:
        pseudo_text = str(sum(image_bytes))
    except Exception:
        pseudo_text = 'image'
    return simple_hash_embedding(pseudo_text)


def connect_oracle():
    """Connect to Oracle using current database configuration."""
    if oracledb is None:
        print("oracledb module not available; cannot store to Oracle.")
        return None
    
    config = current_db_config
    
    try:
        # Check if this is a wallet-based connection
        if config.get('wallet_dir') and config.get('wallet_service'):
            # Wallet connection for Oracle Cloud (Autonomous Database)
            wallet_dir = config['wallet_dir']
            wallet_service = config['wallet_service']
            user = config['user']
            pwd = config['pwd']
            
            print(f"Attempting wallet connection to {config['name']}: {wallet_service} using wallet at {wallet_dir}")
            
            # Connect using config_dir and wallet_location for Oracle Cloud wallet
            conn = oracledb.connect(
                user=user,
                password=pwd,
                dsn=wallet_service,
                config_dir=wallet_dir,
                wallet_location=wallet_dir,
                wallet_password=ORACLE_WALLET_PASSWORD
            )
            print(f"Connected to {config['name']} database via wallet: {wallet_service}")
            return conn
        else:
            # Regular host-based connection
            dsn = f"{config['host']}:{config['port']}/{config['service']}"
            conn = oracledb.connect(user=config['user'], password=config['pwd'], dsn=dsn)
            print(f"Connected to {config['name']} database: {dsn}")
            return conn
    except Exception as e:
        print(f"Oracle connect failed for {config['name']}: {e}")
        import traceback
        traceback.print_exc()
        return None


def ensure_table(conn):
    """Create documents table with VECTOR column if Oracle 26ai+, otherwise use CLOB."""
    try:
        cur = conn.cursor()
        if USE_ORACLE_VECTOR_SEARCH:
            # Oracle 26ai+ with native VECTOR type in USERS tablespace (ASSM required)
            # Use 512 dimensions to accommodate both text (384) and image (512) embeddings
            cur.execute(f"""
            BEGIN
                EXECUTE IMMEDIATE 'CREATE TABLE documents (
                    id VARCHAR2(36),
                    name VARCHAR2(256),
                    chunk_index NUMBER DEFAULT 0,
                    text CLOB,
                    embedding VECTOR(512, FLOAT32),
                    PRIMARY KEY (id, chunk_index)
                ) TABLESPACE USERS';
            EXCEPTION
                WHEN OTHERS THEN
                    IF SQLCODE = -955 THEN NULL; -- Table already exists
                    ELSIF SQLCODE = -902 THEN -- Invalid datatype (pre-26ai)
                        RAISE_APPLICATION_ERROR(-20001, 'VECTOR type not supported. Set USE_ORACLE_VECTOR_SEARCH=false or upgrade to Oracle 26ai+');
                    ELSIF SQLCODE = -43853 THEN -- VECTOR type requires ASSM tablespace
                        RAISE_APPLICATION_ERROR(-20002, 'VECTOR type requires ASSM tablespace. Using USERS tablespace or set USE_ORACLE_VECTOR_SEARCH=false');
                    ELSE RAISE;
                    END IF;
            END;
            """)
        else:
            # Legacy mode: CLOB for JSON array storage
            cur.execute("""
            BEGIN
                EXECUTE IMMEDIATE 'CREATE TABLE documents (
                    id VARCHAR2(36),
                    name VARCHAR2(256),
                    chunk_index NUMBER DEFAULT 0,
                    text CLOB,
                    vector CLOB,
                    PRIMARY KEY (id, chunk_index)
                )';
            EXCEPTION
                WHEN OTHERS THEN
                    IF SQLCODE != -955 THEN RAISE; END IF;
            END;
            """)
        conn.commit()
        print(f"Table ensured with {'VECTOR' if USE_ORACLE_VECTOR_SEARCH else 'CLOB'} storage")
    except Exception as e:
        print("ensure_table error:", e)
        raise


def chunk_text(text: str, chunk_size: int = 500, overlap: int = 100):
    """Split text into overlapping chunks for better retrieval."""
    if len(text) <= chunk_size:
        return [text]
    
    chunks = []
    start = 0
    while start < len(text):
        end = start + chunk_size
        chunk = text[start:end]
        
        # Try to break at sentence boundary
        if end < len(text):
            last_period = chunk.rfind('.')
            last_newline = chunk.rfind('\n')
            break_point = max(last_period, last_newline)
            if break_point > chunk_size * 0.5:  # Only break if we're past halfway
                chunk = chunk[:break_point + 1]
                end = start + break_point + 1
        
        chunks.append(chunk.strip())
        start = end - overlap
        
        if start >= len(text):
            break
    
    return chunks

@app.post("/embed")
async def embed(file: UploadFile = File(...), title: Optional[str] = Form(None)):
    contents = await file.read()
    content_type = getattr(file, 'content_type', '') or ''
    # If the upload is an image based on content type, use image embedding path
    if content_type.startswith('image/'):
        vec = embed_image(contents)
        # store a short textual name for the image in the text field
        text = title or file.filename or 'uploaded_image'
    else:
        try:
            parsed = parser.from_buffer(contents)
            print(f"Tika parsed document: {file.filename}, type: {type(parsed)}")
        except Exception as e:
            print(f"Tika parse failed for {file.filename}:", e)
            parsed = {'content': ''}
        # Guard against unexpected return types
        if isinstance(parsed, dict):
            text = parsed.get('content') or ''
        else:
            try:
                text = getattr(parsed, 'get', lambda k: '')('content') or ''
            except Exception:
                text = ''
        
        text_length = len(text.strip()) if text else 0
        print(f"Extracted text length: {text_length} chars from {file.filename}")
        
        if not text.strip():
            text = file.filename or 'uploaded'
            print(f"Warning: No text extracted from {file.filename}, using filename as fallback")
        vec = embed_text(text)
    
    # Pad vector to 512 dimensions to accommodate both text (384) and image (512) models
    # Use mean padding instead of zeros to avoid artificially inflating similarity
    if len(vec) < 512:
        mean_val = sum(vec) / len(vec) if len(vec) > 0 else 0.0
        vec = vec + [mean_val] * (512 - len(vec))
    elif len(vec) > 512:
        vec = vec[:512]
    
    doc_id = str(uuid.uuid4())
    
    # Chunk the text for better retrieval (skip for images)
    if not content_type.startswith('image/'):
        chunks = chunk_text(text)
    else:
        chunks = [text]  # Images are not chunked

    conn = connect_oracle()
    chunks_stored = 0
    if conn:
        try:
            ensure_table(conn)
            cur = conn.cursor()
            
            # Store each chunk with its own embedding
            for chunk_idx, chunk in enumerate(chunks):
                # Embed the chunk
                if content_type.startswith('image/'):
                    chunk_vec = vec  # Use image embedding
                else:
                    chunk_vec = embed_text(chunk)
                    # Pad to 512 dimensions
                    if len(chunk_vec) < 512:
                        mean_val = sum(chunk_vec) / len(chunk_vec) if len(chunk_vec) > 0 else 0.0
                        chunk_vec = chunk_vec + [mean_val] * (512 - len(chunk_vec))
                    elif len(chunk_vec) > 512:
                        chunk_vec = chunk_vec[:512]
                
                if USE_ORACLE_VECTOR_SEARCH:
                    vec_json = json.dumps(chunk_vec)
                    cur.execute(
                        "INSERT INTO documents (id, name, chunk_index, text, embedding) VALUES (:1, :2, :3, :4, TO_VECTOR(:5))",
                        (doc_id, title or file.filename, chunk_idx, chunk, vec_json)
                    )
                else:
                    cur.execute(
                        "INSERT INTO documents (id, name, chunk_index, text, vector) VALUES (:1, :2, :3, :4, :5)",
                        (doc_id, title or file.filename, chunk_idx, chunk, json.dumps(chunk_vec))
                    )
                chunks_stored += 1
            
            conn.commit()
        except Exception as e:
            print("Insert failed:", e)
    return {"id": doc_id, "vector_dim": len(vec), "name": title or file.filename, "chunks": chunks_stored}


@app.post("/search")
async def search(q: str = Form(...), top_k: int = Form(5)):
    qvec = embed_text(q)
    
    # Pad query vector to 512 dimensions
    if len(qvec) < 512:
        mean_val = sum(qvec) / len(qvec) if len(qvec) > 0 else 0.0
        qvec = qvec + [mean_val] * (512 - len(qvec))
    elif len(qvec) > 512:
        qvec = qvec[:512]
    
    conn = connect_oracle()
    results = []
    if conn:
        try:
            cur = conn.cursor()
            
            if USE_ORACLE_VECTOR_SEARCH:
                # Oracle 26ai+ native vector search with TO_VECTOR() and VECTOR_DISTANCE()
                qvec_json = json.dumps(qvec)
                # COSINE distance: returns 0-2 (0=identical, 2=opposite)
                # Convert to similarity score: 1 - (distance/2) = 0-1 scale
                cur.execute("""
                    SELECT id, name, chunk_index, text,
                           (1 - VECTOR_DISTANCE(embedding, TO_VECTOR(:qvec), COSINE) / 2) as score
                    FROM documents
                    ORDER BY score DESC
                    FETCH FIRST :top_k ROWS ONLY
                """, {"qvec": qvec_json, "top_k": top_k})
                rows = cur.fetchall()
                for r in rows:
                    chunk_text = r[3]
                    if hasattr(chunk_text, 'read'):
                        chunk_text = chunk_text.read()
                    results.append({
                        "id": r[0], 
                        "name": r[1], 
                        "chunk_index": r[2],
                        "text": chunk_text[:500] if chunk_text else "",  # Return up to 500 chars
                        "score": float(r[4])
                    })
            else:
                # Python-based cosine similarity (legacy mode)
                cur.execute("SELECT id, name, text, vector FROM documents")
                rows = cur.fetchall()
                for r in rows:
                    try:
                        vec_data = r[3]
                        # Handle Oracle CLOB objects
                        if hasattr(vec_data, 'read'):
                            vec_data = vec_data.read()
                        vec = json.loads(vec_data)
                        a = np.array(qvec, dtype=np.float32)
                        b = np.array(vec, dtype=np.float32)
                        denom = (np.linalg.norm(a) * np.linalg.norm(b))
                        score = float(np.dot(a, b) / denom) if denom > 0 else 0.0
                    except Exception as e:
                        print(f"Score calculation failed for {r[0]}: {e}")
                        score = 0.0
                    results.append({"id": r[0], "name": r[1], "score": score})
                results = sorted(results, key=lambda x: x['score'], reverse=True)[:top_k]
        except Exception as e:
            print("Search failed:", e)
    return {"query": q, "results": results, "mode": "oracle_vector" if USE_ORACLE_VECTOR_SEARCH else "python_numpy"}


@app.post("/rag")
async def rag_query(q: str = Form(...), top_k: int = Form(5)):
    """RAG endpoint: Search documents and use LLM to generate answer."""
    if not OpenAI or not OPENAI_API_KEY:
        return {"error": "OpenAI not configured. Set OPENAI_API_KEY environment variable."}
    
    # Step 1: Perform vector search
    qvec = embed_text(q)
    
    # Pad query vector to 512 dimensions
    if len(qvec) < 512:
        mean_val = sum(qvec) / len(qvec) if len(qvec) > 0 else 0.0
        qvec = qvec + [mean_val] * (512 - len(qvec))
    elif len(qvec) > 512:
        qvec = qvec[:512]
    
    conn = connect_oracle()
    results = []
    context_chunks = []
    
    if conn:
        try:
            cur = conn.cursor()
            
            if USE_ORACLE_VECTOR_SEARCH:
                qvec_json = json.dumps(qvec)
                cur.execute("""
                    SELECT id, name, chunk_index, text,
                           (1 - VECTOR_DISTANCE(embedding, TO_VECTOR(:qvec), COSINE) / 2) as score
                    FROM documents
                    ORDER BY score DESC
                    FETCH FIRST :top_k ROWS ONLY
                """, {"qvec": qvec_json, "top_k": top_k})
                rows = cur.fetchall()
                for r in rows:
                    chunk_text = r[3]
                    if hasattr(chunk_text, 'read'):
                        chunk_text = chunk_text.read()
                    results.append({
                        "id": r[0], 
                        "name": r[1], 
                        "chunk_index": r[2],
                        "text": chunk_text,
                        "score": float(r[4])
                    })
                    context_chunks.append(chunk_text)
            else:
                # Python-based fallback
                cur.execute("SELECT id, name, text, vector FROM documents")
                rows = cur.fetchall()
                scored_docs = []
                for r in rows:
                    try:
                        vec_data = r[3]
                        if hasattr(vec_data, 'read'):
                            vec_data = vec_data.read()
                        vec = json.loads(vec_data)
                        a = np.array(qvec, dtype=np.float32)
                        b = np.array(vec, dtype=np.float32)
                        denom = (np.linalg.norm(a) * np.linalg.norm(b))
                        score = float(np.dot(a, b) / denom) if denom > 0 else 0.0
                        scored_docs.append({"id": r[0], "name": r[1], "text": r[2], "score": score})
                    except Exception as e:
                        print(f"Score calculation failed: {e}")
                scored_docs = sorted(scored_docs, key=lambda x: x['score'], reverse=True)[:top_k]
                results = scored_docs
                context_chunks = [doc["text"] for doc in scored_docs]
        except Exception as e:
            print("RAG search failed:", e)
            return {"error": f"Search failed: {str(e)}"}
    
    # Step 2: Build context
    context = "\n\n".join([f"[Source: {r['name']}]\n{chunk}" for r, chunk in zip(results, context_chunks)])
    
    # Step 3: Call OpenAI
    try:
        client = OpenAI(api_key=OPENAI_API_KEY)
        
        system_prompt = """You are a helpful assistant that answers questions based on the provided context. 
Always base your answer on the context provided. If the context doesn't contain enough information to answer 
the question, say so. Cite the source documents when providing information."""
        
        user_prompt = f"""Context from documents:
{context}

Question: {q}

Please provide a comprehensive answer based on the context above."""
        
        response = client.chat.completions.create(
            model=OPENAI_MODEL,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            temperature=0.7,
            max_tokens=1000
        )
        
        answer = response.choices[0].message.content
        
        return {
            "query": q,
            "answer": answer,
            "sources": [{
                "name": r["name"], 
                "score": r["score"], 
                "chunk_index": r.get("chunk_index", 0),
                "snippet": r["text"][:200] + "..." if len(r["text"]) > 200 else r["text"]
            } for r in results],
            "model": OPENAI_MODEL
        }
    except Exception as e:
        print(f"OpenAI API call failed: {e}")
        return {"error": f"LLM generation failed: {str(e)}"}


@app.get("/list")
async def list_documents():
    """List all documents in the database with their IDs and names."""
    conn = connect_oracle()
    documents = []
    if conn:
        try:
            cur = conn.cursor()
            # Get distinct documents (one per ID), showing first chunk's name
            cur.execute("SELECT DISTINCT id, name FROM documents WHERE chunk_index = 0 ORDER BY name")
            rows = cur.fetchall()
            for r in rows:
                documents.append({"id": r[0], "name": r[1]})
        except Exception as e:
            print("List failed:", e)
    return {"documents": documents, "count": len(documents)}


@app.post("/reembed")
async def reembed_documents(doc_ids: Optional[str] = Form(None)):
    """Re-embed existing documents (all if doc_ids is None/empty, or comma-separated IDs)."""
    conn = connect_oracle()
    reembedded = []
    if conn:
        try:
            cur = conn.cursor()
            if doc_ids and doc_ids.strip():
                # Re-embed specific documents (comma-separated IDs)
                id_list = [x.strip() for x in doc_ids.split(',') if x.strip()]
                placeholders = ','.join([':' + str(i+1) for i in range(len(id_list))])
                query = f"SELECT id, name, text FROM documents WHERE id IN ({placeholders})"
                cur.execute(query, id_list)
            else:
                # Re-embed all documents
                cur.execute("SELECT id, name, text FROM documents")
            rows = cur.fetchall()
            for r in rows:
                doc_id, name, text = r[0], r[1], r[2]
                # Handle Oracle CLOB objects
                if hasattr(text, 'read'):
                    text = text.read()
                vec = embed_text(text or name)
                
                # Pad vector to 512 dimensions
                if len(vec) < 512:
                    mean_val = sum(vec) / len(vec) if len(vec) > 0 else 0.0
                    vec = vec + [mean_val] * (512 - len(vec))
                elif len(vec) > 512:
                    vec = vec[:512]
                
                if USE_ORACLE_VECTOR_SEARCH:
                    vec_json = json.dumps(vec)
                    cur.execute("UPDATE documents SET embedding = TO_VECTOR(:1) WHERE id = :2",
                               (vec_json, doc_id))
                else:
                    cur.execute("UPDATE documents SET vector = :1 WHERE id = :2",
                               (json.dumps(vec), doc_id))
                reembedded.append({"id": doc_id, "name": name, "vector_dim": len(vec)})
            conn.commit()
        except Exception as e:
            print("Re-embed failed:", e)
            if conn:
                conn.rollback()
    return {"reembedded": reembedded, "count": len(reembedded)}

@app.delete("/documents/{doc_id}")
async def delete_document(doc_id: str):
    """Delete a document and all its chunks by ID."""
    conn = connect_oracle()
    deleted = False
    if conn:
        try:
            cur = conn.cursor()
            cur.execute("DELETE FROM documents WHERE id = :1", (doc_id,))
            rows_deleted = cur.rowcount
            conn.commit()
            deleted = rows_deleted > 0
            return {"success": deleted, "id": doc_id, "chunks_deleted": rows_deleted}
        except Exception as e:
            print("Delete failed:", e)
            if conn:
                conn.rollback()
            return {"success": False, "error": str(e)}
    return {"success": False, "error": "No database connection"}

@app.get("/config")
async def get_config():
    """Get current configuration including vector search mode."""
    return {
        "vector_mode": VECTOR_MODE,
        "oracle_host": current_db_config['host'],
        "oracle_service": current_db_config['service'],
        "current_database": current_db_config['name'],
        "text_model": "loaded" if sess_text else "fallback",
        "image_model": "loaded" if sess_image else "fallback"
    }

@app.post("/config")
async def update_config(request: dict):
    """Update configuration (currently supports toggling vector search mode).
    Note: Requires container restart to fully apply changes to environment variables."""
    global VECTOR_MODE
    
    if "vector_mode" in request:
        mode = request["vector_mode"]
        if mode not in ["oracle_native", "oracle_hybrid", "python"]:
            return {"success": False, "error": f"Invalid vector_mode: {mode}"}
        
        VECTOR_MODE = mode
        # Update environment variable so child processes see it
        os.environ["VECTOR_MODE"] = mode
        return {
            "success": True,
            "vector_mode": VECTOR_MODE,
            "message": f"Vector mode updated to: {mode}"
        }
    
    return {"success": False, "error": "No valid configuration provided"}

@app.post("/setup/import-onnx-model")
async def import_onnx_model(request: dict):
    """Import ONNX model into Oracle database for VECTOR_EMBEDDING() support.
    
    Args:
        request: {
            "model_name": "all_MiniLM_L12_v2",  # Name to store in database
            "model_url": "https://huggingface.co/...",  # URL to download from
            "force_reimport": false  # Optional: force reimport if exists
        }
    """
    conn = connect_oracle()
    if not conn:
        return {"success": False, "error": "No database connection"}
    
    model_name = request.get("model_name", "all_MiniLM_L12_v2").upper()
    # Use Oracle's pre-augmented ONNX model zip download
    # The augmented model includes preprocessing/postprocessing and has fixed tensor dimensions
    model_url = request.get("model_url", "https://adwc4pm.objectstorage.us-ashburn-1.oci.customer-oci.com/p/VBRD9P8ZFWkKvnfhrWxkpPe8K03-JIoM5h_8EJyJcpE80c108fuUjg7R5L5O7mMZ/n/adwc4pm/b/OML-Resources/o/all_MiniLM_L12_v2_augmented.zip")
    force_reimport = request.get("force_reimport", False)
    
    try:
        cur = conn.cursor()
        
        # Check Oracle version
        cur.execute("SELECT version FROM v$instance")
        version = cur.fetchone()[0]
        major_version = int(version.split('.')[0])
        
        if major_version < 23:
            return {
                "success": False, 
                "error": f"Oracle 26ai+ required for VECTOR_EMBEDDING(). Current version: {version}"
            }
        
        # First, let's check what DBMS_VECTOR procedures are available
        try:
            cur.execute("""
                SELECT object_name, procedure_name, overload 
                FROM all_procedures 
                WHERE object_name = 'DBMS_VECTOR' 
                AND owner = 'SYS'
                ORDER BY procedure_name, overload
            """)
            available_procs = cur.fetchall()
            print(f"Available DBMS_VECTOR procedures: {available_procs}")
            
            # Get parameter details for LOAD_ONNX_MODEL
            cur.execute("""
                SELECT argument_name, position, data_type, in_out, overload
                FROM all_arguments 
                WHERE object_name = 'DBMS_VECTOR' 
                AND procedure_name = 'LOAD_ONNX_MODEL'
                AND owner = 'SYS'
                ORDER BY overload, position
            """)
            params = cur.fetchall()
            print(f"LOAD_ONNX_MODEL parameters: {params}")
        except Exception as e:
            print(f"Could not check DBMS_VECTOR procedures: {e}")
        
        # Check if model already exists
        cur.execute("""
            SELECT COUNT(*) FROM user_mining_models 
            WHERE model_name = :1
        """, (model_name,))
        exists = cur.fetchone()[0] > 0
        
        if exists and not force_reimport:
            return {
                "success": False,
                "error": f"Model '{model_name}' already exists. Set force_reimport=true to replace it.",
                "model_name": model_name
            }
        
        # Drop existing model if force reimport
        if exists and force_reimport:
            try:
                cur.execute(f"BEGIN DBMS_VECTOR.DROP_ONNX_MODEL(model_name => '{model_name}', force => TRUE); END;")
                conn.commit()
            except Exception as e:
                print(f"Warning: Could not drop existing model: {e}")
        
        # LOAD_ONNX_MODEL requires a BLOB parameter, not a URL string
        # We need to download the model first, then pass it as a BLOB
        print(f"Downloading ONNX model from {model_url}...")
        
        import requests
        import zipfile
        import io
        
        response = requests.get(model_url, timeout=300)  # 5 minute timeout for large model
        response.raise_for_status()
        
        print(f"Downloaded {len(response.content)} bytes")
        
        # If it's a zip file, extract the .onnx file
        if model_url.endswith('.zip'):
            print("Extracting ONNX file from zip archive...")
            with zipfile.ZipFile(io.BytesIO(response.content)) as z:
                # Find the .onnx file in the zip
                onnx_files = [name for name in z.namelist() if name.endswith('.onnx')]
                if not onnx_files:
                    raise Exception("No .onnx file found in zip archive")
                
                onnx_filename = onnx_files[0]
                print(f"Found {onnx_filename} in zip")
                model_blob = z.read(onnx_filename)
                print(f"Extracted {len(model_blob)} bytes")
        else:
            model_blob = response.content
            print(f"Using downloaded file directly: {len(model_blob)} bytes")
        
        # Load the ONNX model using BLOB
        print(f"Importing ONNX model '{model_name}' into database...")
        
        # Try both overloads with different approaches
        import_attempts = [
            # Attempt 1: Just model_name and blob (simplest form)
            {
                'sql': """
                    BEGIN
                        DBMS_VECTOR.LOAD_ONNX_MODEL(:1, :2);
                    END;
                """,
                'params': [model_name, model_blob],
                'description': 'Positional: model_name, blob'
            },
            # Attempt 2: With JSON metadata as third parameter
            {
                'sql': """
                    BEGIN
                        DBMS_VECTOR.LOAD_ONNX_MODEL(:1, :2, :3);
                    END;
                """,
                'params': [model_name, model_blob, '{"function": "embedding", "embeddingOutput": "embedding"}'],
                'description': 'Positional: model_name, blob, json_string'
            },
            # Attempt 3: Named parameters
            {
                'sql': """
                    BEGIN
                        DBMS_VECTOR.LOAD_ONNX_MODEL(
                            model_name => :model_name,
                            onnx_model => :model_blob
                        );
                    END;
                """,
                'params': {'model_name': model_name, 'model_blob': model_blob},
                'description': 'Named: model_name, onnx_model'
            }
        ]
        
        last_error = None
        for idx, attempt in enumerate(import_attempts):
            try:
                print(f"Attempt {idx+1} ({attempt['description']})...")
                cur.setinputsizes(None, oracledb.DB_TYPE_BLOB)
                cur.execute(attempt['sql'], attempt['params'])
                conn.commit()
                print(f"✓ Successfully imported with attempt {idx+1}!")
                break
            except Exception as e:
                last_error = str(e)
                print(f"✗ Attempt {idx+1} failed: {last_error[:200]}")
                if idx < len(import_attempts) - 1:
                    continue
                else:
                    raise Exception(f"All import attempts failed. Last error: {last_error}")
        
        # Verify import
        cur.execute("""
            SELECT model_name, mining_function, algorithm 
            FROM user_mining_models 
            WHERE model_name = :1
        """, (model_name,))
        result = cur.fetchone()
        
        if result:
            # Test VECTOR_EMBEDDING() function
            test_sql = f"SELECT VECTOR_EMBEDDING({model_name} USING 'test' as data) as embedding FROM dual"
            cur.execute(test_sql)
            test_result = cur.fetchone()
            embedding_dim = len(test_result[0]) if test_result else 0
            
            return {
                "success": True,
                "model_name": result[0],
                "mining_function": result[1],
                "algorithm": result[2],
                "embedding_dimension": embedding_dim,
                "message": f"Model '{model_name}' imported successfully! VECTOR_EMBEDDING() is now available.",
                "test_query": f"SELECT VECTOR_EMBEDDING({model_name} USING 'your text' as data) FROM dual"
            }
        else:
            return {
                "success": False,
                "error": "Model import completed but could not verify. Check database logs."
            }
            
    except Exception as e:
        error_msg = str(e)
        print(f"ONNX model import failed: {error_msg}")
        
        # Check for common errors
        if "ORA-20000" in error_msg or "network" in error_msg.lower():
            return {
                "success": False,
                "error": "Network access error. Database may need ACL configuration for external URLs.",
                "details": error_msg,
                "solution": "Ask DBA to grant network access: BEGIN DBMS_NETWORK_ACL_ADMIN.APPEND_HOST_ACE(...); END;"
            }
        elif "insufficient privileges" in error_msg.lower():
            return {
                "success": False,
                "error": "Insufficient privileges. User needs EXECUTE on DBMS_VECTOR.",
                "details": error_msg,
                "solution": "GRANT EXECUTE ON CTXSYS.CTX_DDL TO <user>; GRANT EXECUTE ON DBMS_VECTOR TO <user>;"
            }
        else:
            return {
                "success": False,
                "error": f"Failed to import ONNX model: {error_msg}"
            }
    finally:
        if conn:
            conn.close()

@app.get("/setup/check-onnx-model")
async def check_onnx_model():
    """Check if ONNX models are imported and VECTOR_EMBEDDING() is available."""
    conn = connect_oracle()
    if not conn:
        return {"success": False, "error": "No database connection"}
    
    try:
        cur = conn.cursor()
        
        # Check Oracle version
        cur.execute("SELECT version FROM v$instance")
        version = cur.fetchone()[0]
        major_version = int(version.split('.')[0])
        
        supports_vector = major_version >= 23
        
        # List all imported ONNX models
        cur.execute("""
            SELECT model_name, mining_function, algorithm, build_duration 
            FROM user_mining_models 
            ORDER BY model_name
        """)
        models = []
        for row in cur.fetchall():
            models.append({
                "name": row[0],
                "function": row[1],
                "algorithm": row[2],
                "build_duration": str(row[3]) if row[3] else None
            })
        
        return {
            "success": True,
            "oracle_version": version,
            "supports_vector_embedding": supports_vector,
            "models_imported": len(models),
            "models": models,
            "can_use_oracle_native": supports_vector and len(models) > 0
        }
    except Exception as e:
        return {
            "success": False,
            "error": str(e)
        }
    finally:
        if conn:
            conn.close()

@app.get("/databases")
async def list_databases():
    """List all available database connections."""
    configs = load_database_configs()
    # Return configs without passwords
    safe_configs = []
    for name, config in configs.items():
        if config.get('wallet_dir'):
            # Wallet connection
            safe_configs.append({
                "name": config["name"],
                "host": "wallet",
                "port": 0,
                "service": config["wallet_service"],
                "user": config["user"],
                "wallet": True,
                "is_active": config["name"] == current_db_config["name"]
            })
        else:
            # Regular connection
            safe_configs.append({
                "name": config["name"],
                "host": config["host"],
                "port": config["port"],
                "service": config["service"],
                "user": config["user"],
                "wallet": False,
                "is_active": config["name"] == current_db_config["name"]
            })
    return {"databases": safe_configs, "count": len(safe_configs)}

@app.post("/databases/switch")
async def switch_database(request: dict):
    """Switch to a different database connection."""
    global current_db_config
    
    if "database_name" not in request:
        return {"success": False, "error": "database_name is required"}
    
    db_name = request["database_name"]
    configs = load_database_configs()
    
    if db_name not in configs:
        return {"success": False, "error": f"Database '{db_name}' not found"}
    
    # Test connection before switching
    test_config = configs[db_name]
    
    try:
        if oracledb is None:
            return {"success": False, "error": "oracledb module not available"}
        
        # Test connection based on type
        if test_config.get('wallet_dir') and test_config.get('wallet_service'):
            # Wallet connection - Oracle Cloud Autonomous Database
            test_conn = oracledb.connect(
                user=test_config['user'],
                password=test_config['pwd'],
                dsn=test_config['wallet_service'],
                config_dir=test_config['wallet_dir'],
                wallet_location=test_config['wallet_dir'],
                wallet_password=ORACLE_WALLET_PASSWORD
            )
            conn_info = f"wallet: {test_config['wallet_service']}"
        else:
            # Regular connection
            dsn = f"{test_config['host']}:{test_config['port']}/{test_config['service']}"
            test_conn = oracledb.connect(user=test_config['user'], password=test_config['pwd'], dsn=dsn)
            conn_info = f"{test_config['host']}:{test_config['port']}/{test_config['service']}"
        
        test_conn.close()
        
        # Connection successful, switch
        current_db_config = test_config
        return {
            "success": True,
            "database": db_name,
            "connection_info": conn_info,
            "message": f"Switched to database: {db_name}"
        }
    except Exception as e:
        import traceback
        error_detail = traceback.format_exc()
        print(f"Switch database error: {error_detail}")
        return {"success": False, "error": f"Connection failed: {str(e)}"}
