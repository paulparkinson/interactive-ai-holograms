package oracleai.aiholo.agents;

import oracleai.aiholo.util.OutputFileWriter;
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
        
        try {
            OutputFileWriter.writeData(outputFilePath, getValueName());
            System.out.println("Mirror Me mode activated");
        } catch (IOException e) {
            System.err.println("Error writing mirror me to file: " + e.getMessage());
            return "Error activating Mirror Me mode: " + e.getMessage();
        }
        
        return "Switched to 'Mirror Me' mode successfully!";
    }
}
