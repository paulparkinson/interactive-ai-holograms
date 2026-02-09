CREATE OR REPLACE FUNCTION generate_text_response_west (
    user_question  VARCHAR2,
    doc_id         NUMBER,
    topn           NUMBER
) RETURN CLOB
IS
    l_msgs      CLOB := 'Use the snippets below to answer the question. Keep it concise.' || CHR(10);
    l_params    JSON;
    l_out       CLOB;
    l_line      VARCHAR2(32767);
    l_embed_id  NUMBER;
    l_cur       SYS_REFCURSOR;
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

    /* Call the remote Ollama server (10.6.161.145) with llama3.2:3b */
    l_params := JSON(
        '{' ||
        '"provider":"ollama",' ||
        '"url":"http://10.6.161.145:11434/api/generate",' ||
        '"model":"llama3.2:3b",' ||
        '"stream":false' ||
        '}'
    );

    l_out := DBMS_VECTOR_CHAIN.UTL_TO_GENERATE_TEXT(l_msgs, l_params);
    RETURN l_out;

EXCEPTION
    WHEN OTHERS THEN
        RETURN SQLERRM || ' - ' || SQLCODE;
END generate_text_response_west;
/
