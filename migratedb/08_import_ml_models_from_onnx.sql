-- ================================================================
-- Step 3: Import ML Models from ONNX Files
-- ================================================================
-- Run this as VECTOR user on aiholodb
--
-- PREREQUISITES:
-- 1. Download ONNX files from demo vector server:
--    - tinybert.onnx (17.7 MB)
--    - all-MiniLM-L6-v2.onnx (90.6 MB)
--    Location: /u01/app/oracle/admin/orcl/dpdump/3439A922B4626531E0637E0B00632F3A/
--
-- 2. Upload them to aiholodb's DATA_PUMP_DIR or create a custom directory
-- ================================================================

-- Option A: Create a directory for ONNX files (as ADMIN)
-- CREATE OR REPLACE DIRECTORY ONNX_MODELS AS 'C:\onnx_models';
-- GRANT READ ON DIRECTORY ONNX_MODELS TO vector;

-- Option B: Use existing DATA_PUMP_DIR
-- Just upload the files to: /u03/dbfs/408C52C6BE845088E06381D5000AFE35/data/dpdump/

-- ================================================================
-- Check which directories are available
-- ================================================================
SELECT directory_name, directory_path 
FROM all_directories 
ORDER BY directory_name;

-- ================================================================
-- Import TINYBERT_MODEL
-- ================================================================
PROMPT
PROMPT Importing TINYBERT_MODEL...
PROMPT

BEGIN
  -- Drop if exists
  BEGIN
    DBMS_DATA_MINING.DROP_MODEL('TINYBERT_MODEL', force => TRUE);
  EXCEPTION 
    WHEN OTHERS THEN NULL;
  END;
  
  -- Import the ONNX model
  -- UPDATE 'DATA_PUMP_DIR' and filename if you uploaded elsewhere
  DBMS_VECTOR.LOAD_ONNX_MODEL(
    directory    => 'DATA_PUMP_DIR',
    file_name    => 'tinybert.onnx',
    model_name   => 'TINYBERT_MODEL',
    metadata     => JSON('{"function" : "embedding", "embeddingOutput" : "embedding"}')
  );
  
  DBMS_OUTPUT.PUT_LINE('✓ TINYBERT_MODEL imported successfully');
END;
/

-- ================================================================
-- Import ALL_MINILM_L6V2MODEL
-- ================================================================
PROMPT
PROMPT Importing ALL_MINILM_L6V2MODEL...
PROMPT

BEGIN
  -- Drop if exists
  BEGIN
    DBMS_DATA_MINING.DROP_MODEL('ALL_MINILM_L6V2MODEL', force => TRUE);
  EXCEPTION 
    WHEN OTHERS THEN NULL;
  END;
  
  -- Import the ONNX model
  DBMS_VECTOR.LOAD_ONNX_MODEL(
    directory    => 'DATA_PUMP_DIR',
    file_name    => 'all-MiniLM-L6-v2.onnx',
    model_name   => 'ALL_MINILM_L6V2MODEL',
    metadata     => JSON('{"function" : "embedding", "embeddingOutput" : "embedding"}')
  );
  
  DBMS_OUTPUT.PUT_LINE('✓ ALL_MINILM_L6V2MODEL imported successfully');
END;
/

-- ================================================================
-- Verify Models
-- ================================================================
PROMPT
PROMPT ================================================================
PROMPT Verifying ML Models...
PROMPT ================================================================

SELECT model_name, 
       mining_function, 
       algorithm,
       model_size,
       creation_date
FROM user_mining_models
ORDER BY model_name;

-- Expected output:
--   ALL_MINILM_L6V2MODEL    EMBEDDING    ONNX
--   TINYBERT_MODEL          EMBEDDING    ONNX

-- ================================================================
-- Test the models
-- ================================================================
PROMPT
PROMPT Testing TINYBERT_MODEL...

SELECT TO_CHAR(VECTOR_EMBEDDING(TINYBERT_MODEL USING 'Hello World' AS data)) as embedding_sample
FROM dual;

PROMPT
PROMPT ================================================================
PROMPT Models imported and tested successfully!
PROMPT ================================================================
PROMPT
PROMPT Next step: Create functions and procedures (09_create_all_functions.sql)
PROMPT ================================================================

-- ================================================================
-- TROUBLESHOOTING
-- ================================================================
/*
Error: ORA-40284: model does not exist
Solution: Model wasn't imported. Check the file path and directory

Error: ORA-22288: file or LOB operation FILEOPEN failed
Solution: ONNX file not found in specified directory
  - Verify file exists: SELECT * FROM TABLE(DBMS_CLOUD.LIST_FILES('DATA_PUMP_DIR'));
  - Check permissions: GRANT READ ON DIRECTORY TO vector;

Error: ORA-40733: invalid ONNX model
Solution: ONNX file is corrupted or wrong version
  - Re-download from source
  - Verify file size matches original

How to get ONNX files from demo vector:
  1. Connect to demo vector server via SSH/RDP
  2. Navigate to: /u01/app/oracle/admin/orcl/dpdump/3439A922B4626531E0637E0B00632F3A/
  3. Copy files:
     - tinybert.onnx
     - all-MiniLM-L6-v2.onnx
  4. Upload to aiholodb server
*/
