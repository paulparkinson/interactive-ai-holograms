-- ================================================================
-- Configuration File for VECTOR Schema Migration
-- ================================================================
-- Update these values for your target environment
--
-- OLLAMA_SERVER_URL: The URL of your Ollama server
-- Update this in the functions after migration
-- ================================================================

DEFINE OLLAMA_SERVER_URL = 'http://YOUR_NEW_OLLAMA_SERVER:11434/api/generate'
DEFINE OLLAMA_MODEL = 'llama3.2:3b'

-- Source database Ollama (demo vector): http://10.10.51.56:11434/api/generate
-- Target database Ollama (aiholodb): UPDATE THIS!

-- To update the Ollama URL in functions after migration, run:
-- ALTER FUNCTION generate_text_response2 COMPILE;
-- (after manually editing the URL in the function code)
