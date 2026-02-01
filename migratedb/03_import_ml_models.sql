-- ================================================================
-- Step 3: Import ML Models (ONNX Models)
-- ================================================================
-- This script provides instructions for importing the ML models
-- The actual model files need to be exported from source and imported
-- ================================================================

/*
IMPORTANT: Oracle ML models cannot be easily scripted via DDL.
You need to use one of these methods:

METHOD 1: Export/Import ML Models Using Data Pump
--------------------------------------------------
On SOURCE database (demo vector):

1. Export the ML models:
   expdp vector/password@demo_vector \
     directory=DATA_PUMP_DIR \
     dumpfile=ml_models.dmp \
     logfile=ml_models_export.log \
     schemas=VECTOR \
     include=MODEL

2. Copy ml_models.dmp to target server

On TARGET database (aiholodb):

3. Import the ML models:
   impdp vector/Welcome12345*@aiholodb \
     directory=DATA_PUMP_DIR \
     dumpfile=ml_models.dmp \
     logfile=ml_models_import.log \
     remap_schema=VECTOR:VECTOR


METHOD 2: Re-Import ONNX Models from Original Files
----------------------------------------------------
If you have the original ONNX model files:

1. TINYBERT_MODEL:
*/

-- Template for importing TINYBERT_MODEL (update with your ONNX file path)
BEGIN
  DBMS_DATA_MINING.DROP_MODEL('TINYBERT_MODEL', force => TRUE);
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

/*
-- Example (update paths):
EXECUTE DBMS_VECTOR.LOAD_ONNX_MODEL(
  'YOUR_DIRECTORY',
  'tinybert_model.onnx',
  'TINYBERT_MODEL'
);
*/

/*
2. ALL_MINILM_L6V2MODEL:

-- Example (update paths):
EXECUTE DBMS_VECTOR.LOAD_ONNX_MODEL(
  'YOUR_DIRECTORY',
  'all-MiniLM-L6-v2.onnx',
  'ALL_MINILM_L6V2MODEL'
);
*/


-- METHOD 3: Extract and Re-Import
-- --------------------------------
-- On source database, get model metadata:
-- SELECT model_name, mining_function, algorithm, model_size 
-- FROM user_mining_models;

-- Verify Models After Import
-- ---------------------------

PROMPT
PROMPT Verifying ML models...
PROMPT

SELECT model_name, 
       mining_function, 
       algorithm,
       creation_date,
       model_size
FROM user_mining_models
ORDER BY model_name;

PROMPT
PROMPT ================================================================
PROMPT Required Models:
PROMPT   1. TINYBERT_MODEL (EMBEDDING, ONNX)
PROMPT   2. ALL_MINILM_L6V2MODEL (EMBEDDING, ONNX)
PROMPT ================================================================
PROMPT
PROMPT If models are not showing above, you need to import them using
PROMPT one of the methods described in this script.
PROMPT ================================================================
PROMPT
PROMPT Next step: Run 04_create_functions.sql
PROMPT ================================================================
