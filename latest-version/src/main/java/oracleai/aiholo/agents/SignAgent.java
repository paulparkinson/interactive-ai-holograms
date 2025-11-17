package oracleai.aiholo.agents;

import org.json.JSONObject;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

/**
 * Sign Agent that changes signs when user says "change sign [name]".
 * Calls the aiholo.org API to set the sign value.
 * Like MirrorMeAgent and VolumetricAgent, this doesn't call any LLM - just sets values.
 */
public class SignAgent implements Agent {
    
    private static final String[] SIGNS = {
        "logo",
        "database",
        "mcp",
        "data",
        "redbull",
        "death star",
        "jackson",
        "leia",
        "spinning",
        "sauron"
    };
    
    private final String outputFilePath;
    
    public SignAgent(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }
    
    @Override
    public String getName() {
        return "Sign Agent";
    }

    @Override
    public String getValueName() {
        return "signagent";
    }

    @Override
    public String[][] getKeywords() {
        // Triggers on "change" AND "sign"
        return new String[][] {
            {"change", "sign"}
        };
    }

    @Override
    public boolean isConfigured() {
        // This agent doesn't need external configuration
        return true;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Sign Agent processing: " + question);
        
        String lowerQuestion = question.toLowerCase();
        
        // Check which sign is mentioned in the question
        for (String sign : SIGNS) {
            if (lowerQuestion.contains(sign.toLowerCase())) {
                System.out.println("Sign Agent found sign: " + sign);
                
                // Write to aiholo_output.txt
                // String filePath = outputFilePath != null ? outputFilePath : "aiholo_output.txt";
                // try (FileWriter writer = new FileWriter(filePath)) {
                //     JSONObject json = new JSONObject();
                //     json.put("data", getValueName());
                //     writer.write(json.toString());
                //     writer.flush();
                //     System.out.println("Sign Agent wrote to: " + filePath);
                // } catch (IOException e) {
                //     System.err.println("Error writing sign agent to file: " + e.getMessage());
                // }
                
                // Call the aiholo.org API to set the sign value
                try {
                    String url = "https://aiholo.org/aiholo/simple/set?value=" + 
                                java.net.URLEncoder.encode(question, "UTF-8");
                    
                    // Create HTTP client and request with basic auth
                    HttpClient client = HttpClient.newHttpClient();
                    String auth = "oracleai:oracleai";
                    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                    
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", "Basic " + encodedAuth)
                            .GET()
                            .build();
                    
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    System.out.println("Sign Agent API response: " + response.statusCode());
                    
                } catch (Exception e) {
                    System.err.println("Error calling aiholo.org API: " + e.getMessage());
                }
                
                return "Changing sign to: " + sign;
            }
        }
        
        // No specific sign found - list available signs
        return "Please specify a sign: " + String.join(", ", SIGNS);
    }
}
