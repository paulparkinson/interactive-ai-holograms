-- ================================================================
-- MASTER MIGRATION SCRIPT
-- ================================================================
-- This script provides the complete step-by-step migration process
-- Update connection strings and paths as needed
-- ================================================================

/*
================================================================================
ORACLE VECTOR SCHEMA MIGRATION - MASTER SCRIPT
================================================================================

SOURCE DATABASE: demo vector
TARGET DATABASE: aiholodb
TARGET USER: VECTOR
PASSWORD: Welcome12345*

MIGRATION CONTENTS:
  - Tables: MY_BOOKS (12 rows), DEMO_BOOKS (1 row), VECTOR_STORE (1951 rows)
  - ML Models: TINYBERT_MODEL, ALL_MINILM_L6V2MODEL
  - Functions: GENERATE_TEXT_RESPONSE2, GENERATE_TEXT_RESPONSE_GEN, OLLAMA_TEST_FUNCTION

================================================================================
PRE-REQUISITES:
================================================================================
1. Oracle 23c+ database with AI Vector Search enabled
2. Admin access to target database (aiholodb)
3. Data Pump directory configured
4. ONNX model files (if not using Data Pump for models)
5. New Ollama server URL

================================================================================
STEP-BY-STEP EXECUTION:
================================================================================

STEP 1: CREATE USER ON TARGET (Run as ADMIN)
---------------------------------------------
Connect to aiholodb as ADMIN:
  sqlcl admin/password@aiholodb
  @C:\migratedb\01_create_user.sql

STEP 2: CREATE TABLES (Run as VECTOR)
--------------------------------------
Connect to aiholodb as VECTOR:
  sqlcl vector/Welcome12345*@aiholodb
  @C:\migratedb\02_create_tables.sql

STEP 3: IMPORT ML MODELS
-------------------------
Choose one method:

Method A - Data Pump (Recommended):
  On SOURCE (demo vector):
    expdp vector/password@demo_vector 
      directory=DATA_PUMP_DIR 
      dumpfile=ml_models.dmp 
      logfile=ml_models_export.log 
      schemas=VECTOR 
      include=MODEL

  Copy ml_models.dmp to target server

  On TARGET (aiholodb):
    impdp vector/Welcome12345*@aiholodb 
      directory=DATA_PUMP_DIR 
      dumpfile=ml_models.dmp 
      logfile=ml_models_import.log

Method B - ONNX Files:
  See 03_import_ml_models.sql for details

STEP 4: CREATE FUNCTIONS (Run as VECTOR)
-----------------------------------------
*** IMPORTANT: Edit 04_create_functions.sql first! ***
*** Update Ollama server URLs in the functions ***

  sqlcl vector/Welcome12345*@aiholodb
  @C:\migratedb\04_create_functions.sql

STEP 5: EXPORT DATA FROM SOURCE
--------------------------------
Use Data Pump for complete migration (includes BLOBs):

  expdp vector/password@demo_vector 
    directory=DATA_PUMP_DIR 
    dumpfile=vector_data.dmp 
    logfile=vector_data_export.log 
    tables=MY_BOOKS,DEMO_BOOKS,VECTOR_STORE

Copy vector_data.dmp to target server

STEP 6: IMPORT DATA TO TARGET
------------------------------
  impdp vector/Welcome12345*@aiholodb 
    directory=DATA_PUMP_DIR 
    dumpfile=vector_data.dmp 
    logfile=vector_data_import.log 
    table_exists_action=TRUNCATE

STEP 7: VERIFY MIGRATION
-------------------------
  sqlcl vector/Welcome12345*@aiholodb
  @C:\migratedb\07_verify.sql

STEP 8: TEST THE FUNCTION
--------------------------
  SELECT generate_text_response2('What is vector search?', 1, 3) AS response 
  FROM dual;

================================================================================
COMPLETE DATA PUMP MIGRATION (All-in-One)
================================================================================
If you want to migrate EVERYTHING at once (models + data):

EXPORT (on source):
-------------------
expdp vector/password@demo_vector 
  directory=DATA_PUMP_DIR 
  dumpfile=vector_full.dmp 
  logfile=vector_full_export.log 
  schemas=VECTOR

IMPORT (on target):
-------------------
First create user (Step 1), then:

impdp vector/Welcome12345*@aiholodb 
  directory=DATA_PUMP_DIR 
  dumpfile=vector_full.dmp 
  logfile=vector_full_import.log 
  remap_schema=VECTOR:VECTOR 
  exclude=USER

Then run verification (Step 7) and update Ollama URLs

================================================================================
TROUBLESHOOTING:
================================================================================

Issue: Models not importing
  - Ensure source and target are both Oracle 23c+
  - Try Method B (ONNX files) from Step 3

Issue: Function compilation errors
  - Check ML models are imported first
  - Verify DBMS_VECTOR_CHAIN package is available
  - Check network ACL for Ollama server access

Issue: Data import fails
  - Verify tablespaces exist (USERS, DATA)
  - Check quotas are unlimited
  - Import MY_BOOKS before VECTOR_STORE (FK dependency)

Issue: generate_text_response2 returns error
  - Update Ollama server URL in function
  - Verify Ollama server is accessible from target database
  - Check network ACL permissions

================================================================================
POST-MIGRATION TASKS:
================================================================================
1. Update Ollama server URL in all functions
2. Test vector search queries
3. Verify data integrity (row counts)
4. Update application connection strings
5. Create additional database users/roles if needed
6. Configure backups for new schema

================================================================================
REUSE FOR OTHER DATABASES:
================================================================================
This package can be reused for migrating to other databases.
Just update:
  - Connection strings
  - Ollama server URL (in 04_create_functions.sql)
  - Target password (in 01_create_user.sql)
  - Data Pump directory paths

================================================================================
*/

PROMPT
PROMPT ================================================================
PROMPT Migration package ready!
PROMPT Review this master script and follow the steps above.
PROMPT ================================================================
PROMPT
PROMPT All migration scripts are in: C:\migratedb\
PROMPT ================================================================
