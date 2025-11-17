package oracleai.aiholo.agents;

import oracleai.status.StatusController;
import org.json.JSONObject;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Digital Twin Agent that displays volumetric models when user says 
 * "show volumetric [model]" or "display volumetric [model]" or "show digital twin [model]".
 * Sets the StatusController value to trigger the volumetric display.
 * Like MirrorMeAgent, this doesn't call any LLM - just sets values.
 */
public class DigitalTwinAgent implements Agent {
    
    private static final String[] VOLUMETRIC_MODELS = {
        "airport",
        "recognizer", 
        "tron",
        "kubernetes",
        "spring boot",
        "ghoul",
        "human"
    };
    
    private final String outputFilePath;
    
    public DigitalTwinAgent(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }
    
    @Override
    public String getName() {
        return "Digital Twin Agent";
    }

    @Override
    public String getValueName() {
        return "digitaltwinagent";
    }

    @Override
    public String[][] getKeywords() {
        // Triggers on ("show" OR "display") AND ("digital twin" OR "volumetric")
        return new String[][] {
            {"show", "digital twin"},
            {"display", "digital twin"},
            {"show", "volumetric"},
            {"display", "volumetric"}
        };
    }

    @Override
    public boolean isConfigured() {
        // This agent doesn't need external configuration
        return true;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Digital Twin Agent processing: " + question);
        
        String lowerQuestion = question.toLowerCase();
        
        // Check which volumetric model is mentioned in the question
        for (String model : VOLUMETRIC_MODELS) {
            if (lowerQuestion.contains(model.toLowerCase())) {
                System.out.println("Digital Twin Agent found model: " + model);
                
                // Write to aiholo_output.txt
                String filePath = outputFilePath != null ? outputFilePath : "aiholo_output.txt";
                try (FileWriter writer = new FileWriter(filePath)) {
                    JSONObject json = new JSONObject();
                    json.put("data", getValueName());
                    writer.write(json.toString());
                    writer.flush();
                    System.out.println("Digital Twin Agent wrote to: " + filePath);
                } catch (IOException e) {
                    System.err.println("Error writing digital twin agent to file: " + e.getMessage());
                }
                
                // Set the status value to trigger volumetric display with JSON format
                JSONObject statusJson = new JSONObject();
                statusJson.put("type", "digitaltwin");
                statusJson.put("value", model);
                StatusController.setStatusValueStatic(statusJson.toString());
                
                return "Displaying volumetric model of digital twin: " + model;
            }
        }
        
        // No specific model found - list available models
        return "Please specify a volumetric model: " + String.join(", ", VOLUMETRIC_MODELS);
    }
}

