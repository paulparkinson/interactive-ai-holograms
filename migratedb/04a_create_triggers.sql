-- ================================================================
-- Step 4a: Create Triggers
-- ================================================================
-- Run this script as VECTOR user on the target database
-- 
-- INSTRUCTIONS:
-- 1. First, on your SOURCE database, find the trigger name:
--
--    SELECT trigger_name, status, trigger_type, triggering_event, table_name
--    FROM user_triggers
--    WHERE table_name = 'MY_BOOKS';
--
-- 2. Then export the trigger DDL (replace with actual trigger name):
--
--    SELECT DBMS_METADATA.GET_DDL('TRIGGER', 'TRG_MYBOOKS_VECTOR_STORE_ROW') AS trigger_ddl
--    FROM DUAL;
--
-- 3. Paste the trigger DDL below and run this script on TARGET database
-- ================================================================

PROMPT Creating trigger for MY_BOOKS table...

-- *** PASTE TRIGGER DDL HERE ***
-- The trigger should automatically vectorize PDFs inserted into MY_BOOKS
-- and populate VECTOR_STORE table

-- Example structure (replace with actual trigger code):
/*
CREATE OR REPLACE TRIGGER trg_mybooks_vector_store_row
AFTER INSERT ON my_books
FOR EACH ROW
BEGIN
  -- Logic to create vectors from file and insert into vector_store
  -- This trigger should process :NEW.file_content and populate vector_store
END;
/
*/


-- Verify trigger created
PROMPT
PROMPT ================================================================
PROMPT Verifying triggers...
PROMPT ================================================================

SELECT trigger_name, status, trigger_type, triggering_event, table_name
FROM user_triggers
WHERE table_name = 'MY_BOOKS';

PROMPT
PROMPT ================================================================
PROMPT Trigger creation complete!
PROMPT ================================================================
PROMPT
PROMPT Next step: Export data from source (05_export_data.sql)
PROMPT ================================================================
