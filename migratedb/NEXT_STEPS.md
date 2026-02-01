# NEXT STEPS - Complete Your Migration

## ✅ What's Been Done

I've successfully created the VECTOR schema on **aiholodb** with:
- ✅ VECTOR user (password: Welcome12345*)
- ✅ 3 tables ready: MY_BOOKS, DEMO_BOOKS, VECTOR_STORE
- ✅ 3 functions created: GENERATE_TEXT_RESPONSE2, GENERATE_TEXT_RESPONSE_GEN, OLLAMA_TEST_FUNCTION
- ✅ All privileges and network ACLs configured
- ✅ All migration scripts saved to `C:\migratedb\`

## ⚠️ What You Need to Do

The schema is ready, but you need to migrate the **data** and **ML models** from **demo vector** to **aiholodb**.

### OPTION 1: Quick Full Migration (RECOMMENDED) ⚡

This is the fastest way - one export, one import, done!

#### Step 1: Export Everything from Source
Open PowerShell and run:

```powershell
expdp vector/YOUR_PASSWORD@demo_vector `
  directory=DATA_PUMP_DIR `
  dumpfile=vector_full.dmp `
  logfile=vector_full_export.log `
  schemas=VECTOR
```

**Replace `YOUR_PASSWORD` with the demo vector password**

This exports:
- All data (MY_BOOKS: 12 rows, DEMO_BOOKS: 1 row, VECTOR_STORE: 1,951 rows)
- ML models (TINYBERT_MODEL, ALL_MINILM_L6V2MODEL)
- Everything else

#### Step 2: Copy the Dump File
If demo vector and aiholodb are on different servers:
```powershell
# Copy vector_full.dmp from source to target
# Use file share, scp, or USB drive
```

#### Step 3: Import to Target (Data Only)
Tables already exist, so import data only:

```powershell
impdp vector/Welcome12345*@aiholodb `
  directory=DATA_PUMP_DIR `
  dumpfile=vector_full.dmp `
  logfile=vector_full_import.log `
  content=DATA_ONLY `
  remap_schema=VECTOR:VECTOR `
  table_exists_action=TRUNCATE
```

Wait for import to complete (~5-10 minutes)

#### Step 4: Update Ollama Server URLs

The functions currently have placeholder URLs. Update them:

**Edit and run this SQL as VECTOR user on aiholodb:**

```sql
-- Connect to aiholodb as VECTOR
-- Update GENERATE_TEXT_RESPONSE2
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

  -- *** CHANGE THIS URL TO YOUR OLLAMA SERVER ***
  l_params := JSON(
      '{'||
      '"provider":"ollama",'||
      '"url":"http://YOUR_OLLAMA_IP:11434/api/generate",'||
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

-- Update OLLAMA_TEST_FUNCTION too
CREATE OR REPLACE FUNCTION ollama_test_function (p_question IN VARCHAR2)
  RETURN CLOB
IS
  l_url    VARCHAR2(200) := 'http://YOUR_OLLAMA_IP:11434/api/generate';  -- CHANGE THIS
  l_model  VARCHAR2(50)  := 'llama3.2:3b';
  l_params JSON;
  l_out    CLOB;
BEGIN
  l_params := JSON(
      '{' || '"provider":"ollama",' || '"url":"' || l_url || '",' || 
      '"model":"' || l_model || '"' || '}'
  );

  l_out := DBMS_VECTOR_CHAIN.UTL_TO_GENERATE_TEXT(
    '## Answer this question: ' || p_question, l_params
  );

  RETURN l_out;
END;
/
```

**Replace `YOUR_OLLAMA_IP` with your actual Ollama server IP/hostname**

#### Step 5: Verify Migration

Run verification queries:

```sql
-- Check row counts
SELECT 'MY_BOOKS' as table_name, COUNT(*) as rows FROM my_books
UNION ALL
SELECT 'DEMO_BOOKS', COUNT(*) FROM demo_books
UNION ALL
SELECT 'VECTOR_STORE', COUNT(*) FROM vector_store;

-- Expected: MY_BOOKS=12, DEMO_BOOKS=1, VECTOR_STORE=1951

-- Check ML models
SELECT model_name, mining_function, algorithm 
FROM user_mining_models 
ORDER BY model_name;

-- Expected: TINYBERT_MODEL, ALL_MINILM_L6V2MODEL

-- Check functions
SELECT object_name, status 
FROM user_objects 
WHERE object_type = 'FUNCTION';

-- Expected: All VALID
```

#### Step 6: Test!

```sql
SELECT generate_text_response2('What is vector search?', 1, 3) AS response 
FROM dual;
```

If this returns a meaningful answer, **migration is complete!** 🎉

---

### OPTION 2: Separate Migrations (If Option 1 Fails)

If the full export doesn't work, migrate in parts:

1. **ML Models:**
   ```powershell
   expdp vector/password@demo_vector directory=DATA_PUMP_DIR dumpfile=ml_models.dmp logfile=ml_export.log schemas=VECTOR include=MODEL
   impdp vector/Welcome12345*@aiholodb directory=DATA_PUMP_DIR dumpfile=ml_models.dmp logfile=ml_import.log
   ```

2. **Data:**
   ```powershell
   expdp vector/password@demo_vector directory=DATA_PUMP_DIR dumpfile=vector_data.dmp logfile=data_export.log tables=MY_BOOKS,DEMO_BOOKS,VECTOR_STORE
   impdp vector/Welcome12345*@aiholodb directory=DATA_PUMP_DIR dumpfile=vector_data.dmp logfile=data_import.log table_exists_action=TRUNCATE
   ```

3. Update Ollama URLs (same as Step 4 above)

4. Verify and test (same as Steps 5-6 above)

---

## 📋 Quick Checklist

- [ ] Run Data Pump export from demo vector
- [ ] Copy dump file to aiholodb server (if different)
- [ ] Run Data Pump import on aiholodb
- [ ] Update Ollama server URLs in both functions
- [ ] Verify row counts match expected values
- [ ] Verify ML models imported successfully
- [ ] Test generate_text_response2 function
- [ ] Success! 🎉

---

## 🆘 Troubleshooting

**Problem:** `ORA-39001: invalid argument value`  
**Solution:** Check that DATA_PUMP_DIR exists and has permissions:
```sql
SELECT * FROM dba_directories WHERE directory_name = 'DATA_PUMP_DIR';
```

**Problem:** `ORA-40284: model does not exist`  
**Solution:** ML models weren't imported. Run ML model import separately (Option 2, step 1)

**Problem:** Function returns network error  
**Solution:** 
1. Check Ollama URL is correct
2. Verify Ollama server is running and accessible
3. Test with: `curl http://YOUR_OLLAMA_IP:11434/api/generate`

**Problem:** Import fails with tablespace errors  
**Solution:** Tablespaces already set up correctly. If issue persists, check disk space.

---

## 📁 All Migration Files

Complete migration package saved to: **`C:\migratedb\`**

Key files:
- `MIGRATION_STATUS.md` - Full status report
- `NEXT_STEPS.md` - This file
- `QUICK_START.sql` - Quick reference
- `04_create_functions.sql` - Function templates (for Ollama URL updates)
- `07_verify.sql` - Comprehensive verification script

---

## 🔄 Reuse This Migration

Save the `C:\migratedb\` folder to migrate to other databases later!

Just update:
1. Connection strings
2. Ollama server URL
3. Passwords

Then run the same steps.

---

**You're almost done! Just run the Data Pump commands and update the Ollama URL.**

**Good luck!** 🚀
