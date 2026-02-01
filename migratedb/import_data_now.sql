-- ================================================================
-- IMPORT DATA TO AIHOLODB
-- ================================================================
-- Prerequisites:
--   1. vector_full.dmp must be in aiholodb's DATA_PUMP_DIR:
--      /u03/dbfs/408C52C6BE845088E06381D5000AFE35/data/dpdump/
--   2. Run this as ADMIN on aiholodb
-- ================================================================

-- Verify the dump file is accessible
DECLARE
  v_exists BOOLEAN;
BEGIN
  v_exists := DBMS_CLOUD.FILE_EXISTS(
    directory_name => 'DATA_PUMP_DIR',
    file_name      => 'vector_full.dmp'
  );
  
  IF v_exists THEN
    DBMS_OUTPUT.PUT_LINE('✓ Dump file found! Proceeding with import...');
  ELSE
    DBMS_OUTPUT.PUT_LINE('✗ ERROR: vector_full.dmp not found in DATA_PUMP_DIR');
    DBMS_OUTPUT.PUT_LINE('   Copy the file first before running this script.');
    RAISE_APPLICATION_ERROR(-20001, 'Dump file not found');
  END IF;
END;
/

-- Run the import
DECLARE
  l_dp_handle   NUMBER;
  l_job_state   VARCHAR2(30);
BEGIN
  DBMS_OUTPUT.PUT_LINE('Starting import job...');
  
  -- Create import job
  l_dp_handle := DBMS_DATAPUMP.OPEN(
    operation => 'IMPORT',
    job_mode  => 'SCHEMA',
    job_name  => 'VECTOR_IMPORT_' || TO_CHAR(SYSDATE, 'YYYYMMDDHH24MISS')
  );
  
  -- Add dump file
  DBMS_DATAPUMP.ADD_FILE(
    handle    => l_dp_handle,
    filename  => 'vector_full.dmp',
    directory => 'DATA_PUMP_DIR',
    filetype  => DBMS_DATAPUMP.KU$_FILE_TYPE_DUMP_FILE
  );
  
  -- Add log file
  DBMS_DATAPUMP.ADD_FILE(
    handle    => l_dp_handle,
    filename  => 'vector_import_' || TO_CHAR(SYSDATE, 'YYYYMMDDHH24MISS') || '.log',
    directory => 'DATA_PUMP_DIR',
    filetype  => DBMS_DATAPUMP.KU$_FILE_TYPE_LOG_FILE
  );
  
  -- Import data only (tables already exist)
  DBMS_DATAPUMP.SET_PARAMETER(
    handle => l_dp_handle,
    name   => 'CONTENT',
    value  => 'DATA_ONLY'
  );
  
  -- Truncate and reload existing tables
  DBMS_DATAPUMP.SET_PARAMETER(
    handle => l_dp_handle,
    name   => 'TABLE_EXISTS_ACTION',
    value  => 'TRUNCATE'
  );
  
  -- Filter to VECTOR schema
  DBMS_DATAPUMP.METADATA_FILTER(
    handle => l_dp_handle,
    name   => 'SCHEMA_EXPR',
    value  => 'IN (''VECTOR'')'
  );
  
  DBMS_OUTPUT.PUT_LINE('Starting import... This may take several minutes.');
  
  -- Start the job
  DBMS_DATAPUMP.START_JOB(l_dp_handle);
  
  -- Wait for completion
  DBMS_DATAPUMP.WAIT_FOR_JOB(l_dp_handle, l_job_state);
  
  DBMS_OUTPUT.PUT_LINE('Import completed with status: ' || l_job_state);
  
  IF l_job_state = 'COMPLETED' THEN
    DBMS_OUTPUT.PUT_LINE('✓ SUCCESS! Data imported successfully.');
  ELSIF l_job_state = 'COMPLETED WITH ERRORS' THEN
    DBMS_OUTPUT.PUT_LINE('⚠ Import completed with errors. Check the log file.');
  ELSE
    DBMS_OUTPUT.PUT_LINE('✗ Import failed. Status: ' || l_job_state);
  END IF;
  
  -- Detach from job
  DBMS_DATAPUMP.DETACH(l_dp_handle);
  
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('ERROR: ' || SQLERRM);
    IF l_dp_handle IS NOT NULL THEN
      BEGIN
        DBMS_DATAPUMP.DETACH(l_dp_handle);
      EXCEPTION WHEN OTHERS THEN NULL;
      END;
    END IF;
    RAISE;
END;
/

-- Verify the import
PROMPT
PROMPT ================================================================
PROMPT Verifying imported data...
PROMPT ================================================================

SELECT 'MY_BOOKS' as table_name, COUNT(*) as row_count, 12 as expected
FROM vector.my_books
UNION ALL
SELECT 'DEMO_BOOKS', COUNT(*), 1 FROM vector.demo_books
UNION ALL
SELECT 'VECTOR_STORE', COUNT(*), 1951 FROM vector.vector_store;

PROMPT
PROMPT Check ML models:
SELECT model_name, mining_function, algorithm 
FROM dba_mining_models 
WHERE owner = 'VECTOR'
ORDER BY model_name;

PROMPT
PROMPT ================================================================
PROMPT If row counts match and models are present, import succeeded!
PROMPT
PROMPT Next step: Update Ollama URLs in functions
PROMPT See: C:\migratedb\NEXT_STEPS.md
PROMPT ================================================================
