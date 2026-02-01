# Quick Reference - VECTOR Schema

## 🚀 Installation (Clean Install - No Data Import)

### Prerequisites
- Oracle 23c+ with AI Vector Search
- Admin access to aiholodb
- ONNX model files from demo vector:
  - `tinybert.onnx` (17.7 MB)
  - `all-MiniLM-L6-v2.onnx` (90.6 MB)
  - Location: `/u01/app/oracle/admin/orcl/dpdump/3439A922B4626531E0637E0B00632F3A/`

### Installation Steps

```sql
-- 1. Create user (as ADMIN)
@01_create_user.sql

-- 2. Create tables (as VECTOR)
@02_create_tables.sql

-- 3. Import ML models (as VECTOR)
-- First: Download and upload ONNX files to aiholodb
@08_import_ml_models_from_onnx.sql

-- 4. Create functions (as VECTOR)
-- First: Edit file to update Ollama URL!
@09_create_all_functions.sql

-- 5. Done! Now upload PDFs
@10_upload_and_vectorize_pdf.sql
```

---

## 📤 Upload and Vectorize PDFs

### Quick Upload
```sql
-- Create directory (as ADMIN)
CREATE OR REPLACE DIRECTORY PDF_FILES AS 'C:\pdfs';
GRANT READ ON DIRECTORY PDF_FILES TO vector;

-- Upload PDF
DECLARE
  l_bfile  BFILE := BFILENAME('PDF_FILES', 'my_doc.pdf');
  l_blob   BLOB;
  l_new_id NUMBER;
  l_size   NUMBER;
BEGIN
  DBMS_LOB.FILEOPEN(l_bfile, DBMS_LOB.FILE_READONLY);
  l_size := DBMS_LOB.GETLENGTH(l_bfile);
  DBMS_LOB.CREATETEMPORARY(l_blob, TRUE);
  DBMS_LOB.LOADBLOBFROMFILE(l_blob, l_bfile, l_size);
  DBMS_LOB.FILECLOSE(l_bfile);
  
  insert_my_table_row('my_doc.pdf', l_size, 'application/pdf', l_blob, l_new_id);
  DBMS_OUTPUT.PUT_LINE('Doc ID: ' || l_new_id);
  
  DBMS_LOB.FREETEMPORARY(l_blob);
  COMMIT;
END;
/
```

### Quick Vectorize
```sql
DECLARE
  l_doc_id NUMBER := 1;  -- Your document ID
  l_text CLOB;
  l_chunk VARCHAR2(4000);
  l_vec VECTOR;
  l_chunk_id NUMBER := 1;
BEGIN
  -- Extract text from PDF
  SELECT DBMS_VECTOR_CHAIN.UTL_TO_TEXT(file_content) INTO l_text
  FROM my_books WHERE id = l_doc_id;
  
  -- Delete old chunks
  DELETE FROM vector_store WHERE doc_id = l_doc_id;
  
  -- Chunk and vectorize (1000 char chunks with 200 overlap)
  FOR i IN 0..FLOOR(LENGTH(l_text)/800) LOOP
    l_chunk := SUBSTR(l_text, i*800+1, 1000);
    IF TRIM(l_chunk) IS NOT NULL THEN
      SELECT TO_VECTOR(VECTOR_EMBEDDING(TINYBERT_MODEL USING l_chunk AS data))
      INTO l_vec FROM DUAL;
      
      INSERT INTO vector_store VALUES (l_doc_id, l_chunk_id, l_chunk, l_vec);
      l_chunk_id := l_chunk_id + 1;
    END IF;
  END LOOP;
  
  COMMIT;
  DBMS_OUTPUT.PUT_LINE('Created ' || (l_chunk_id-1) || ' chunks');
END;
/
```

---

## 🔍 Query Your Documents

### Basic RAG Query
```sql
SELECT generate_text_response2(
  'What is this document about?',  -- Your question
  1,                                -- Document ID
  5                                 -- Number of chunks to retrieve
) AS response 
FROM dual;
```

### Test Vector Search
```sql
-- Find similar chunks
SELECT doc_id, embed_id, 
       SUBSTR(embed_data, 1, 100) as chunk_preview
FROM vector_store
WHERE doc_id = 1
ORDER BY VECTOR_DISTANCE(
  embed_vector,
  (SELECT TO_VECTOR(VECTOR_EMBEDDING(TINYBERT_MODEL USING 'your query' AS data)) FROM DUAL),
  COSINE
)
FETCH FIRST 5 ROWS ONLY;
```

---

## 🛠️ Common Tasks

### List All Documents
```sql
SELECT id, file_name, file_size, created_on FROM my_books ORDER BY id;
```

### Count Chunks Per Document
```sql
SELECT doc_id, COUNT(*) as chunks FROM vector_store GROUP BY doc_id;
```

### Re-vectorize a Document
```sql
DELETE FROM vector_store WHERE doc_id = 1;
-- Then run vectorization script from 10_upload_and_vectorize_pdf.sql
```

### Batch Process All Unvectorized Documents
```sql
-- See METHOD 4 in 10_upload_and_vectorize_pdf.sql
```

### Update Ollama URL
```sql
-- Edit and re-run the function from 09_create_all_functions.sql
-- Change line: "url":"http://YOUR_OLLAMA_SERVER:11434/api/generate"
```

---

## 📊 Verification Queries

```sql
-- Check ML models
SELECT model_name, mining_function FROM user_mining_models;

-- Check functions
SELECT object_name, status FROM user_objects WHERE object_type = 'FUNCTION';

-- Test Ollama connection
SELECT ollama_test_function('Hello, are you working?') FROM dual;

-- View sample chunks
SELECT embed_id, SUBSTR(embed_data, 1, 80) FROM vector_store WHERE doc_id = 1 
FETCH FIRST 3 ROWS ONLY;
```

---

## 🆘 Troubleshooting

| Error | Solution |
|-------|----------|
| ORA-40284: model does not exist | Import ONNX models (step 3) |
| Network error in generate_text_response2 | Update Ollama URL in function |
| ORA-22288: file operation failed | Check directory exists and has permissions |
| Empty response from Ollama | Verify Ollama server is running |
| Function compilation errors | Ensure ML models imported first |

---

## 📁 File Reference

| File | Purpose |
|------|---------|
| `CLEAN_INSTALL_README.md` | This overview |
| `01_create_user.sql` | Create VECTOR user |
| `02_create_tables.sql` | Create tables |
| `08_import_ml_models_from_onnx.sql` | Import ONNX models |
| `09_create_all_functions.sql` | Create all functions |
| `10_upload_and_vectorize_pdf.sql` | PDF upload guide |
| `11_example_workflow.sql` | Complete example |

---

## 💡 Tips

- **Chunk Size**: 1000 characters works well for most documents
- **Overlap**: 200 characters preserves context across chunks
- **Model Choice**: TINYBERT is faster, ALL_MINILM is more accurate
- **Top-N**: Retrieve 3-5 chunks for concise answers, 10+ for comprehensive answers
- **Re-processing**: Delete from vector_store before re-vectorizing a document

---

## 🎯 Next Steps

1. ✅ Install schema (steps 1-4 above)
2. ✅ Upload your first PDF
3. ✅ Vectorize it
4. ✅ Test a query
5. 🚀 Build your RAG application!
