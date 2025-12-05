package oracleai.aiholo.agents;

import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Financial agent that uses Langflow to answer questions about finance,
 * stocks, portfolios, and financial data.
 */
public class FinancialAgent implements Agent {
    private final String langflowServerUrl;
    private final String flowId;
    private final String apiKey;

    public FinancialAgent(String langflowServerUrl, String flowId, String apiKey) {
        this.langflowServerUrl = langflowServerUrl;
        this.flowId = flowId;
        this.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return "Financial Agent";
    }

    @Override
    public String[][] getKeywords() {
        // Requires both "financ" AND "agent" to be present
        return new String[][] {
            {"financ", "agent"}
        };
    }

    @Override
    public boolean isConfigured() {
        return langflowServerUrl != null && flowId != null && apiKey != null;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Financial Agent processing: " + question);
        
        if (!isConfigured()) {
            return "Error: Financial Agent configuration is not set";
        }

        String url = langflowServerUrl + "/v1/run/" + flowId + "?stream=false";
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("output_type", "chat");
        payload.put("input_type", "chat");
        payload.put("input_value", question);

        JSONObject jsonPayload = new JSONObject(payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        
        HttpEntity<String> request = new HttpEntity<>(jsonPayload.toString(), headers);
        RestTemplate restTemplate = new RestTemplate();
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                String body = response.getBody();
                if (body == null) {
                    return "Error: Empty response from Langflow";
                }
                
                String trimmedBody = body.trim();
                if (!trimmedBody.startsWith("{") && !trimmedBody.startsWith("[")) {
                    return trimmedBody;
                }
                
                JSONObject responseData;
                try {
                    responseData = new JSONObject(trimmedBody);
                } catch (Exception e) {
                    return "Error parsing Langflow response: " + e.getMessage();
                }
                
                try {
                    String message = responseData
                            .getJSONArray("outputs")
                            .getJSONObject(0)
                            .getJSONArray("outputs")
                            .getJSONObject(0)
                            .getJSONObject("outputs")
                            .getJSONObject("message")
                            .getString("message");
                    return message;
                } catch (Exception e1) {
                    try {
                        String text = responseData
                                .getJSONArray("outputs")
                                .getJSONObject(0)
                                .getJSONArray("outputs")
                                .getJSONObject(0)
                                .getJSONObject("results")
                                .getJSONObject("message")
                                .getString("text");
                        return text;
                    } catch (Exception e2) {
                        return "Error extracting message from response: " + e2.getMessage();
                    }
                }
            } else {
                return "Error: Langflow returned status " + response.getStatusCode();
            }
        } catch (Exception e) {
            return "Error calling Langflow: " + e.getMessage();
        }
    }
}
