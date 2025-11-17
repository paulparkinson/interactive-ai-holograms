package oracleai.aiholo.agents;

import org.json.JSONObject;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Mirror Me agent that activates mirror mode when user says "mirror me".
 * This agent doesn't call any LLM - it just sets the mode in the output file.
 */
public class MirrorMeAgent implements Agent {
    private final String outputFilePath;
    
    public MirrorMeAgent(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }
    
    @Override
    public String getName() {
        return "Mirror Me Agent";
    }

    @Override
    public String getValueName() {
        return "mirrorme";
    }

    @Override
    public String[][] getKeywords() {
        // Triggers on "mirror" AND "me" together
        return new String[][] {
            {"mirror", "me"}
        };
    }

    @Override
    public boolean isConfigured() {
        // This agent doesn't need external configuration
        return true;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Mirror Me Agent activating mirror mode");
        
        String filePath = outputFilePath != null ? outputFilePath : "aiholo_output.txt";
        try (FileWriter writer = new FileWriter(filePath)) {
            JSONObject json = new JSONObject();
            json.put("data", "mirrorme");
            writer.write(json.toString());
            writer.flush();
            System.out.println("Mirror Me mode activated - wrote to: " + filePath);
        } catch (IOException e) {
            System.err.println("Error writing mirror me to file: " + e.getMessage());
            return "Error activating Mirror Me mode: " + e.getMessage();
        }
        
        return "Switched to 'Mirror Me' mode successfully!";
    }
}
