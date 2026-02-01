-- ================================================================
-- Step 5: Export Data from Source Database
-- ================================================================
-- Run this script on the SOURCE database (demo vector)
-- This will create CSV/SQL files to import on target
-- ================================================================

-- Set output formatting
SET LINESIZE 32767
SET PAGESIZE 0
SET TRIMSPOOL ON
SET FEEDBACK OFF
SET HEADING OFF
SET ECHO OFF

PROMPT
PROMPT ================================================================
PROMPT Exporting data from VECTOR schema...
PROMPT ================================================================

-- Note: For BLOB data (file_content), we'll use a different approach
-- This script generates row counts and sample exports

PROMPT
PROMPT MY_BOOKS table - Row count:
SELECT COUNT(*) FROM my_books;

PROMPT
PROMPT DEMO_BOOKS table - Row count:
SELECT COUNT(*) FROM demo_books;

PROMPT
PROMPT VECTOR_STORE table - Row count:
SELECT COUNT(*) FROM vector_store;

PROMPT
PROMPT ================================================================
PROMPT DATA EXPORT OPTIONS:
PROMPT ================================================================
PROMPT
PROMPT Option 1: Use Data Pump (Recommended for large data/BLOBs)
PROMPT ----------------------------------------------------------
PROMPT 
PROMPT expdp vector/password@demo_vector \
PROMPT   directory=DATA_PUMP_DIR \
PROMPT   dumpfile=vector_data.dmp \
PROMPT   logfile=vector_data_export.log \
PROMPT   tables=MY_BOOKS,DEMO_BOOKS,VECTOR_STORE \
PROMPT   exclude=STATISTICS
PROMPT
PROMPT Then on target:
PROMPT 
PROMPT impdp vector/Welcome12345*@aiholodb \
PROMPT   directory=DATA_PUMP_DIR \
PROMPT   dumpfile=vector_data.dmp \
PROMPT   logfile=vector_data_import.log \
PROMPT   table_exists_action=TRUNCATE
PROMPT
PROMPT ================================================================
PROMPT
PROMPT Option 2: Export to SQL*Loader format (for VECTOR_STORE)
PROMPT ----------------------------------------------------------
PROMPT For VECTOR_STORE (no BLOBs), you can export to CSV:
PROMPT
PROMPT

-- Generate CSV export for VECTOR_STORE
SPOOL C:\migratedb\data\vector_store_data.csv

SELECT doc_id || ',' || 
       NVL(embed_id, 'NULL') || ',' || 
       '"' || REPLACE(embed_data, '"', '""') || '",' ||
       '"' || TO_CHAR(embed_vector) || '"'
FROM vector_store
ORDER BY doc_id, embed_id;

SPOOL OFF

PROMPT
PROMPT VECTOR_STORE data exported to: C:\migratedb\data\vector_store_data.csv
PROMPT

-- For MY_BOOKS and DEMO_BOOKS with BLOBs, show structure
PROMPT
PROMPT MY_BOOKS sample data (excluding BLOB):
PROMPT

SELECT id, file_name, file_size, file_type, 
       CASE WHEN file_content IS NOT NULL THEN '[BLOB DATA]' ELSE 'NULL' END as file_content,
       created_on
FROM my_books
ORDER BY id;

PROMPT
PROMPT ================================================================
PROMPT Export Summary:
PROMPT ================================================================
PROMPT 1. VECTOR_STORE exported to CSV (C:\migratedb\data\vector_store_data.csv)
PROMPT 2. For MY_BOOKS and DEMO_BOOKS with BLOBs, use Data Pump (Option 1)
PROMPT 3. Alternative: Export books as separate files and re-import
PROMPT ================================================================
PROMPT
PROMPT Next step: Import data on target (06_import_data.sql)
PROMPT ================================================================

SET FEEDBACK ON
SET HEADING ON
