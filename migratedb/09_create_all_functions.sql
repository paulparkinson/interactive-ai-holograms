-- ================================================================
-- Step 4: Create All Functions and Procedures
-- ================================================================
-- Run this as VECTOR user on aiholodb
-- 
-- IMPORTANT: Update the Ollama server URL before running!
-- ================================================================

-- ================================================================
-- 1. PROCEDURE: INSERT_MY_TABLE_ROW
-- ================================================================
-- Inserts a file into MY_BOOKS table
-- Returns the new ID or existing ID if duplicate
-- ================================================================

CREATE OR REPLACE PROCEDURE insert_my_table_row (
    p_file_name    IN VARCHAR2,
    p_file_size    IN NUMBER,
    p_file_type    IN VARCHAR2,
    p_file_content IN BLOB,
    p_new_id       OUT NUMBER
) AUTHID DEFINER
IS
BEGIN
  INSERT INTO my_books (file_name, file_size, file_type, file_content)
  VALUES (p_file_name, p_file_size, p_file_type, p_file_content)
  RETURNING id INTO p_new_id;

EXCEPTION
  WHEN dup_val_on_index THEN
    SELECT id INTO p_new_id
    FROM   my_books
    WHERE  file_name = p_file_name
    AND    file_size = p_file_size;
END insert_my_table_row;
/

SHOW ERRORS

-- ================================================================
-- 2. FUNCTION: GENERATE_TEXT_RESPONSE2
-- ================================================================
-- Main RAG function - retrieves relevant chunks and generates answer
-- using Ollama
-- ================================================================

CREATE OR REPLACE FUNCTION generate_text_response2 (
    user_question VARCHAR2,
    doc_id        NUMBER,
    topn          NUMBER
) RETURN CLOB
IS
    l_msgs     CLOB := 'Use the snippets below to answer the question. Keep it concise.'||CHR(10);
    l_params   JSON;
    l_out      CLOB;
    l_line     VARCHAR2(32767);
    l_embed_id NUMBER;
    l_cur      SYS_REFCURSOR;
BEGIN
  /* Retrieve the most relevant chunks for the question */
  OPEN l_cur FOR
    'WITH a AS (
       SELECT TO_VECTOR(VECTOR_EMBEDDING(TINYBERT_MODEL USING :1 AS data)) AS embed
       FROM dual
     )
     SELECT embed_data, embed_id
       FROM vector_store, a
      WHERE doc_id = :2
      ORDER BY VECTOR_DISTANCE(embed_vector, a.embed, COSINE)
      FETCH FIRST :3 ROWS ONLY'
    USING user_question, doc_id, topn;

  LOOP
    FETCH l_cur INTO l_line, l_embed_id;
    EXIT WHEN l_cur%NOTFOUND;
    l_line := REGEXP_REPLACE(l_line, '[[:cntrl:]]', ' ');
    l_msgs := l_msgs || '- ' || l_line || CHR(10);
  END LOOP;
  CLOSE l_cur;

  l_msgs := l_msgs || CHR(10) || 'Question: ' || user_question;

  /* *** UPDATE THIS URL TO YOUR OLLAMA SERVER *** */
  l_params := JSON(
      '{'||
      '"provider":"ollama",'||
      '"url":"http://YOUR_OLLAMA_SERVER:11434/api/generate",'||
      '"model":"llama3.2:3b",'||
      '"stream":false'||
      '}'
  );

  l_out := DBMS_VECTOR_CHAIN.UTL_TO_GENERATE_TEXT(l_msgs, l_params);
  RETURN l_out;

EXCEPTION
  WHEN OTHERS THEN
    RETURN SQLERRM||' - '||SQLCODE;
END generate_text_response2;
/

SHOW ERRORS

-- ================================================================
-- 3. FUNCTION: GENERATE_TEXT_RESPONSE_GEN
-- ================================================================
-- Alternative function using OCI GenAI instead of Ollama
-- Requires GENAI_CRED credential to be created
-- ================================================================

CREATE OR REPLACE FUNCTION generate_text_response_gen (
    user_question VARCHAR2, 
    doc_id        NUMBER
) RETURN CLOB 
IS
  messages         CLOB;
  params_genai     CLOB;
  output           CLOB;
  message_line     VARCHAR2(4000);
  message_cursor   SYS_REFCURSOR;
  user_question_vec VECTOR;
  search_query     VARCHAR2(4000);
BEGIN
  -- Vectorize the user_question
  SELECT TO_VECTOR(VECTOR_EMBEDDING(TINYBERT_MODEL USING user_question AS data)) 
  INTO user_question_vec
  FROM DUAL;
  
  -- Build search query
  search_query := 'SELECT embed_data FROM vector_store WHERE doc_id = ' || doc_id ||
                  ' ORDER BY VECTOR_DISTANCE(embed_vector, :user_question_vec, COSINE)' ||
                  ' FETCH FIRST 10 ROWS ONLY';
  OPEN message_cursor FOR search_query USING user_question_vec;

  messages := '';

  LOOP
    FETCH message_cursor INTO message_line;
    EXIT WHEN message_cursor%NOTFOUND;
    messages := messages || '{"message": "' || message_line || '"},' || CHR(10);
  END LOOP;
  
  messages := messages || '{"Question": "' || user_question  || '"},' || CHR(10);
  CLOSE message_cursor;

  messages := RTRIM(messages, ',' || CHR(10));

  -- OCI GenAI parameters (requires credential setup)
  params_genai := '{
    "provider": "ocigenai",
    "credential_name": "GENAI_CRED",
    "url": "https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/20231130/actions/chat",
    "model": "cohere.command-r-08-2024",
    "chatRequest": {
      "maxTokens": 300
    }
  }';

  output := DBMS_VECTOR_CHAIN.UTL_TO_GENERATE_TEXT(messages, JSON(params_genai));
  RETURN output;

EXCEPTION
  WHEN OTHERS THEN
    RETURN SQLERRM || ' - ' || SQLCODE;
END generate_text_response_gen;
/

SHOW ERRORS

-- ================================================================
-- 4. FUNCTION: OLLAMA_TEST_FUNCTION
-- ================================================================
-- Simple test function for Ollama connectivity
-- ================================================================

CREATE OR REPLACE FUNCTION ollama_test_function (
    p_question IN VARCHAR2
) RETURN CLOB
IS
  l_url    VARCHAR2(200) := 'http://YOUR_OLLAMA_SERVER:11434/api/generate';
  l_model  VARCHAR2(50)  := 'llama3.2:3b';
  l_params JSON;
  l_out    CLOB;
BEGIN
  -- *** UPDATE l_url TO YOUR OLLAMA SERVER ***
  
  l_params := JSON(
      '{'
    || '"provider":"ollama",'
    || '"url":"'   || l_url   || '",'
    || '"model":"' || l_model || '"'
    || '}'
  );

  l_out := DBMS_VECTOR_CHAIN.UTL_TO_GENERATE_TEXT(
    '## Answer this question: ' || p_question,
    l_params
  );

  RETURN l_out;
END ollama_test_function;
/

SHOW ERRORS

-- ================================================================
-- Verify All Objects Created
-- ================================================================
PROMPT
PROMPT ================================================================
PROMPT Verifying created objects...
PROMPT ================================================================

SELECT object_name, 
       object_type, 
       status,
       TO_CHAR(last_ddl_time, 'YYYY-MM-DD HH24:MI:SS') as last_modified
FROM user_objects
WHERE object_type IN ('FUNCTION', 'PROCEDURE')
ORDER BY object_type, object_name;

PROMPT
PROMPT Expected objects:
PROMPT   - INSERT_MY_TABLE_ROW (PROCEDURE)
PROMPT   - GENERATE_TEXT_RESPONSE2 (FUNCTION)
PROMPT   - GENERATE_TEXT_RESPONSE_GEN (FUNCTION)
PROMPT   - OLLAMA_TEST_FUNCTION (FUNCTION)
PROMPT
PROMPT ================================================================
PROMPT IMPORTANT: Update Ollama URLs before testing!
PROMPT ================================================================
PROMPT
PROMPT In GENERATE_TEXT_RESPONSE2 and OLLAMA_TEST_FUNCTION:
PROMPT   Change: http://YOUR_OLLAMA_SERVER:11434/api/generate
PROMPT   To your actual Ollama server URL
PROMPT
PROMPT Then test with:
PROMPT   SELECT ollama_test_function('What is AI?') FROM dual;
PROMPT
PROMPT ================================================================
PROMPT Next step: Upload and vectorize PDFs (10_upload_and_vectorize_pdf.sql)
PROMPT ================================================================
