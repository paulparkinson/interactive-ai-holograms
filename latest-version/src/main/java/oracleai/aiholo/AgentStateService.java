package oracleai.aiholo;

import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Centralized service for managing agent state output.
 * Writes the current agent/value name to a file that can be read by external systems
 * (e.g., Unreal Engine for hologram selection).
 * Used by both web interface (AIHoloController) and voice assistant (VoiceAssistantService).
 */
@Service
public class AgentStateService {
    
    private static final String OUTPUT_FILE_PATH = Configuration.getOutputFilePath();
    private static final String DEFAULT_FILE_PATH = "aiholo_output.txt";
    
    /**
     * Writes the agent value name to the output file.
     * This value is typically used to select which hologram/animation to display.
     * 
     * @param valueName The name of the value to write (e.g., "financialagent", "leia", "question")
     * @return true if write was successful, false otherwise
     */
    public boolean writeAgentValue(String valueName) {
        String filePath = OUTPUT_FILE_PATH != null ? OUTPUT_FILE_PATH : DEFAULT_FILE_PATH;
        try (FileWriter writer = new FileWriter(filePath)) {
            JSONObject json = new JSONObject();
            json.put("data", valueName);
            writer.write(json.toString());
            writer.flush();
            System.out.println("Successfully wrote agent value '" + valueName + "' to " + filePath);
            return true;
        } catch (IOException e) {
            System.err.println("Error writing agent value to file: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Writes an agent response value to the output file.
     * Convenience method that accepts AgentService.AgentResponse.
     * 
     * @param agentResponse The agent response containing the value name
     * @return true if write was successful, false otherwise
     */
    public boolean writeAgentResponse(AgentService.AgentResponse agentResponse) {
        if (agentResponse == null) {
            System.err.println("Cannot write null agent response");
            return false;
        }
        System.out.println("Writing agent response - Agent: " + agentResponse.getAgentName() + 
                         ", Value: " + agentResponse.getValueName());
        return writeAgentValue(agentResponse.getValueName());
    }
}
