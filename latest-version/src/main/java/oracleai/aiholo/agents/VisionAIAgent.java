package oracleai.aiholo.agents;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.Base64;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;

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
            {"what", "see"}
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
        
        // Capture image from webcam
        try {
            String imagePath = captureAndSaveImage();
            
            if (imagePath != null) {
                String result = "I've captured an image and saved it to: " + imagePath;
                
                // If OCI Vision is configured, also analyze the image
                if (isConfigured()) {
                    File imageFile = new File(imagePath);
                    BufferedImage bufferedImage = ImageIO.read(imageFile);
                    String base64Image = convertImageToBase64(bufferedImage);
                    String analysis = analyzeImage(base64Image);
                    result += "\n\nAnalysis: " + analysis;
                }
                
                return result;
            } else {
                return "Sorry, I couldn't capture an image from the webcam.";
            }
        } catch (Exception e) {
            System.err.println("Error capturing image: " + e.getMessage());
            e.printStackTrace();
            return "An error occurred while capturing the image: " + e.getMessage();
        }
    }
    
    /**
     * Captures an image from the default webcam and saves it to the current directory
     * 
     * @return Path to the saved image file, or null if capture failed
     */
    private String captureAndSaveImage() {
        Webcam webcam = null;
        try {
            // Get default webcam
            webcam = Webcam.getDefault();
            if (webcam == null) {
                System.err.println("No webcam detected!");
                return null;
            }
            
            // Set resolution
            webcam.setViewSize(WebcamResolution.VGA.getSize());
            
            // Open webcam
            System.out.println("Opening webcam...");
            webcam.open();
            
            // Give the camera time to adjust
            Thread.sleep(1000);
            
            // Capture image
            System.out.println("Capturing image...");
            BufferedImage image = webcam.getImage();
            
            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "vision_capture_" + timestamp + ".png";
            
            // Save to current directory
            File outputFile = new File(filename);
            ImageIO.write(image, "PNG", outputFile);
            
            System.out.println("Image saved to: " + outputFile.getAbsolutePath());
            return outputFile.getAbsolutePath();
            
        } catch (Exception e) {
            System.err.println("Error capturing image: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (webcam != null && webcam.isOpen()) {
                webcam.close();
            }
        }
    }
    
    /**
     * Converts a BufferedImage to Base64 string
     * 
     * @param image BufferedImage to convert
     * @return Base64-encoded string
     */
    private String convertImageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
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
