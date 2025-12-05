package oracleai.aiholo.agents;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.Base64;

/**
 * Vision AI agent that analyzes images using OCI Vision Service
 * to detect objects, text, and other visual features.
 * Triggered by keywords related to vision, images, or camera.
 */
public class VisionAIAgent implements Agent {
    private final String ociVisionEndpoint;
    private final String ociCompartmentId;
    private final String ociApiKey;
    
    public VisionAIAgent(String ociVisionEndpoint, String ociCompartmentId, String ociApiKey) {
        this.ociVisionEndpoint = ociVisionEndpoint;
        this.ociCompartmentId = ociCompartmentId;
        this.ociApiKey = ociApiKey;
    }
    
    @Override
    public String getName() {
        return "Vision AI Agent";
    }

    @Override
    public String getValueName() {
        return "visionagent";
    }

    @Override
    public String[][] getKeywords() {
        return new String[][] {
            {"vision", "agent"},
            {"camera"},
            {"picture"},
            {"photo"},
            {"image"},
            {"what", "see"},
            {"analyze", "image"}
        };
    }

    @Override
    public boolean isConfigured() {
        return ociVisionEndpoint != null && !ociVisionEndpoint.isEmpty() &&
               ociCompartmentId != null && !ociCompartmentId.isEmpty();
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Vision AI Agent processing: " + question);
        
        if (!isConfigured()) {
            return "Vision AI Agent is not configured. Please set OCI_VISION_ENDPOINT and OCI_COMPARTMENT_ID environment variables.";
        }
        
        // This will be called after the image is captured and sent from the frontend
        return "Please use the camera button to capture an image for analysis.";
    }

    /**
     * Analyzes an image using OCI Vision Service
     * 
     * @param imageBase64 Base64-encoded image data
     * @return Description of detected objects and features
     */
    public String analyzeImage(String imageBase64) {
        System.out.println("Vision AI Agent analyzing image...");
        
        if (!isConfigured()) {
            return "Vision AI Agent is not configured properly.";
        }
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // Build OCI Vision API request
            JSONObject requestBody = new JSONObject();
            requestBody.put("compartmentId", ociCompartmentId);
            
            JSONObject imageObject = new JSONObject();
            imageObject.put("source", "INLINE");
            imageObject.put("data", imageBase64);
            
            JSONArray features = new JSONArray();
            JSONObject objectDetection = new JSONObject();
            objectDetection.put("featureType", "OBJECT_DETECTION");
            objectDetection.put("maxResults", 10);
            features.put(objectDetection);
            
            JSONObject imageClassification = new JSONObject();
            imageClassification.put("featureType", "IMAGE_CLASSIFICATION");
            imageClassification.put("maxResults", 5);
            features.put(imageClassification);
            
            requestBody.put("image", imageObject);
            requestBody.put("features", features);
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (ociApiKey != null && !ociApiKey.isEmpty()) {
                headers.set("Authorization", "Bearer " + ociApiKey);
            }
            
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
            
            // Make the API call
            System.out.println("Calling OCI Vision API at: " + ociVisionEndpoint);
            ResponseEntity<String> response = restTemplate.exchange(
                ociVisionEndpoint + "/actions/analyzeImage",
                HttpMethod.POST,
                entity,
                String.class
            );
            
            // Parse the response
            if (response.getStatusCode() == HttpStatus.OK) {
                JSONObject responseJson = new JSONObject(response.getBody());
                StringBuilder result = new StringBuilder("I can see: ");
                
                // Extract object detection results
                if (responseJson.has("imageObjects")) {
                    JSONArray objects = responseJson.getJSONArray("imageObjects");
                    for (int i = 0; i < Math.min(5, objects.length()); i++) {
                        JSONObject obj = objects.getJSONObject(i);
                        String name = obj.getString("name");
                        double confidence = obj.getDouble("confidence");
                        result.append(name).append(" (").append(String.format("%.0f", confidence * 100)).append("%), ");
                    }
                }
                
                // Extract classification results
                if (responseJson.has("labels")) {
                    JSONArray labels = responseJson.getJSONArray("labels");
                    result.append("\nCategories: ");
                    for (int i = 0; i < Math.min(3, labels.length()); i++) {
                        JSONObject label = labels.getJSONObject(i);
                        String name = label.getString("name");
                        result.append(name).append(", ");
                    }
                }
                
                String finalResult = result.toString().replaceAll(", $", "");
                System.out.println("Vision AI analysis complete: " + finalResult);
                return finalResult;
            }
            
            System.err.println("Unexpected response from OCI Vision API: " + response.getStatusCode());
            return "Sorry, I couldn't analyze the image. Status: " + response.getStatusCode();
            
        } catch (Exception e) {
            System.err.println("Error calling OCI Vision API: " + e.getMessage());
            e.printStackTrace();
            return "An error occurred while analyzing the image: " + e.getMessage();
        }
    }
}
