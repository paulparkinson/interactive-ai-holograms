package oracleai.aiholo.agents;

import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent that uses Oracle AI Optimizer and Toolkit for general questions.
 * This agent handles questions that don't match more specific agents.
 */
public class OptimizerToolkitAgent implements Agent {
    private final String sandboxApiUrl;
    private final String aiOptimizer;
    
    public OptimizerToolkitAgent(String sandboxApiUrl, String aiOptimizer) {
        this.sandboxApiUrl = sandboxApiUrl;
        this.aiOptimizer = aiOptimizer;
    }
    
    @Override
    public String getName() {
        return "Optimizer Toolkit Agent";
    }

    @Override
    public String[][] getKeywords() {
        // This is a catch-all agent - no specific keywords
        // It should be registered last and used when no other agent matches
        return new String[0][];
    }

    @Override
    public boolean isConfigured() {
        return sandboxApiUrl != null && aiOptimizer != null;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Optimizer Toolkit Agent processing: " + question);
        
        if (!isConfigured()) {
            return "Error: Optimizer Toolkit Agent configuration is not set";
        }
        
        Map<String, Object> payload = new HashMap<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", question);
        payload.put("messages", new Object[]{message});
        
        JSONObject jsonPayload = new JSONObject(payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", aiOptimizer);
        headers.set("Accept", "application/json");
        headers.set("client", "server");
        
        HttpEntity<String> request = new HttpEntity<>(jsonPayload.toString(), headers);
        RestTemplate restTemplate = new RestTemplate();
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(sandboxApiUrl, HttpMethod.POST, request, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JSONObject responseData = new JSONObject(response.getBody());
                String answer = responseData
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                System.out.println("Optimizer Toolkit Agent response: " + answer);
                return answer;
            } else {
                return "Error: Optimizer returned status " + response.getStatusCode();
            }
        } catch (Exception e) {
            return "Error calling Optimizer: " + e.getMessage();
        }
    }
}
