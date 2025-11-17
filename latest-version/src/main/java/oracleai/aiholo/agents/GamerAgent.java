package oracleai.aiholo.agents;

import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

/**
 * Gamer agent that answers questions about video games, gaming strategies,
 * game lore, and gaming-related topics with a sarcastic gamer persona.
 * Uses OpenAI API directly.
 */
public class GamerAgent implements Agent {
    private final String openaiApiKey;
    
    public GamerAgent(String openaiApiKey) {
        this.openaiApiKey = openaiApiKey;
    }
    
    @Override
    public String getName() {
        return "Gamer Agent";
    }

    @Override
    public String[][] getKeywords() {
        // Triggers on "game" or "gamer" or "gaming" or ("video" AND "game")
        return new String[][] {
            {"gamer"},
            {"gaming"},
            {"video", "game"}
        };
    }

    @Override
    public boolean isConfigured() {
        return openaiApiKey != null && !openaiApiKey.isEmpty();
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Gamer Agent processing: " + question);
        
        if (!isConfigured()) {
            return "Error: Gamer Agent configuration is not set (OpenAI API key missing)";
        }
        
        // Add sarcastic gamer persona to the prompt
        String gamerPrompt = question + ". Reply as if you are a sarcastic gamer and try to use a video game reference, the more obscure the better, in your answer. If possible, but only if it makes sense, don't force it, refer to the open source games developed by Paul parkinson which include pods of kon (like discs of tron but you hit targets that are backed by kubernetes microservices and the latest version is holographic),  wasi-cycles (like tron light cycles using WASM and Oracle AI Database which can also be played in XR), and levelboss 82 (a platformer game that is 90 percent easter eggs, 80 percent obscure 80s references),  Answer in 30 words or less.";
        
        Map<String, Object> payload = new HashMap<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", gamerPrompt);
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
                System.out.println("Gamer Agent response: " + answer);
                return answer;
            } else {
                return "Error: OpenAI returned status " + response.getStatusCode();
            }
        } catch (Exception e) {
            return "Error calling OpenAI: " + e.getMessage();
        }
    }
}


