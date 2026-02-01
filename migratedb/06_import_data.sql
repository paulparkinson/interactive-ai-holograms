-- ================================================================
-- Step 6: Import Data to Target Database
-- ================================================================
-- Run this script as VECTOR user on TARGET database (aiholodb)
-- ================================================================

SET FEEDBACK ON
SET ECHO ON

PROMPT
PROMPT ================================================================
PROMPT Importing data to VECTOR schema on aiholodb...
PROMPT ================================================================

-- Option 1: If you used Data Pump, skip this script
-- and use the impdp command shown in 05_export_data.sql

-- Option 2: Manual import from exported data files

PROMPT
PROMPT Manual import process:
PROMPT ================================================================
PROMPT
PROMPT 1. For VECTOR_STORE (from CSV):
PROMPT    Use SQL*Loader or manual INSERT statements
PROMPT
PROMPT 2. For MY_BOOKS and DEMO_BOOKS with BLOB data:
PROMPT    Use Data Pump or DBMS_CLOUD.COPY_DATA if files are in object storage
PROMPT
PROMPT Example: Import from CSV (update file path):
PROMPT ================================================================

-- Example template for SQL*Loader control file
-- Create file: vector_store.ctl
/*
LOAD DATA
INFILE 'C:\migratedb\data\vector_store_data.csv'
INTO TABLE vector_store
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
TRAILING NULLCOLS
(
  doc_id,
  embed_id "DECODE(:embed_id,'NULL',NULL,:embed_id)",
  embed_data,
  embed_vector "TO_VECTOR(:embed_vector)"
)
*/

-- Then run: sqlldr vector/Welcome12345*@aiholodb control=vector_store.ctl

PROMPT
PROMPT ================================================================
PROMPT Alternative: Direct SQL Insert (Small Datasets)
PROMPT ================================================================
PROMPT
PROMPT If you have small amount of data, you can use direct INSERT:
PROMPT
PROMPT Example:
PROMPT   INSERT INTO vector_store (doc_id, embed_id, embed_data, embed_vector)
PROMPT   VALUES (1, 1, 'sample text', TO_VECTOR('[0.1, 0.2, 0.3, ...]'));
PROMPT
PROMPT For large datasets (1951 rows), use Data Pump or SQL*Loader
PROMPT
PROMPT ================================================================
PROMPT Verifying current data counts:
PROMPT ================================================================

SELECT 'MY_BOOKS' as table_name, COUNT(*) as row_count FROM my_books
UNION ALL
SELECT 'DEMO_BOOKS', COUNT(*) FROM demo_books
UNION ALL
SELECT 'VECTOR_STORE', COUNT(*) FROM vector_store;

PROMPT
PROMPT ================================================================
PROMPT Expected counts from source:
PROMPT   MY_BOOKS: 12 rows
PROMPT   DEMO_BOOKS: 1 row
PROMPT   VECTOR_STORE: 1951 rows
PROMPT ================================================================
PROMPT
PROMPT Next step: Verify migration (07_verify.sql)
PROMPT ================================================================
