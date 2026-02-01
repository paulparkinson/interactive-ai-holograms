-- ================================================================
-- Complete Example Workflow
-- ================================================================
-- This script demonstrates a complete end-to-end workflow:
-- 1. Upload a PDF
-- 2. Vectorize it
-- 3. Query it using RAG
-- ================================================================

SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT
PROMPT ================================================================
PROMPT VECTOR SCHEMA - COMPLETE EXAMPLE WORKFLOW
PROMPT ================================================================
PROMPT

-- ================================================================
-- STEP 1: Check Current State
-- ================================================================
PROMPT Step 1: Checking current state...
PROMPT

-- Check if ML models are loaded
SELECT COUNT(*) as model_count 
FROM user_mining_models;

-- Expected: 2 (TINYBERT_MODEL and ALL_MINILM_L6V2MODEL)

-- Check current documents
SELECT id, file_name, file_size 
FROM my_books 
ORDER BY id;

-- Check vectorized chunks
SELECT doc_id, COUNT(*) as num_chunks
FROM vector_store
GROUP BY doc_id
ORDER BY doc_id;

-- ================================================================
-- STEP 2: Upload a Sample PDF (EXAMPLE)
-- ================================================================
PROMPT
PROMPT Step 2: Upload PDF Example
PROMPT (Update with your actual file path and name)
PROMPT

/*
-- Uncomment and modify this block to upload your PDF:

DECLARE
  l_bfile  BFILE;
  l_blob   BLOB;
  l_dest_offset NUMBER := 1;
  l_src_offset  NUMBER := 1;
  l_file_size   NUMBER;
  l_new_id      NUMBER;
BEGIN
  -- Update these values:
  l_bfile := BFILENAME('PDF_FILES', 'your_document.pdf');
  
  DBMS_LOB.FILEOPEN(l_bfile, DBMS_LOB.FILE_READONLY);
  l_file_size := DBMS_LOB.GETLENGTH(l_bfile);
  
  DBMS_LOB.CREATETEMPORARY(l_blob, TRUE);
  
  DBMS_LOB.LOADBLOBFROMFILE(
    dest_lob    => l_blob,
    src_bfile   => l_bfile,
    amount      => l_file_size,
    dest_offset => l_dest_offset,
    src_offset  => l_src_offset
  );
  
  DBMS_LOB.FILECLOSE(l_bfile);
  
  insert_my_table_row(
    p_file_name    => 'your_document.pdf',
    p_file_size    => l_file_size,
    p_file_type    => 'application/pdf',
    p_file_content => l_blob,
    p_new_id       => l_new_id
  );
  
  DBMS_OUTPUT.PUT_LINE('✓ PDF uploaded! Document ID: ' || l_new_id);
  DBMS_LOB.FREETEMPORARY(l_blob);
  COMMIT;
END;
/
*/

-- ================================================================
-- STEP 3: Vectorize a Document
-- ================================================================
PROMPT
PROMPT Step 3: Vectorizing document...
PROMPT (Update l_doc_id to match your document)
PROMPT

-- Get the last document ID
DECLARE
  l_doc_id NUMBER;
BEGIN
  SELECT MAX(id) INTO l_doc_id FROM my_books;
  DBMS_OUTPUT.PUT_LINE('Will vectorize document ID: ' || l_doc_id);
END;
/

-- Vectorize it
DECLARE
  l_doc_id      NUMBER;
  l_chunk_size  NUMBER := 1000;
  l_overlap     NUMBER := 200;
  l_doc         BLOB;
  l_text        CLOB;
  l_chunk       VARCHAR2(4000);
  l_embed_vector VECTOR;
  l_chunk_id    NUMBER := 1;
  l_pos         NUMBER := 1;
  l_text_len    NUMBER;
BEGIN
  -- Get last document
  SELECT MAX(id) INTO l_doc_id FROM my_books;
  
  IF l_doc_id IS NULL THEN
    DBMS_OUTPUT.PUT_LINE('No documents found. Upload a PDF first.');
    RETURN;
  END IF;
  
  -- Check if already vectorized
  DECLARE
    l_exists NUMBER;
  BEGIN
    SELECT COUNT(*) INTO l_exists 
    FROM vector_store 
    WHERE doc_id = l_doc_id;
    
    IF l_exists > 0 THEN
      DBMS_OUTPUT.PUT_LINE('Document ' || l_doc_id || ' already vectorized (' || l_exists || ' chunks)');
      DBMS_OUTPUT.PUT_LINE('Skipping... (delete from vector_store first to re-process)');
      RETURN;
    END IF;
  END;
  
  -- Get PDF content
  SELECT file_content INTO l_doc FROM my_books WHERE id = l_doc_id;
  
  -- Extract text
  DBMS_OUTPUT.PUT_LINE('Extracting text from PDF...');
  l_text := DBMS_VECTOR_CHAIN.UTL_TO_TEXT(l_doc);
  l_text_len := LENGTH(l_text);
  DBMS_OUTPUT.PUT_LINE('✓ Extracted ' || l_text_len || ' characters');
  
  -- Chunk and vectorize
  DBMS_OUTPUT.PUT_LINE('Creating vector embeddings...');
  
  WHILE l_pos <= l_text_len LOOP
    l_chunk := SUBSTR(l_text, l_pos, l_chunk_size);
    
    IF TRIM(l_chunk) IS NOT NULL THEN
      SELECT TO_VECTOR(VECTOR_EMBEDDING(TINYBERT_MODEL USING l_chunk AS data))
      INTO l_embed_vector
      FROM DUAL;
      
      INSERT INTO vector_store (doc_id, embed_id, embed_data, embed_vector)
      VALUES (l_doc_id, l_chunk_id, l_chunk, l_embed_vector);
      
      IF MOD(l_chunk_id, 10) = 0 THEN
        DBMS_OUTPUT.PUT_LINE('  Processed ' || l_chunk_id || ' chunks...');
      END IF;
      
      l_chunk_id := l_chunk_id + 1;
    END IF;
    
    l_pos := l_pos + l_chunk_size - l_overlap;
  END LOOP;
  
  COMMIT;
  
  DBMS_OUTPUT.PUT_LINE('✓ Vectorization complete! Created ' || (l_chunk_id - 1) || ' chunks');
END;
/

-- ================================================================
-- STEP 4: Test Vector Search
-- ================================================================
PROMPT
PROMPT Step 4: Testing vector search...
PROMPT

-- Find similar chunks to a query
DECLARE
  l_query        VARCHAR2(500) := 'What is artificial intelligence?';
  l_query_vector VECTOR;
  l_doc_id       NUMBER;
BEGIN
  SELECT MAX(doc_id) INTO l_doc_id FROM vector_store;
  
  IF l_doc_id IS NULL THEN
    DBMS_OUTPUT.PUT_LINE('No vectorized documents found.');
    RETURN;
  END IF;
  
  DBMS_OUTPUT.PUT_LINE('Query: ' || l_query);
  DBMS_OUTPUT.PUT_LINE('Searching document ' || l_doc_id || '...');
  DBMS_OUTPUT.PUT_LINE('');
  
  SELECT TO_VECTOR(VECTOR_EMBEDDING(TINYBERT_MODEL USING l_query AS data))
  INTO l_query_vector
  FROM DUAL;
  
  FOR rec IN (
    SELECT doc_id,
           embed_id,
           ROUND(VECTOR_DISTANCE(embed_vector, l_query_vector, COSINE), 4) as distance,
           SUBSTR(embed_data, 1, 150) || '...' as chunk_text
    FROM vector_store
    WHERE doc_id = l_doc_id
    ORDER BY VECTOR_DISTANCE(embed_vector, l_query_vector, COSINE)
    FETCH FIRST 3 ROWS ONLY
  ) LOOP
    DBMS_OUTPUT.PUT_LINE('Match ' || rec.embed_id || ' (distance: ' || rec.distance || '):');
    DBMS_OUTPUT.PUT_LINE('  ' || rec.chunk_text);
    DBMS_OUTPUT.PUT_LINE('');
  END LOOP;
END;
/

-- ================================================================
-- STEP 5: Test RAG with Ollama
-- ================================================================
PROMPT
PROMPT Step 5: Testing RAG query with Ollama...
PROMPT (Make sure Ollama URL is configured in generate_text_response2)
PROMPT

DECLARE
  l_question VARCHAR2(500) := 'What are the main topics in this document?';
  l_response CLOB;
  l_doc_id   NUMBER;
BEGIN
  SELECT MAX(doc_id) INTO l_doc_id FROM vector_store;
  
  IF l_doc_id IS NULL THEN
    DBMS_OUTPUT.PUT_LINE('No vectorized documents found.');
    RETURN;
  END IF;
  
  DBMS_OUTPUT.PUT_LINE('Question: ' || l_question);
  DBMS_OUTPUT.PUT_LINE('Querying document ' || l_doc_id || '...');
  DBMS_OUTPUT.PUT_LINE('');
  
  -- Call RAG function
  l_response := generate_text_response2(l_question, l_doc_id, 5);
  
  DBMS_OUTPUT.PUT_LINE('Response:');
  DBMS_OUTPUT.PUT_LINE('----------------------------------------');
  DBMS_OUTPUT.PUT_LINE(l_response);
  DBMS_OUTPUT.PUT_LINE('----------------------------------------');
  
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('Error: ' || SQLERRM);
    DBMS_OUTPUT.PUT_LINE('');
    DBMS_OUTPUT.PUT_LINE('If error mentions network/connection:');
    DBMS_OUTPUT.PUT_LINE('  1. Update Ollama URL in generate_text_response2 function');
    DBMS_OUTPUT.PUT_LINE('  2. Ensure Ollama server is running and accessible');
    DBMS_OUTPUT.PUT_LINE('  3. Check network ACL permissions');
END;
/

-- ================================================================
-- STEP 6: View Statistics
-- ================================================================
PROMPT
PROMPT ================================================================
PROMPT Final Statistics
PROMPT ================================================================

SELECT 'Total Documents' as metric, COUNT(*) as value FROM my_books
UNION ALL
SELECT 'Total Chunks', COUNT(*) FROM vector_store
UNION ALL
SELECT 'Vectorized Docs', COUNT(DISTINCT doc_id) FROM vector_store;

PROMPT
PROMPT Per-Document Breakdown:
SELECT b.id as doc_id,
       b.file_name,
       b.file_size,
       COUNT(v.embed_id) as num_chunks
FROM my_books b
LEFT JOIN vector_store v ON b.id = v.doc_id
GROUP BY b.id, b.file_name, b.file_size
ORDER BY b.id;

PROMPT
PROMPT ================================================================
PROMPT Workflow Complete!
PROMPT ================================================================
PROMPT
PROMPT Next steps:
PROMPT 1. Upload more PDFs using Method 1 in 10_upload_and_vectorize_pdf.sql
PROMPT 2. Batch process them using Method 4 in that same script
PROMPT 3. Test queries using generate_text_response2()
PROMPT 4. Customize chunk size and overlap for your use case
PROMPT 5. Update Ollama server URL if needed
PROMPT ================================================================
