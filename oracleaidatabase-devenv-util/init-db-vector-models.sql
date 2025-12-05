-- Initialize Oracle Database for VECTOR_EMBEDDING()
-- This script imports ONNX models into the database for native embedding generation

-- Check if we're running Oracle 26ai or later with VECTOR support
DECLARE
  v_version VARCHAR2(100);
  v_major NUMBER;
BEGIN
  SELECT version INTO v_version FROM v$instance;
  v_major := TO_NUMBER(SUBSTR(v_version, 1, INSTR(v_version, '.') - 1));
  
  IF v_major < 23 THEN
    RAISE_APPLICATION_ERROR(-20001, 'Oracle 26ai+ required for VECTOR_EMBEDDING(). Current version: ' || v_version);
  END IF;
  
  DBMS_OUTPUT.PUT_LINE('Oracle version: ' || v_version || ' - VECTOR support available');
END;
/

-- Import sentence-transformers/all-MiniLM-L12-v2 ONNX model
-- This is a 384-dimension text embedding model compatible with Oracle VECTOR_EMBEDDING()
BEGIN
  -- Check if model already exists
  DECLARE
    v_count NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_count 
    FROM user_mining_models 
    WHERE model_name = 'ALL_MINILM_L12_V2';
    
    IF v_count > 0 THEN
      DBMS_OUTPUT.PUT_LINE('Model ALL_MINILM_L12_V2 already exists');
      RETURN;
    END IF;
  EXCEPTION
    WHEN OTHERS THEN NULL;
  END;
  
  -- Import the ONNX model from a URL or file
  -- Note: This requires the model file to be accessible to the database
  -- Option 1: Load from URL (if database has network access)
  -- DBMS_VECTOR.LOAD_ONNX_MODEL(
  --   model_name => 'ALL_MINILM_L12_V2',
  --   model_source => '                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    '
  -- );
  
  -- Option 2: Load from Oracle directory object
  -- First create directory: CREATE DIRECTORY MODEL_DIR AS '/models';
  -- DBMS_VECTOR.LOAD_ONNX_MODEL(
  --   model_name => 'ALL_MINILM_L12_V2',
  --   model_source => 'MODEL_DIR',
  --   model_file => 'all-MiniLM-L12-v2.onnx'
  -- );
  
  DBMS_OUTPUT.PUT_LINE('To import ONNX model, uncomment appropriate method above');
  DBMS_OUTPUT.PUT_LINE('See Oracle documentation: DBMS_VECTOR.LOAD_ONNX_MODEL');
END;
/

-- Verify the model is loaded
SELECT model_name, mining_function, algorithm, build_duration 
FROM user_mining_models 
WHERE model_name LIKE '%MINILM%';

-- Test VECTOR_EMBEDDING() function
DECLARE
  v_embedding VECTOR;
BEGIN
  -- This will fail if model is not loaded
  v_embedding := VECTOR_EMBEDDING(all_MiniLM_L12_v2 USING 'test query' as data);
  DBMS_OUTPUT.PUT_LINE('VECTOR_EMBEDDING() test successful. Dimension: ' || v_embedding.get_length());
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('VECTOR_EMBEDDING() not available. Error: ' || SQLERRM);
    DBMS_OUTPUT.PUT_LINE('Use oracle_hybrid mode instead (Python ONNX + Oracle storage)');
END;
/
