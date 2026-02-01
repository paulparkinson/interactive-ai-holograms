# Oracle VECTOR Schema Migration Package

This package contains all scripts needed to migrate the VECTOR schema from one Oracle database to another.

## Migration Overview

**Source:** demo vector database
**Target:** aiholodb (or any Oracle 23c+ database)
**User:** VECTOR
**Password:** Welcome12345*

## Contents

1. **01_create_user.sql** - Creates VECTOR user and grants necessary privileges
2. **02_create_tables.sql** - Creates essential tables (MY_BOOKS, DEMO_BOOKS, VECTOR_STORE)
3. **03_import_ml_models.sql** - Instructions to import ML models (TINYBERT_MODEL, ALL_MINILM_L6V2MODEL)
4. **04_create_functions.sql** - Creates all 3 functions
5. **05_export_data.sql** - Exports data from source (run on demo vector)
6. **06_import_data.sql** - Imports data to target (run on aiholodb as VECTOR)
7. **07_verify.sql** - Verification queries
8. **config.sql** - Configuration file for Ollama server URL (update this!)

## Migration Steps

### Step 1: Create User and Schema Objects on Target
Connect as ADMIN to aiholodb:
```sql
@01_create_user.sql
```

### Step 2: Connect as VECTOR and Create Tables
```sql
CONNECT vector/Welcome12345*@aiholodb
@02_create_tables.sql
```

### Step 3: Import ML Models
Follow instructions in `03_import_ml_models.sql`

### Step 4: Create Functions
```sql
@04_create_functions.sql
```

### Step 5: Export Data from Source
Connect to demo vector database:
```sql
@05_export_data.sql
```

### Step 6: Import Data to Target
Connect as VECTOR to aiholodb:
```sql
@06_import_data.sql
```

### Step 7: Verify Migration
```sql
@07_verify.sql
```

## Important Notes

- **Ollama Server:** Update the Ollama server URL in `config.sql` and `04_create_functions.sql` before running
- **ML Models:** Both TINYBERT_MODEL and ALL_MINILM_L6V2MODEL must be imported using ONNX files
- **Data Volume:** VECTOR_STORE has ~1,951 rows, MY_BOOKS has 12 rows
- **Dependencies:** VECTOR_STORE has a foreign key to MY_BOOKS, so import MY_BOOKS first

## Reusability

This package can be used to migrate to any Oracle 23c+ database with AI Vector Search capabilities.
Simply update the connection details and Ollama server URL.

---
Generated: February 1, 2026
