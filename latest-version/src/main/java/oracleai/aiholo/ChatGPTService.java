package oracleai.aiholo;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Service for direct ChatGPT API integration
 */
@Service
public class ChatGPTService {
    
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String DEFAULT_MODEL = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4");
    
    /**
     * Send a query directly to ChatGPT and return the response
     * 
     * @param question The user's question
     * @return ChatGPT's response text
     */
    public String queryChatGPT(String question) {
        if (OPENAI_API_KEY == null || OPENAI_API_KEY.isEmpty()) {
            System.err.println("OPENAI_API_KEY environment variable not set");
            return "ChatGPT service is not configured. Please set OPENAI_API_KEY environment variable.";
        }
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // Build the request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", DEFAULT_MODEL);
            
            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", question);
            messages.put(userMessage);
            
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 150);
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(OPENAI_API_KEY);
            
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
            
            // Make the API call
            System.out.println("Calling ChatGPT API with model: " + DEFAULT_MODEL);
            ResponseEntity<String> response = restTemplate.exchange(
                OPENAI_API_URL,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            // Parse the response
            if (response.getStatusCode() == HttpStatus.OK) {
                JSONObject responseJson = new JSONObject(response.getBody());
                JSONArray choices = responseJson.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    JSONObject message = firstChoice.getJSONObject("message");
                    String answer = message.getString("content").trim();
                    
                    System.out.println("ChatGPT response received: " + answer.substring(0, Math.min(100, answer.length())) + "...");
                    return answer;
                }
            }
            
            System.err.println("Unexpected response from ChatGPT API: " + response.getStatusCode());
            return "Sorry, I couldn't get a response from ChatGPT.";
            
        } catch (Exception e) {
            System.err.println("Error calling ChatGPT API: " + e.getMessage());
            e.printStackTrace();
            return "An error occurred while contacting ChatGPT: " + e.getMessage();
        }
    }
}
