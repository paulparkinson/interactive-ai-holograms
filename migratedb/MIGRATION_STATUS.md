# VECTOR Schema Migration Summary
## Migration from demo vector to aiholodb

### Current Status: ✓ SCHEMA CREATED - DATA MIGRATION PENDING

---

## ✅ COMPLETED STEPS

### 1. VECTOR User Created on aiholodb
- Username: `vector`
- Password: `Welcome12345*`
- Tablespaces: USERS, DATA (unlimited quotas)
- Privileges granted:
  - CONNECT, RESOURCE, CREATE SESSION
  - CREATE TABLE, VIEW, PROCEDURE, SEQUENCE, SYNONYM
  - EXECUTE ON DBMS_VECTOR, DBMS_VECTOR_CHAIN
  - CREATE MINING MODEL
  - Network ACL for HTTP access to Ollama servers

### 2. Tables Created
✓ **MY_BOOKS** - Ready for 12 rows
✓ **DEMO_BOOKS** - Ready for 1 row  
✓ **VECTOR_STORE** - Ready for 1,951 rows (with VECTOR column)

All tables include:
- Identity columns for IDs
- BLOB storage for file_content
- Constraints and indexes
- Foreign key: VECTOR_STORE → MY_BOOKS

### 3. Functions Created
✓ **GENERATE_TEXT_RESPONSE2** - Main RAG function (with placeholder Ollama URL)
✓ **GENERATE_TEXT_RESPONSE_GEN** - OCI GenAI function (requires TINYBERT_MODEL)
✓ **OLLAMA_TEST_FUNCTION** - Simple test function (with placeholder Ollama URL)

**Note:** Functions are compiled but will not work until:
1. ML models are imported
2. Ollama URLs are updated
3. Data is imported

---

## ⚠️ REMAINING STEPS (MANUAL)

### Step 1: Import ML Models Using Data Pump

The ML models (TINYBERT_MODEL, ALL_MINILM_L6V2MODEL) cannot be migrated via SQL scripts.  
You MUST use Oracle Data Pump:

**On SOURCE database (demo vector):**
```powershell
expdp vector/password@demo_vector `
  directory=DATA_PUMP_DIR `
  dumpfile=ml_models.dmp `
  logfile=ml_models_export.log `
  schemas=VECTOR `
  include=MODEL
```

**Copy ml_models.dmp to target server**

**On TARGET database (aiholodb):**
```powershell
impdp vector/Welcome12345*@aiholodb `
  directory=DATA_PUMP_DIR `
  dumpfile=ml_models.dmp `
  logfile=ml_models_import.log `
  remap_schema=VECTOR:VECTOR
```

### Step 2: Import Data Using Data Pump

**On SOURCE database (demo vector):**
```powershell
expdp vector/password@demo_vector `
  directory=DATA_PUMP_DIR `
  dumpfile=vector_data.dmp `
  logfile=vector_data_export.log `
  tables=MY_BOOKS,DEMO_BOOKS,VECTOR_STORE
```

**Copy vector_data.dmp to target server**

**On TARGET database (aiholodb):**
```powershell
impdp vector/Welcome12345*@aiholodb `
  directory=DATA_PUMP_DIR `
  dumpfile=vector_data.dmp `
  logfile=vector_data_import.log `
  table_exists_action=TRUNCATE
```

### Step 3: Update Ollama Server URLs

Edit the following functions to use your new Ollama server:

**GENERATE_TEXT_RESPONSE2:**
```sql
-- Line 42: Update URL
'"url":"http://YOUR_NEW_OLLAMA_SERVER:11434/api/generate",'
```

**OLLAMA_TEST_FUNCTION:**
```sql
-- Line 5: Update URL
l_url VARCHAR2(200) := 'http://YOUR_NEW_OLLAMA_SERVER:11434/api/generate';
```

Run the updated functions using the scripts in:
- `C:\migratedb\04_create_functions.sql` (edit first, then re-run)

### Step 4: Verify Migration

Run the verification script:
```sql
sqlcl vector/Welcome12345*@aiholodb
@C:\migratedb\07_verify.sql
```

Checklist:
- [ ] User VECTOR exists and is OPEN
- [ ] All 3 tables exist
- [ ] Row counts match (MY_BOOKS: 12, DEMO_BOOKS: 1, VECTOR_STORE: 1951)
- [ ] Both ML models present (TINYBERT_MODEL, ALL_MINILM_L6V2MODEL)
- [ ] All 3 functions are VALID
- [ ] Ollama URLs updated
- [ ] Function test passes

### Step 5: Test the Function

```sql
SELECT generate_text_response2('What is vector search?', 1, 3) AS response 
FROM dual;
```

---

## 📁 MIGRATION PACKAGE LOCATION

All reusable migration scripts are saved in:
```
C:\migratedb\
├── README.md                    - Complete migration guide
├── config.sql                   - Configuration variables
├── 00_MASTER_SCRIPT.sql         - Master script with all instructions
├── QUICK_START.sql              - Quick start guide for Data Pump
├── 01_create_user.sql           - User creation (✓ EXECUTED)
├── 02_create_tables.sql         - Table creation (✓ EXECUTED)
├── 03_import_ml_models.sql      - ML model import instructions
├── 04_create_functions.sql      - Function creation (✓ EXECUTED, needs URL update)
├── 05_export_data.sql           - Data export instructions
├── 06_import_data.sql           - Data import instructions
└── 07_verify.sql                - Verification queries
```

---

## 🔄 REUSE FOR OTHER DATABASES

This migration package is reusable! To migrate to another database:

1. Update connection details in scripts
2. Update Ollama server URL in `config.sql` and `04_create_functions.sql`
3. Update password in `01_create_user.sql` if needed
4. Run through the steps 1-5 above

---

## 📊 DATA TO BE MIGRATED

| Object | Type | Count | Status |
|--------|------|-------|--------|
| VECTOR user | User | 1 | ✅ Created |
| MY_BOOKS | Table | 12 rows | ⚠️ Empty - needs Data Pump |
| DEMO_BOOKS | Table | 1 row | ⚠️ Empty - needs Data Pump |
| VECTOR_STORE | Table | 1,951 rows | ⚠️ Empty - needs Data Pump |
| TINYBERT_MODEL | ML Model | - | ⚠️ Missing - needs Data Pump |
| ALL_MINILM_L6V2MODEL | ML Model | - | ⚠️ Missing - needs Data Pump |
| GENERATE_TEXT_RESPONSE2 | Function | - | ✅ Created (needs Ollama URL) |
| GENERATE_TEXT_RESPONSE_GEN | Function | - | ✅ Created (needs model) |
| OLLAMA_TEST_FUNCTION | Function | - | ✅ Created (needs Ollama URL) |

---

## ⚡ QUICK MIGRATION (All-in-One Data Pump)

For fastest migration, use a single Data Pump export/import:

**Export everything from source:**
```powershell
expdp vector/password@demo_vector `
  directory=DATA_PUMP_DIR `
  dumpfile=vector_full.dmp `
  logfile=vector_full_export.log `
  schemas=VECTOR
```

**Import to target (tables already exist, so use content=DATA_ONLY):**
```powershell
impdp vector/Welcome12345*@aiholodb `
  directory=DATA_PUMP_DIR `
  dumpfile=vector_full.dmp `
  logfile=vector_full_import.log `
  content=DATA_ONLY `
  remap_schema=VECTOR:VECTOR `
  table_exists_action=TRUNCATE
```

This imports:
- All ML models
- All table data
- Sequences and other objects

Then just update Ollama URLs and test!

---

## 🆘 TROUBLESHOOTING

### Functions show errors during compilation
**Cause:** ML models don't exist yet  
**Solution:** Import ML models first (Step 1), then recompile functions

### Data Pump import fails
**Cause:** Missing tablespaces or insufficient privileges  
**Solution:** Verify USERS and DATA tablespaces exist, check quotas

### generate_text_response2 returns ORA-XXXX error
**Cause:** ML model missing or Ollama server unreachable  
**Solution:** 
1. Verify TINYBERT_MODEL exists: `SELECT * FROM user_mining_models;`
2. Update Ollama URL in function
3. Test network connectivity to Ollama server

### VECTOR_STORE import fails
**Cause:** MY_BOOKS must be imported first (foreign key dependency)  
**Solution:** Import MY_BOOKS before VECTOR_STORE, or use Data Pump with dependencies enabled

---

## 📝 NOTES

- **Original Ollama URL (demo vector):** `http://10.10.51.56:11434/api/generate`
- **New Ollama URL (aiholodb):** To be configured by you
- **Database versions:** Both Oracle 23c+ with AI Vector Search
- **Password:** Remember to update if reusing for production systems
- **Network access:** Ensure firewall allows database → Ollama traffic

---

**Generated:** February 1, 2026  
**Status:** Schema created, data migration pending  
**Next Action:** Run Data Pump exports from demo vector database
