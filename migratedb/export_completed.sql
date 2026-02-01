-- ================================================================
-- EXPORT COMPLETED SUCCESSFULLY!
-- ================================================================
-- Export Details:
--   Source: demo vector database
--   Schema: VECTOR
--   Dump file: vector_full.dmp
--   Log file: vector_full_export.log
--   Location: /u01/app/oracle/admin/orcl/dpdump/3439A922B4626531E0637E0B00632F3A
--
-- Contents exported:
--   - All tables with data (MY_BOOKS, DEMO_BOOKS, VECTOR_STORE, etc.)
--   - ML models (TINYBERT_MODEL, ALL_MINILM_L6V2MODEL)
--   - Functions (GENERATE_TEXT_RESPONSE2, etc.)
--   - Sequences, indexes, constraints
-- ================================================================

-- ================================================================
-- NEXT STEP: Import to aiholodb
-- ================================================================

-- Run this PL/SQL block as ADMIN on aiholodb to import:

DECLARE
  l_dp_handle   NUMBER;
  l_status      VARCHAR2(30);
  l_job_state   VARCHAR2(30);
BEGIN
  -- Create a Data Pump import job
  l_dp_handle := DBMS_DATAPUMP.OPEN(
    operation => 'IMPORT',
    job_mode  => 'SCHEMA',
    job_name  => 'VECTOR_FULL_IMPORT'
  );
  
  -- Specify the dump file
  DBMS_DATAPUMP.ADD_FILE(
    handle    => l_dp_handle,
    filename  => 'vector_full.dmp',
    directory => 'DATA_PUMP_DIR',
    filetype  => DBMS_DATAPUMP.KU$_FILE_TYPE_DUMP_FILE
  );
  
  -- Specify the log file
  DBMS_DATAPUMP.ADD_FILE(
    handle    => l_dp_handle,
    filename  => 'vector_full_import.log',
    directory => 'DATA_PUMP_DIR',
    filetype  => DBMS_DATAPUMP.KU$_FILE_TYPE_LOG_FILE
  );
  
  -- Remap schema (VECTOR to VECTOR)
  DBMS_DATAPUMP.METADATA_REMAP(
    handle    => l_dp_handle,
    name      => 'REMAP_SCHEMA',
    old_value => 'VECTOR',
    value     => 'VECTOR'
  );
  
  -- Import data only (since tables already exist)
  DBMS_DATAPUMP.SET_PARAMETER(
    handle => l_dp_handle,
    name   => 'CONTENT',
    value  => 'DATA_ONLY'
  );
  
  -- Replace existing data
  DBMS_DATAPUMP.SET_PARAMETER(
    handle => l_dp_handle,
    name   => 'TABLE_EXISTS_ACTION',
    value  => 'TRUNCATE'
  );
  
  -- Start the import job
  DBMS_DATAPUMP.START_JOB(l_dp_handle);
  
  -- Wait for the job to complete
  DBMS_DATAPUMP.WAIT_FOR_JOB(l_dp_handle, l_job_state);
  
  -- Display job status
  DBMS_OUTPUT.PUT_LINE('Import job completed with status: ' || l_job_state);
  
  -- Detach from the job
  DBMS_DATAPUMP.DETACH(l_dp_handle);
  
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('Error during import: ' || SQLERRM);
    IF l_dp_handle IS NOT NULL THEN
      DBMS_DATAPUMP.DETACH(l_dp_handle);
    END IF;
    RAISE;
END;
/

-- ================================================================
-- IMPORTANT NOTES:
-- ================================================================
-- 1. The dump file must be copied to the DATA_PUMP_DIR on aiholodb
--    server before running the import
--
-- 2. Check the DATA_PUMP_DIR location on aiholodb:
--    SELECT directory_path FROM all_directories 
--    WHERE directory_name = 'DATA_PUMP_DIR';
--
-- 3. Copy vector_full.dmp from:
--    Source: /u01/app/oracle/admin/orcl/dpdump/3439A922B4626531E0637E0B00632F3A/
--    To: [aiholodb DATA_PUMP_DIR path]/vector_full.dmp
--
-- 4. If both databases are on the same server, you might be able to
--    use the same dump file directly!
--
-- 5. After import, update Ollama URLs in functions (see NEXT_STEPS.md)
-- ================================================================
