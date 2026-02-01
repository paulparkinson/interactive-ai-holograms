-- ================================================================
-- Step 5: Upload and Vectorize PDF Documents
-- ================================================================
-- This script shows you how to:
-- 1. Upload a PDF file to MY_BOOKS table
-- 2. Extract and chunk the text from the PDF
-- 3. Generate vector embeddings for each chunk
-- 4. Store chunks in VECTOR_STORE table for RAG queries
-- ================================================================

-- ================================================================
-- METHOD 1: Upload PDF from File System
-- ================================================================
-- Requires: PDF file accessible via Oracle Directory
-- ================================================================

-- Step 1: Create a directory object (as ADMIN)
-- CREATE OR REPLACE DIRECTORY PDF_FILES AS 'C:\pdfs';
-- GRANT READ ON DIRECTORY PDF_FILES TO vector;

-- Step 2: Upload PDF to MY_BOOKS
DECLARE
  l_bfile  BFILE;
  l_blob   BLOB;
  l_dest_offset NUMBER := 1;
  l_src_offset  NUMBER := 1;
  l_file_size   NUMBER;
  l_new_id      NUMBER;
BEGIN
  -- Open the PDF file from directory
  l_bfile := BFILENAME('PDF_FILES', 'my_document.pdf');  -- UPDATE filename
  
  -- Get file size
  DBMS_LOB.FILEOPEN(l_bfile, DBMS_LOB.FILE_READONLY);
  l_file_size := DBMS_LOB.GETLENGTH(l_bfile);
  
  -- Create temporary BLOB
  DBMS_LOB.CREATETEMPORARY(l_blob, TRUE);
  
  -- Load file into BLOB
  DBMS_LOB.LOADBLOBFROMFILE(
    dest_lob    => l_blob,
    src_bfile   => l_bfile,
    amount      => l_file_size,
    dest_offset => l_dest_offset,
    src_offset  => l_src_offset
  );
  
  DBMS_LOB.FILECLOSE(l_bfile);
  
  -- Insert into MY_BOOKS
  insert_my_table_row(
    p_file_name    => 'my_document.pdf',  -- UPDATE filename
    p_file_size    => l_file_size,
    p_file_type    => 'application/pdf',
    p_file_content => l_blob,
    p_new_id       => l_new_id
  );
  
  DBMS_OUTPUT.PUT_LINE('PDF uploaded successfully! Document ID: ' || l_new_id);
  
  -- Clean up
  DBMS_LOB.FREETEMPORARY(l_blob);
  
  COMMIT;
END;
/

-- ================================================================
-- METHOD 2: Upload PDF via SQL Developer / APEX
-- ================================================================
-- You can also use SQL Developer's "Load File" feature:
-- 1. Right-click on MY_BOOKS table
-- 2. Select "Import Data"
-- 3. Choose your PDF file
-- 4. Map columns appropriately
-- ================================================================

-- ================================================================
-- METHOD 3: Vectorize Existing PDF in MY_BOOKS
-- ================================================================
-- This is the key step: Extract text, chunk it, and create embeddings
-- ================================================================

DECLARE
  l_doc_id      NUMBER := 1;  -- UPDATE to your document ID
  l_chunk_size  NUMBER := 1000; -- Characters per chunk
  l_overlap     NUMBER := 200;   -- Overlap between chunks
  
  l_doc         BLOB;
  l_text        CLOB;
  l_chunk       VARCHAR2(4000);
  l_embed_vector VECTOR;
  l_chunk_id    NUMBER := 1;
  l_pos         NUMBER := 1;
  l_text_len    NUMBER;
BEGIN
  -- Get the PDF content
  SELECT file_content 
  INTO l_doc 
  FROM my_books 
  WHERE id = l_doc_id;
  
  -- Extract text from PDF using DBMS_VECTOR_CHAIN
  -- This uses Oracle's built-in PDF text extraction
  l_text := DBMS_VECTOR_CHAIN.UTL_TO_TEXT(l_doc);
  
  l_text_len := LENGTH(l_text);
  DBMS_OUTPUT.PUT_LINE('Extracted ' || l_text_len || ' characters from PDF');
  
  -- Delete existing chunks for this document (if re-processing)
  DELETE FROM vector_store WHERE doc_id = l_doc_id;
  
  -- Chunk the text and create embeddings
  WHILE l_pos <= l_text_len LOOP
    -- Extract chunk
    l_chunk := SUBSTR(l_text, l_pos, l_chunk_size);
    
    -- Skip empty chunks
    IF TRIM(l_chunk) IS NOT NULL THEN
      -- Generate vector embedding using TINYBERT_MODEL
      SELECT TO_VECTOR(VECTOR_EMBEDDING(TINYBERT_MODEL USING l_chunk AS data))
      INTO l_embed_vector
      FROM DUAL;
      
      -- Store chunk and its embedding
      INSERT INTO vector_store (doc_id, embed_id, embed_data, embed_vector)
      VALUES (l_doc_id, l_chunk_id, l_chunk, l_embed_vector);
      
      DBMS_OUTPUT.PUT_LINE('Chunk ' || l_chunk_id || ' processed');
      l_chunk_id := l_chunk_id + 1;
    END IF;
    
    -- Move to next chunk (with overlap)
    l_pos := l_pos + l_chunk_size - l_overlap;
  END LOOP;
  
  COMMIT;
  
  DBMS_OUTPUT.PUT_LINE('✓ Vectorization complete! Created ' || (l_chunk_id - 1) || ' chunks');
END;
/

-- ================================================================
-- Verify Vectorization
-- ================================================================
SELECT doc_id,
       COUNT(*) as num_chunks,
       MIN(embed_id) as first_chunk,
       MAX(embed_id) as last_chunk
FROM vector_store
GROUP BY doc_id
ORDER BY doc_id;

-- View sample chunks
SELECT doc_id, 
       embed_id, 
       SUBSTR(embed_data, 1, 100) || '...' as chunk_sample
FROM vector_store
WHERE doc_id = 1  -- UPDATE to your doc_id
ORDER BY embed_id
FETCH FIRST 5 ROWS ONLY;

-- ================================================================
-- METHOD 4: Batch Process Multiple PDFs
-- ================================================================
-- Process all PDFs in MY_BOOKS that haven't been vectorized yet
-- ================================================================

DECLARE
  l_chunk_size  NUMBER := 1000;
  l_overlap     NUMBER := 200;
  l_text        CLOB;
  l_chunk       VARCHAR2(4000);
  l_embed_vector VECTOR;
  l_chunk_id    NUMBER;
  l_pos         NUMBER;
  l_text_len    NUMBER;
  l_count       NUMBER := 0;
BEGIN
  -- Loop through all books
  FOR book IN (
    SELECT id, file_name, file_content
    FROM my_books
    WHERE NOT EXISTS (
      SELECT 1 FROM vector_store WHERE doc_id = my_books.id
    )
  ) LOOP
    
    DBMS_OUTPUT.PUT_LINE('Processing: ' || book.file_name);
    
    -- Extract text
    l_text := DBMS_VECTOR_CHAIN.UTL_TO_TEXT(book.file_content);
    l_text_len := LENGTH(l_text);
    l_pos := 1;
    l_chunk_id := 1;
    
    -- Chunk and vectorize
    WHILE l_pos <= l_text_len LOOP
      l_chunk := SUBSTR(l_text, l_pos, l_chunk_size);
      
      IF TRIM(l_chunk) IS NOT NULL THEN
        SELECT TO_VECTOR(VECTOR_EMBEDDING(TINYBERT_MODEL USING l_chunk AS data))
        INTO l_embed_vector
        FROM DUAL;
        
        INSERT INTO vector_store (doc_id, embed_id, embed_data, embed_vector)
        VALUES (book.id, l_chunk_id, l_chunk, l_embed_vector);
        
        l_chunk_id := l_chunk_id + 1;
      END IF;
      
      l_pos := l_pos + l_chunk_size - l_overlap;
    END LOOP;
    
    l_count := l_count + 1;
    DBMS_OUTPUT.PUT_LINE('✓ ' || book.file_name || ' - ' || (l_chunk_id - 1) || ' chunks');
  END LOOP;
  
  COMMIT;
  DBMS_OUTPUT.PUT_LINE('Processed ' || l_count || ' documents');
END;
/

-- ================================================================
-- Test RAG Query
-- ================================================================
-- Now test your vectorized documents!
-- ================================================================

-- Simple test: Find similar chunks to a query
DECLARE
  l_query VARCHAR2(500) := 'What is vector search?';
  l_query_vector VECTOR;
BEGIN
  -- Vectorize the query
  SELECT TO_VECTOR(VECTOR_EMBEDDING(TINYBERT_MODEL USING l_query AS data))
  INTO l_query_vector
  FROM DUAL;
  
  -- Find most similar chunks
  FOR rec IN (
    SELECT doc_id,
           embed_id,
           VECTOR_DISTANCE(embed_vector, l_query_vector, COSINE) as distance,
           SUBSTR(embed_data, 1, 200) || '...' as chunk_text
    FROM vector_store
    ORDER BY VECTOR_DISTANCE(embed_vector, l_query_vector, COSINE)
    FETCH FIRST 5 ROWS ONLY
  ) LOOP
    DBMS_OUTPUT.PUT_LINE('Doc ' || rec.doc_id || ', Chunk ' || rec.embed_id || 
                         ', Distance: ' || ROUND(rec.distance, 4));
    DBMS_OUTPUT.PUT_LINE('  ' || rec.chunk_text);
    DBMS_OUTPUT.PUT_LINE('---');
  END LOOP;
END;
/

-- Full RAG test with Ollama
SELECT generate_text_response2('What is vector search?', 1, 3) AS response 
FROM dual;

-- ================================================================
-- SUMMARY
-- ================================================================
/*
Complete Workflow:
1. Upload PDF to MY_BOOKS (METHOD 1, 2, or 3 above)
2. Vectorize the PDF (METHOD 3 or 4 above)
3. Query using generate_text_response2()

Tips:
- Chunk size of 1000 chars works well for most documents
- 200 char overlap helps maintain context across chunks
- Use TINYBERT_MODEL for faster processing
- Use ALL_MINILM_L6V2MODEL for better accuracy (slower)

Next Steps:
- Upload your own PDFs
- Adjust chunk size based on your documents
- Customize the RAG functions for your use case
- Set up automated processing for new documents
*/
