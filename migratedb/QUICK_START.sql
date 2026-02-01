-- ================================================================
-- QUICK START: Data Pump Full Migration
-- ================================================================
-- This is the fastest and most reliable method to migrate
-- the VECTOR schema from demo vector to aiholodb
-- ================================================================

/*
================================================================================
RECOMMENDED APPROACH: Full Data Pump Export/Import
================================================================================

This approach migrates everything in one go:
  ✓ Tables with all data (including BLOBs)
  ✓ ML Models (TINYBERT_MODEL, ALL_MINILM_L6V2MODEL)
  ✓ Functions and Procedures
  ✓ Sequences, Indexes, Constraints

================================================================================
PREREQUISITES:
================================================================================
1. Create VECTOR user on target (see step 1 below)
2. Ensure DATA_PUMP_DIR exists and has permissions
3. Sufficient disk space for dump file (~size of data + 20%)

================================================================================
QUICK START STEPS:
================================================================================

STEP 1: Create User on Target Database
---------------------------------------
Run as ADMIN on aiholodb:
*/

-- Connect as ADMIN to aiholodb first!
-- Then run: @C:\migratedb\01_create_user.sql

/*
STEP 2: Export from Source Database
------------------------------------
Run this from command line (or SQL Developer):
*/

-- Windows PowerShell:
-- expdp vector/password@demo_vector directory=DATA_PUMP_DIR dumpfile=vector_full.dmp logfile=vector_full_export.log schemas=VECTOR

-- This exports:
--   - All tables with data
--   - All functions and procedures
--   - All ML models
--   - All indexes and constraints

/*
STEP 3: Copy Dump File to Target Server
----------------------------------------
Copy the dump file from source to target:
*/

-- If same server: No action needed
-- If different servers:
--   scp vector_full.dmp user@target_server:/path/to/data_pump_dir/
--   OR use file share, USB drive, etc.

/*
STEP 4: Import to Target Database
----------------------------------
Run this from command line (or SQL Developer):
*/

-- Windows PowerShell:
-- impdp vector/Welcome12345*@aiholodb directory=DATA_PUMP_DIR dumpfile=vector_full.dmp logfile=vector_full_import.log remap_schema=VECTOR:VECTOR table_exists_action=REPLACE

-- The remap_schema is optional since source and target user are both VECTOR
-- But it's good practice to be explicit

/*
STEP 5: Update Ollama Server URLs
----------------------------------
After import, update the Ollama server URL in functions:
*/

-- Connect as VECTOR to aiholodb:
-- sqlcl vector/Welcome12345*@aiholodb

-- Update GENERATE_TEXT_RESPONSE2:
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

  -- *** UPDATE THIS URL TO YOUR NEW OLLAMA SERVER ***
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
END;
/

-- Update OLLAMA_TEST_FUNCTION similarly

/*
STEP 6: Verify Migration
-------------------------
*/

-- Run verification script:
-- @C:\migratedb\07_verify.sql

/*
STEP 7: Test the Function
--------------------------
*/

-- Test query:
SELECT generate_text_response2('What is vector search?', 1, 3) AS response 
FROM dual;

/*
================================================================================
ALTERNATIVE: Selective Migration (Tables Only, No Models)
================================================================================
If you already have ML models on target or want to import them separately:
*/

-- Export only tables:
-- expdp vector/password@demo_vector directory=DATA_PUMP_DIR dumpfile=vector_tables.dmp logfile=vector_tables_export.log tables=MY_BOOKS,DEMO_BOOKS,VECTOR_STORE

-- Import:
-- impdp vector/Welcome12345*@aiholodb directory=DATA_PUMP_DIR dumpfile=vector_tables.dmp logfile=vector_tables_import.log

-- Then create functions manually using 04_create_functions.sql

/*
================================================================================
DATA PUMP DIRECTORY SETUP
================================================================================
If DATA_PUMP_DIR doesn't exist, create it as DBA:
*/

-- As DBA/ADMIN:
CREATE OR REPLACE DIRECTORY data_pump_dir AS 'C:\oracle\data_pump';
GRANT READ, WRITE ON DIRECTORY data_pump_dir TO vector;

-- Verify:
SELECT directory_name, directory_path FROM dba_directories WHERE directory_name = 'DATA_PUMP_DIR';

/*
================================================================================
ESTIMATED TIME:
================================================================================
Export:  2-5 minutes (depending on data size)
Copy:    Varies (same server = instant)
Import:  5-10 minutes
Update:  2 minutes
Verify:  1 minute
---------
Total:   ~10-20 minutes

================================================================================
*/

PROMPT
PROMPT ================================================================
PROMPT Quick Start script ready!
PROMPT
PROMPT Use Data Pump for fastest, most reliable migration:
PROMPT   1. Run: @01_create_user.sql (as ADMIN on aiholodb)
PROMPT   2. Run: expdp command (on demo vector)
PROMPT   3. Copy dump file to target
PROMPT   4. Run: impdp command (on aiholodb)
PROMPT   5. Update Ollama URLs in functions
PROMPT   6. Run: @07_verify.sql
PROMPT   7. Test!
PROMPT ================================================================
