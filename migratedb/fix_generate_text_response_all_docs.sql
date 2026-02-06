-- Fix and recompile generate_text_response_all_docs with working Ollama URL

CREATE OR REPLACE FUNCTION generate_text_response_all_docs (
    user_question VARCHAR2,
    topn          NUMBER
) RETURN CLOB
IS
    l_msgs     CLOB := 'Use the snippets below to answer the question. Keep it concise.'||CHR(10);
    l_params   JSON;
    l_out      CLOB;
    l_result   CLOB;

    l_line     VARCHAR2(32767);
    l_embed_id NUMBER;
    l_doc_id   NUMBER;
    l_doc_ids  VARCHAR2(4000) := '';
    l_cur      SYS_REFCURSOR;
BEGIN
  OPEN l_cur FOR
    'WITH a AS (
       SELECT TO_VECTOR(VECTOR_EMBEDDING(TINYBERT_MODEL USING :1 AS data)) AS embed
       FROM dual
     )
     SELECT embed_data, embed_id, doc_id
       FROM vector_store, a
      ORDER BY VECTOR_DISTANCE(embed_vector, a.embed, COSINE)
      FETCH FIRST :2 ROWS ONLY'
    USING user_question, topn;

  LOOP
    FETCH l_cur INTO l_line, l_embed_id, l_doc_id;
    EXIT WHEN l_cur%NOTFOUND;
    l_line := REGEXP_REPLACE(l_line, '[[:cntrl:]]', ' ');
    l_msgs := l_msgs || '- ' || l_line || CHR(10);

    IF INSTR(l_doc_ids, ',' || l_doc_id || ',') = 0 THEN
      l_doc_ids := l_doc_ids || l_doc_id || ',';
    END IF;
  END LOOP;
  CLOSE l_cur;

  l_msgs := l_msgs || CHR(10) || 'Question: ' || user_question;

  -- Use working Ollama server IP (.56 not .55)
  l_params := JSON(
      '{'||
      '"provider":"ollama",'||
      '"url":"http://10.10.51.56:11434/api/generate",'||
      '"model":"llama3.2:3b",'||
      '"stream":false'||
      '}'
  );

  l_out := DBMS_VECTOR_CHAIN.UTL_TO_GENERATE_TEXT(l_msgs, l_params);

  l_doc_ids := RTRIM(l_doc_ids, ',');

  l_result := '{"response":' || l_out || ',"doc_ids":[' || l_doc_ids || ']}';
  RETURN l_result;

EXCEPTION
  WHEN OTHERS THEN
    RETURN '{"error":"' || SQLERRM || '","sqlcode":' || SQLCODE || '}';
END generate_text_response_all_docs;
/

SHOW ERRORS

-- Test it
SELECT generate_text_response_all_docs('How does Oracle help DOD?', 3) AS response FROM dual;
