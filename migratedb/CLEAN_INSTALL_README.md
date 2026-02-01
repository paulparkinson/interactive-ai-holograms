# Clean Installation Guide - VECTOR Schema
## Install from scratch without data import

This guide shows how to create the VECTOR schema on aiholodb from scratch, then upload and vectorize your own PDFs.

---

## 📋 Installation Steps

### Step 1: Create User and Grant Privileges
Run: [`01_create_user.sql`](01_create_user.sql) as ADMIN

### Step 2: Create Tables
Run: [`02_create_tables.sql`](02_create_tables.sql) as VECTOR

### Step 3: Import ML Models
**Critical:** You need the ONNX model files from demo vector:
- `tinybert.onnx` (17.7 MB)
- `all-MiniLM-L6-v2.onnx` (90.6 MB)

These are available in demo vector's DATA_PUMP_DIR:
`/u01/app/oracle/admin/orcl/dpdump/3439A922B4626531E0637E0B00632F3A/`

**Download them to your local machine, then upload to aiholodb**

Run: [`08_import_ml_models_from_onnx.sql`](08_import_ml_models_from_onnx.sql)

### Step 4: Create Functions and Procedures
Run: [`09_create_all_functions.sql`](09_create_all_functions.sql) as VECTOR

**Important:** Edit this file first to update your Ollama server URL!

### Step 5: Upload and Vectorize PDFs
Run: [`10_upload_and_vectorize_pdf.sql`](10_upload_and_vectorize_pdf.sql)

This shows you how to:
- Upload a PDF file to MY_BOOKS table
- Extract text chunks from the PDF
- Generate vector embeddings for each chunk
- Store in VECTOR_STORE table

### Step 6: Test!
```sql
SELECT generate_text_response2('What is vector search?', 1, 3) AS response 
FROM dual;
```

---

## 📁 File Summary

| File | Description |
|------|-------------|
| `01_create_user.sql` | ✅ Already exists - Creates VECTOR user |
| `02_create_tables.sql` | ✅ Already exists - Creates tables |
| `08_import_ml_models_from_onnx.sql` | 🆕 Import ONNX models |
| `09_create_all_functions.sql` | 🆕 All functions/procedures |
| `10_upload_and_vectorize_pdf.sql` | 🆕 PDF upload guide |
| `11_example_workflow.sql` | 🆕 Complete example |

---

## 🎯 Advantages of This Approach

✅ **No large dump files to transfer**  
✅ **Version controlled SQL scripts**  
✅ **Start fresh with your own documents**  
✅ **Easier to customize**  
✅ **Can be automated**  

---

## 📖 Next: See Individual Scripts

Each script has detailed comments and instructions.
