-- ================================================================
-- Step 1: Create VECTOR User and Grant Privileges
-- ================================================================
-- Run this script as ADMIN on the target database (aiholodb)
-- ================================================================

-- Create the VECTOR user
CREATE USER vector IDENTIFIED BY "Welcome12345*"
  DEFAULT TABLESPACE users
  QUOTA UNLIMITED ON users
  QUOTA UNLIMITED ON data;

-- Grant basic session and object creation privileges
GRANT CONNECT TO vector;
GRANT RESOURCE TO vector;
GRANT CREATE SESSION TO vector;
GRANT CREATE TABLE TO vector;
GRANT CREATE VIEW TO vector;
GRANT CREATE PROCEDURE TO vector;
GRANT CREATE SEQUENCE TO vector;
GRANT CREATE SYNONYM TO vector;

-- Grant AI Vector Search privileges
GRANT EXECUTE ON DBMS_VECTOR TO vector;
GRANT EXECUTE ON DBMS_VECTOR_CHAIN TO vector;

-- Grant privileges for ML models
GRANT CREATE MINING MODEL TO vector;
GRANT EXECUTE ON CTX_DDL TO vector;

-- Grant JSON privileges
GRANT EXECUTE ON JSON TO vector;

-- Grant network access for Ollama calls
BEGIN
  DBMS_NETWORK_ACL_ADMIN.APPEND_HOST_ACE(
    host       => '*',
    ace        => xs$ace_type(
                    privilege_list => xs$name_list('http', 'connect'),
                    principal_name => 'VECTOR',
                    principal_type => xs_acl.ptype_db
                  )
  );
END;
/

-- Display user privileges
SELECT * FROM dba_sys_privs WHERE grantee = 'VECTOR' ORDER BY privilege;

PROMPT
PROMPT ================================================================
PROMPT VECTOR user created successfully!
PROMPT Default tablespace: USERS
PROMPT Password: Welcome12345*
PROMPT ================================================================
PROMPT
PROMPT Next step: Connect as VECTOR and run 02_create_tables.sql
PROMPT ================================================================
