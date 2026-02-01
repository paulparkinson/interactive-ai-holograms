-- ================================================================
-- Step 4: Create Functions and Procedures
-- ================================================================
-- Run this script as VECTOR user on the target database
-- 
-- IMPORTANT: Update the Ollama server URL before running!
-- Current URL in functions: http://10.10.51.56:11434/api/generate
-- Update to your new Ollama server URL
-- ================================================================

PROMPT Creating GENERATE_TEXT_RESPONSE2 function...

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

  /* *** UPDATE THIS URL TO YOUR NEW OLLAMA SERVER *** */
  l_params := JSON(
      '{'||
      '"provider":"ollama",'||
      '"url":"http://YOUR_NEW_OLLAMA_SERVER:11434/api/generate",'||
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

PROMPT GENERATE_TEXT_RESPONSE2 function created.

PROMPT Creating GENERATE_TEXT_RESPONSE_GEN function...

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
  
  -- Open the cursor using the provided query string
  search_query := 'SELECT embed_data FROM vector_store WHERE doc_id = ' || doc_id ||
                  ' ORDER BY VECTOR_DISTANCE(embed_vector, :user_question_vec, COSINE)' ||
                  ' FETCH FIRST 10 ROWS ONLY';
  OPEN message_cursor FOR search_query USING user_question_vec;

  -- Initialize messages CLOB
  messages := '';

  -- Loop through cursor results and construct messages
  LOOP
    FETCH message_cursor INTO message_line;
    EXIT WHEN message_cursor%NOTFOUND;
    messages := messages || '{"message": "' || message_line || '"},' || CHR(10);
  END LOOP;
  
  -- Finally pass the user question
  messages := messages || '{"Question": "' || user_question  || '"},' || CHR(10);
  
  CLOSE message_cursor;

  -- Remove the trailing comma and newline character
  messages := RTRIM(messages, ',' || CHR(10));

  -- Construct params JSON for OCI GenAI
  -- NOTE: Requires pre-created credentials (GENAI_CRED)
  params_genai := '{
    "provider": "ocigenai",
    "credential_name": "GENAI_CRED",
    "url": "https://inference.generativeai.us-chicago-1.oci.oraclecloud.com/20231130/actions/chat",
    "model": "cohere.command-r-08-2024",
    "chatRequest": {
      "maxTokens": 300
    }
  }';

  DBMS_OUTPUT.PUT_LINE(TO_CHAR(user_question_vec));

  -- Call UTL function to generate text
  output := DBMS_VECTOR_CHAIN.UTL_TO_GENERATE_TEXT(messages, JSON(params_genai));
  DBMS_OUTPUT.PUT_LINE(output);
  
  RETURN output;

EXCEPTION
  WHEN OTHERS THEN
    RETURN SQLERRM || ' - ' || SQLCODE;
END generate_text_response_gen;
/

SHOW ERRORS

PROMPT GENERATE_TEXT_RESPONSE_GEN function created.

PROMPT Creating OLLAMA_TEST_FUNCTION...

CREATE OR REPLACE FUNCTION ollama_test_function (
    p_question IN VARCHAR2
) RETURN CLOB
IS
  l_url    VARCHAR2(200) := 'http://YOUR_NEW_OLLAMA_SERVER:11434/api/generate';
  l_model  VARCHAR2(50)  := 'llama3.2:3b';
  l_params JSON;
  l_out    CLOB;
BEGIN
  -- *** UPDATE l_url TO YOUR NEW OLLAMA SERVER ***
  
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

PROMPT OLLAMA_TEST_FUNCTION created.

-- Verify all functions created
PROMPT
PROMPT ================================================================
PROMPT Verifying functions...
PROMPT ================================================================

SELECT object_name, object_type, status
FROM user_objects
WHERE object_type IN ('FUNCTION', 'PROCEDURE')
ORDER BY object_type, object_name;

PROMPT
PROMPT ================================================================
PROMPT Functions created successfully!
PROMPT ================================================================
PROMPT
PROMPT IMPORTANT: Update Ollama server URLs in the functions!
PROMPT   - GENERATE_TEXT_RESPONSE2: Line 42
PROMPT   - OLLAMA_TEST_FUNCTION: Line 145
PROMPT
PROMPT Next step: Export data from source (05_export_data.sql)
PROMPT ================================================================
