-- ================================================================
-- Step 7: Verification Script
-- ================================================================
-- Run this script as VECTOR user on TARGET database (aiholodb)
-- to verify successful migration
-- ================================================================

SET LINESIZE 200
SET PAGESIZE 100

PROMPT
PROMPT ================================================================
PROMPT VECTOR SCHEMA MIGRATION VERIFICATION
PROMPT ================================================================
PROMPT

-- 1. Check User
PROMPT 1. User Information:
PROMPT ================================================================
SELECT username, 
       account_status, 
       default_tablespace,
       created
FROM user_users;

PROMPT
PROMPT 2. Privileges:
PROMPT ================================================================
SELECT privilege 
FROM user_sys_privs 
WHERE privilege LIKE '%VECTOR%' 
   OR privilege LIKE '%MINING%'
ORDER BY privilege;

PROMPT
PROMPT 3. Tables:
PROMPT ================================================================
SELECT table_name, 
       num_rows,
       last_analyzed
FROM user_tables
WHERE table_name IN ('MY_BOOKS', 'DEMO_BOOKS', 'VECTOR_STORE')
ORDER BY table_name;

PROMPT
PROMPT 4. Table Row Counts:
PROMPT ================================================================
SELECT 'MY_BOOKS' as table_name, COUNT(*) as actual_count, 12 as expected_count 
FROM my_books
UNION ALL
SELECT 'DEMO_BOOKS', COUNT(*), 1 FROM demo_books
UNION ALL
SELECT 'VECTOR_STORE', COUNT(*), 1951 FROM vector_store;

PROMPT
PROMPT 5. ML Models:
PROMPT ================================================================
SELECT model_name, 
       mining_function, 
       algorithm,
       creation_date
FROM user_mining_models
ORDER BY model_name;

PROMPT Expected models:
PROMPT   - TINYBERT_MODEL (EMBEDDING, ONNX)
PROMPT   - ALL_MINILM_L6V2MODEL (EMBEDDING, ONNX)

PROMPT
PROMPT 6. Functions and Procedures:
PROMPT ================================================================
SELECT object_name, 
       object_type, 
       status,
       last_ddl_time
FROM user_objects
WHERE object_type IN ('FUNCTION', 'PROCEDURE')
ORDER BY object_type, object_name;

PROMPT Expected objects:
PROMPT   - GENERATE_TEXT_RESPONSE2 (FUNCTION)
PROMPT   - GENERATE_TEXT_RESPONSE_GEN (FUNCTION)
PROMPT   - OLLAMA_TEST_FUNCTION (FUNCTION)

PROMPT
PROMPT 7. Constraints:
PROMPT ================================================================
SELECT constraint_name, 
       constraint_type,
       table_name,
       status
FROM user_constraints
WHERE table_name IN ('MY_BOOKS', 'DEMO_BOOKS', 'VECTOR_STORE')
ORDER BY table_name, constraint_type;

PROMPT
PROMPT 8. Indexes:
PROMPT ================================================================
SELECT index_name, 
       table_name,
       uniqueness,
       status
FROM user_indexes
WHERE table_name IN ('MY_BOOKS', 'DEMO_BOOKS', 'VECTOR_STORE')
ORDER BY table_name;

PROMPT
PROMPT ================================================================
PROMPT 9. Test Vector Search Query:
PROMPT ================================================================
PROMPT Testing vector similarity search...

-- Test if TINYBERT_MODEL works
SELECT COUNT(*) as vector_search_test
FROM (
  SELECT embed_data
  FROM vector_store
  WHERE doc_id = (SELECT MIN(doc_id) FROM vector_store)
  ORDER BY VECTOR_DISTANCE(
    embed_vector,
    (SELECT TO_VECTOR(VECTOR_EMBEDDING(TINYBERT_MODEL USING 'test' AS data)) FROM DUAL),
    COSINE
  )
  FETCH FIRST 3 ROWS ONLY
);

PROMPT If count = 3, vector search is working!

PROMPT
PROMPT ================================================================
PROMPT 10. Test GENERATE_TEXT_RESPONSE2 Function:
PROMPT ================================================================
PROMPT Testing function with sample data...
PROMPT NOTE: This will fail if:
PROMPT   - Ollama server URL is not updated
PROMPT   - Ollama server is not accessible
PROMPT   - No data in tables
PROMPT

-- Only test if data exists
DECLARE
  v_result CLOB;
  v_doc_id NUMBER;
BEGIN
  -- Get first doc_id
  SELECT MIN(doc_id) INTO v_doc_id FROM vector_store;
  
  IF v_doc_id IS NOT NULL THEN
    DBMS_OUTPUT.PUT_LINE('Testing with doc_id: ' || v_doc_id);
    
    -- Test function (will fail if Ollama URL not updated)
    BEGIN
      v_result := generate_text_response2('What is vector search?', v_doc_id, 3);
      DBMS_OUTPUT.PUT_LINE('Function test result: ' || SUBSTR(v_result, 1, 200));
    EXCEPTION
      WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('Function test failed: ' || SQLERRM);
        DBMS_OUTPUT.PUT_LINE('Update Ollama server URL in function!');
    END;
  ELSE
    DBMS_OUTPUT.PUT_LINE('No data in VECTOR_STORE - skipping function test');
  END IF;
END;
/

PROMPT
PROMPT ================================================================
PROMPT VERIFICATION CHECKLIST:
PROMPT ================================================================
PROMPT [ ] User VECTOR exists and is OPEN
PROMPT [ ] All 3 tables exist (MY_BOOKS, DEMO_BOOKS, VECTOR_STORE)
PROMPT [ ] Row counts match expected (12, 1, 1951)
PROMPT [ ] Both ML models are present (TINYBERT_MODEL, ALL_MINILM_L6V2MODEL)
PROMPT [ ] All 3 functions are VALID
PROMPT [ ] Vector search query works
PROMPT [ ] Constraints and indexes are in place
PROMPT [ ] Ollama server URL updated in functions
PROMPT [ ] Function test passes
PROMPT ================================================================
PROMPT
PROMPT If all items are checked, migration is complete!
PROMPT
PROMPT To use the migrated schema:
PROMPT   SELECT generate_text_response2('Your question?', 1, 3) 
PROMPT   FROM dual;
PROMPT ================================================================
