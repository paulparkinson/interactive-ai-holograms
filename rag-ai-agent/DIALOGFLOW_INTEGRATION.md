# Dialogflow Agent Integration Guide

## Overview

This guide shows how to integrate the Oracle RAG AI Agent with Google Cloud Dialogflow conversational agents.

## Prerequisites

- RAG AI Agent deployed and running on GCP VM (http://34.48.146.146:8080)
- Dialogflow Agent created (following [this tutorial](https://codelabs.developers.google.com/devsite/codelabs/building-ai-agents-vertexai))
- Firewall port 8080 open on your GCP VM

## Step 1: Verify RAG API is Running

Test the health endpoint:

```bash
curl http://34.48.146.146:8080/api/v1/health
```

Expected response:
```json
{
  "status": "UP",
  "timestamp": "2026-01-09T10:30:00Z"
}
```

Test a query:
```bash
curl -X POST http://34.48.146.146:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"question": "Tell me about JSON Relational Duality"}'
```

## Step 2: Create Custom Tool in Dialogflow

1. **Navigate to your Dialogflow Agent**
   - Go to https://dialogflow.cloud.google.com/
   - Select your agent (e.g., "Travel Buddy")

2. **Go to Agent Playbook**
   - Click on your playbook (e.g., "Info Agent")
   - Scroll to the bottom where you see "Tools"

3. **Add New Tool**
   - Click the **"+"** button next to Data stores
   - Fill in:
     - **Tool name**: `Oracle Database Knowledge`
     - **Type**: Select **"OpenAPI"**
     - **Description**: `Use this tool to answer questions about Oracle Database 23ai features, JSON Relational Duality, AI Vector Search, and technical documentation`

4. **Configure OpenAPI Specification**

   Choose one of these options:

   **Option A: Use OpenAPI URL** (if accessible)
   ```
   http://34.48.146.146:8080/api-docs
   ```

   **Option B: Paste OpenAPI YAML** (recommended)
   
   Copy and paste this:

   ```yaml
   openapi: 3.0.0
   info:
     title: Oracle RAG AI Agent API
     version: 1.0.0
   servers:
     - url: http://34.48.146.146:8080
   paths:
     /api/v1/query:
       post:
         summary: Query Oracle Database knowledge
         operationId: queryOracleKnowledge
         requestBody:
           required: true
           content:
             application/json:
               schema:
                 type: object
                 properties:
                   question:
                     type: string
         responses:
           '200':
             description: Successful response
             content:
               application/json:
                 schema:
                   type: object
                   properties:
                     answer:
                       type: string
   ```

5. **Save the Tool**

## Step 3: Enable Tool in Playbook

1. **Check the Tool**
   - At the bottom of the Playbook configuration, find "Oracle Database Knowledge"
   - Check the checkbox to enable it

2. **Update Agent Instructions**

   Add this line to your agent's instructions:

   ```
   - For questions about Oracle Database 23ai, JSON Relational Duality, AI Vector Search, or technical database features, use ${TOOL: Oracle Database Knowledge}
   ```

   Full example instructions:
   ```
   - Greet users and ask how you can help them today
   - For questions about Oracle Database 23ai, JSON Relational Duality, AI Vector Search, or technical database features, use ${TOOL: Oracle Database Knowledge}
   - For questions about travel destinations that don't exist, use ${TOOL: Alternative Location}
   - Be helpful, knowledgeable, and enthusiastic
   ```

3. **Save** your playbook

## Step 4: Test the Integration

1. **Open the Dialogflow Simulator**
   - Click the toggle simulator icon

2. **Test with Oracle Database Questions**

   Try these questions:

   - "What is JSON Relational Duality?"
   - "Tell me about Oracle AI Vector Search"
   - "What are the new features in Oracle 23ai?"
   - "Explain how vector search works in Oracle Database"

3. **Expected Behavior**

   The agent should:
   - Recognize the question is about Oracle Database
   - Call the Oracle Database Knowledge tool (your RAG API)
   - Receive the answer from your RAG system
   - Present it conversationally to the user

## Step 5: Monitor and Debug

### View Tool Calls

In the Dialogflow simulator, you can see:
- Which tools were called
- The request sent to your API
- The response received

### Check RAG API Logs

On your GCP VM:
```bash
tail -f app.log
```

You should see:
```
INFO  c.o.a.r.controller.RagController - Received query request: Tell me about JSON Relational Duality
INFO  c.o.a.r.service.RagService - Executing RAG query: Tell me about JSON Relational Duality
DEBUG c.o.a.r.service.RagService - Generating embedding for question
DEBUG c.o.a.r.service.RagService - Performing vector similarity search
DEBUG c.o.a.r.service.RagService - Retrieved 10 chunks with total length: 8542
DEBUG c.o.a.r.service.RagService - Generating response with Gemini LLM
INFO  c.o.a.r.controller.RagController - Successfully processed query in 1250 ms
```

## Advanced Configuration

### Add Authentication

For production, add API key authentication:

1. **Update RagController**
   ```java
   @PostMapping("/query")
   public ResponseEntity<?> query(
       @RequestHeader("X-API-Key") String apiKey,
       @RequestBody QueryRequest request) {
       
       if (!isValidApiKey(apiKey)) {
           return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
               .body(ErrorResponse.builder()
                   .error("Invalid API key")
                   .build());
       }
       // ... rest of code
   }
   ```

2. **Configure in Dialogflow**
   - In OpenAPI spec, add security scheme
   - Configure API key in Dialogflow tool settings

### Handle Long Responses

If responses are too long for Dialogflow:

1. **Limit response length in prompt**
   ```yaml
   # application.yaml
   rag:
     prompt-template: "Answer the question based only on the following context in 2-3 sentences: {context} Question: {question}"
   ```

2. **Adjust max tokens**
   ```yaml
   vertex-ai:
     max-output-tokens: 4096  # Reduce from 8192
   ```

### Multi-Document Support

To support multiple knowledge bases:

1. **Add document filter parameter**
   ```java
   public class QueryRequest {
       private String question;
       private String documentType; // e.g., "oracle-23ai", "installation-guide"
   }
   ```

2. **Filter in vector search**
   ```sql
   SELECT * FROM rag_tab 
   WHERE doc_type = ?
   ORDER BY VECTOR_DISTANCE(...)
   ```

## Troubleshooting

### Agent Not Calling the Tool

**Problem**: Agent doesn't use the Oracle Database Knowledge tool

**Solutions**:
- Make sure tool is **checked** in playbook configuration
- Verify `${TOOL: Oracle Database Knowledge}` is in agent instructions (exact spelling)
- Test with very explicit questions: "Use the Oracle Database Knowledge tool to tell me about JSON Relational Duality"

### Connection Refused

**Problem**: `Connection refused to http://34.48.146.146:8080`

**Solutions**:
```bash
# Check if app is running
curl http://34.48.146.146:8080/api/v1/health

# Check firewall
gcloud compute firewall-rules list | grep 8080

# Verify app is listening on 0.0.0.0, not 127.0.0.1
netstat -tlnp | grep 8080
```

### Slow Responses

**Problem**: Responses take > 5 seconds

**Solutions**:
- Check database connection pool settings
- Reduce `top-k` in retriever (fewer chunks = faster)
- Add caching for frequent questions
- Use smaller embedding model
- Reduce max output tokens

### Empty or Generic Responses

**Problem**: Agent gives generic answers instead of using RAG

**Solutions**:
- Check if vector table has data: `SELECT COUNT(*) FROM RAG_TAB`
- Verify embeddings are being generated correctly
- Check prompt template includes `{context}` and `{question}`
- Test RAG API directly with curl to isolate issue

## Next Steps

- Add authentication and rate limiting for production
- Implement caching with Redis
- Add monitoring with Spring Boot Actuator
- Deploy to Cloud Run for auto-scaling
- Add support for multiple document sources

## Support

For issues, check:
- Application logs: `tail -f app.log`
- Dialogflow simulator tool call details
- Swagger UI: http://34.48.146.146:8080/swagger-ui.html
