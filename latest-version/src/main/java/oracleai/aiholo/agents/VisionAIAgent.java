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
 * Vision AI agent that analyzes images using ChatGPT Vision API
 * to describe and understand visual content.
 * Triggered by keywords related to vision, images, or camera.
 */
public class VisionAIAgent implements Agent {
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String OPENAI_API_KEY = oracleai.aiholo.Configuration.getOpenAiApiKey();
    private static final String VISION_MODEL = "gpt-4o";  // GPT-4 with vision capabilities
    
    public VisionAIAgent(String ociVisionEndpoint, String ociCompartmentId, String ociApiKey) {
        // Keep constructor signature for compatibility but don't use OCI params
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
            {"what", "do", "you", "see"}
        };
    }

    @Override
    public boolean isConfigured() {
        return OPENAI_API_KEY != null && !OPENAI_API_KEY.isEmpty();
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Vision AI Agent processing: " + question);
        
        // Capture image from webcam
        if (!isConfigured()) {
            return "Vision AI is not configured. Please set OPENAI_API_KEY environment variable.";
        }
        
        // Capture image from webcam
        try {
            String imagePath = captureAndSaveImage();
            
            if (imagePath != null) {
                System.out.println("Image captured, analyzing with ChatGPT Vision...");
                
                // Read and convert image to base64
                File imageFile = new File(imagePath);
                BufferedImage bufferedImage = ImageIO.read(imageFile);
                String base64Image = convertImageToBase64(bufferedImage);
                
                // Analyze with ChatGPT Vision
                String analysis = analyzeImageWithChatGPT(base64Image, question);
                
                return analysis;
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
     * Analyzes an image using ChatGPT Vision API
     * 
     * @param imageBase64 Base64-encoded image data
     * @param userQuestion The user's original question for context
     * @return Natural language description of the image
     */
    private String analyzeImageWithChatGPT(String imageBase64, String userQuestion) {
        System.out.println("Analyzing image with ChatGPT Vision API...");
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // Build the request body for GPT-4 Vision
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", VISION_MODEL);
            requestBody.put("max_tokens", 500);
            
            // Create messages array with image
            JSONArray messages = new JSONArray();
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            
            // Create content array with text and image
            JSONArray content = new JSONArray();
            
            // Add text prompt
            JSONObject textContent = new JSONObject();
            textContent.put("type", "text");
            textContent.put("text", "Describe what you see in this image in a natural, conversational way. Be specific about objects, people, actions, and the overall scene. Keep your response concise but informative.");
            content.put(textContent);
            
            // Add image
            JSONObject imageContent = new JSONObject();
            imageContent.put("type", "image_url");
            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:image/png;base64," + imageBase64);
            imageContent.put("image_url", imageUrl);
            content.put(imageContent);
            
            userMessage.put("content", content);
            messages.put(userMessage);
            
            requestBody.put("messages", messages);
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(OPENAI_API_KEY);
            
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
            
            // Make the API call
            System.out.println("Calling ChatGPT Vision API...");
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
                    String description = message.getString("content").trim();
                    
                    System.out.println("ChatGPT Vision analysis complete");
                    return description;
                }
            }
            
            System.err.println("Unexpected response from ChatGPT Vision API: " + response.getStatusCode());
            return "Sorry, I couldn't analyze the image with ChatGPT.";
            
        } catch (Exception e) {
            System.err.println("Error calling ChatGPT Vision API: " + e.getMessage());
            e.printStackTrace();
            return "An error occurred while analyzing the image: " + e.getMessage();
        }
    }
}
