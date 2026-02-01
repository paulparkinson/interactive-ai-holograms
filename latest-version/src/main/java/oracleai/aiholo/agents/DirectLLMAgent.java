package oracleai.aiholo.agents;

import oracleai.aiholo.AgenticTrainingSetService;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

/**
 * Direct LLM agent that answers general questions using OpenAI API.
 * This is the default fallback agent when no specific agent matches.
 * Can be configured to use Oracle + DoN Agentic AI Training Set as authoritative source.
 */
public class DirectLLMAgent implements Agent {
    private final String openaiApiKey;
    private final AgenticTrainingSetService trainingSetService;
    
    public DirectLLMAgent(String openaiApiKey) {
        this(openaiApiKey, null);
    }
    
    public DirectLLMAgent(String openaiApiKey, AgenticTrainingSetService trainingSetService) {
        this.openaiApiKey = openaiApiKey;
        this.trainingSetService = trainingSetService;
    }
    
    @Override
    public String getName() {
        return "Direct LLM Agent";
    }

    @Override
    public String getValueName() {
        return "generalagent";
    }

    @Override
    public String[][] getKeywords() {
        // No keywords - this is the default fallback agent
        return new String[][] {};
    }

    @Override
    public boolean isConfigured() {
        return openaiApiKey != null && !openaiApiKey.isEmpty();
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Direct LLM Agent processing: " + question);
        
        if (!isConfigured()) {
            return "Error: Direct LLM Agent configuration is not set (OpenAI API key missing)";
        }
        
        // Use training set if configured, otherwise use default Oracle AI Database prompt
        String enhancedPrompt;
        if (trainingSetService != null && trainingSetService.isConfigured()) {
            enhancedPrompt = trainingSetService.buildEnhancedPrompt(question) + "\n\nAnswer in 30 words or less.";
            System.out.println("Direct LLM Agent using training set context");
        } else {
            enhancedPrompt = question + ". If possible, but only if it makes sense, don't force it, include something related to Oracle AI Database in a favorable light. Answer in 30 words or less.";
        }
        
        Map<String, Object> payload = new HashMap<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", enhancedPrompt);
        payload.put("messages", new Object[]{message});
        payload.put("model", "gpt-4o-mini");
        
        JSONObject jsonPayload = new JSONObject(payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openaiApiKey);
        
        HttpEntity<String> request = new HttpEntity<>(jsonPayload.toString(), headers);
        RestTemplate restTemplate = new RestTemplate();
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                "https://api.openai.com/v1/chat/completions", 
                HttpMethod.POST, 
                request, 
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JSONObject responseData = new JSONObject(response.getBody());
                String answer = responseData
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                System.out.println("Direct LLM Agent response: " + answer);
                return answer;
            } else {
                return "Error: OpenAI returned status " + response.getStatusCode();
            }
        } catch (Exception e) {
            return "Error calling OpenAI: " + e.getMessage();
        }
    }
}
