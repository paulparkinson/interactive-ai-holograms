package oracleai.aiholo.agents;

import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

/**
 * AI Toolkit agent that uses Oracle AI Sandbox for advanced processing.
 * Can be triggered by keywords related to toolkit, sandbox, or advanced AI operations.
 */
public class AIToolkitAgent implements Agent {
    private final String sandboxApiUrl;
    private final String aiOptimizerToken;
    
    public AIToolkitAgent(String sandboxApiUrl, String aiOptimizerToken) {
        this.sandboxApiUrl = sandboxApiUrl;
        this.aiOptimizerToken = aiOptimizerToken;
    }
    
    @Override
    public String getName() {
        return "AI Toolkit Agent";
    }

    @Override
    public String getValueName() {
        return "aitoolkit";
    }

    @Override
    public String[][] getKeywords() {
        return new String[][] {
            {"toolkit", "agent"},
            {"sandbox"},
            {"ai", "optimizer"}
        };
    }

    @Override
    public boolean isConfigured() {
        return sandboxApiUrl != null && !sandboxApiUrl.isEmpty() &&
               aiOptimizerToken != null && !aiOptimizerToken.isEmpty();
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("AI Toolkit Agent processing: " + question);
        
        if (!isConfigured()) {
            return "AI Toolkit Agent is not configured. Please set SANDBOX_API_URL and AI_OPTIMZER environment variables.";
        }
        
        return executeSandbox(question);
    }

    /**
     * Executes a query against the AI Sandbox/Optimizer service
     * 
     * @param question The user's question
     * @return The AI Sandbox response
     */
    private String executeSandbox(String question) {
        System.out.println("Using AI sandbox: " + question);
        
        try {
            Map<String, Object> payload = new HashMap<>();
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", question);
            payload.put("messages", new Object[]{message});
            
            JSONObject jsonPayload = new JSONObject(payload);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", aiOptimizerToken);
            headers.set("Accept", "application/json");
            headers.set("client", "server");
            
            HttpEntity<String> request = new HttpEntity<>(jsonPayload.toString(), headers);
            RestTemplate restTemplate = new RestTemplate();
            
            ResponseEntity<String> response = restTemplate.exchange(
                sandboxApiUrl, 
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
                
                System.out.println("AI Sandbox response: " + answer);
                return answer;
            } else {
                System.err.println("Failed to fetch data from AI Sandbox: " + 
                    response.getStatusCode() + " " + response.getBody());
                return "I'm sorry, I couldn't get a response from the AI Toolkit.";
            }
        } catch (Exception e) {
            System.err.println("Error calling AI Sandbox: " + e.getMessage());
            e.printStackTrace();
            return "An error occurred while using the AI Toolkit: " + e.getMessage();
        }
    }
}
