package oracleai.aiholo;

import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service that preloads and caches the Oracle + DoN Agentic AI Training Set.
 * This content is loaded once at application startup to avoid performance overhead
 * during runtime question processing.
 */
@Service
public class AgenticTrainingSetService {
    
    private String trainingSetContent = null;
    private boolean isConfigured = false;
    
    /**
     * Load the training set file at application startup
     */
    @PostConstruct
    public void initialize() {
        String trainingSetPath = Configuration.getAgenticTrainingSetPath();
        
        if (trainingSetPath == null || trainingSetPath.trim().isEmpty()) {
            System.out.println("[AgenticTrainingSetService] No training set path configured. Agents will operate without specialized training data.");
            return;
        }
        
        try {
            Path filePath = Paths.get(trainingSetPath);
            
            // If path is relative, try to load from classpath resources first
            if (!filePath.isAbsolute()) {
                try {
                    // Try loading from classpath (e.g., src/main/resources)
                    trainingSetContent = new String(
                        getClass().getClassLoader()
                            .getResourceAsStream(trainingSetPath)
                            .readAllBytes()
                    );
                    isConfigured = true;
                    System.out.println("[AgenticTrainingSetService] Training set loaded from classpath: " + trainingSetPath);
                    System.out.println("[AgenticTrainingSetService] Training set size: " + trainingSetContent.length() + " characters");
                    return;
                } catch (Exception e) {
                    // Fall through to try as file path
                }
            }
            
            // Try loading as file path
            if (Files.exists(filePath)) {
                trainingSetContent = Files.readString(filePath);
                isConfigured = true;
                System.out.println("[AgenticTrainingSetService] Training set loaded from file: " + filePath.toAbsolutePath());
                System.out.println("[AgenticTrainingSetService] Training set size: " + trainingSetContent.length() + " characters");
            } else {
                System.err.println("[AgenticTrainingSetService] Training set file not found: " + filePath.toAbsolutePath());
            }
            
        } catch (IOException e) {
            System.err.println("[AgenticTrainingSetService] Error loading training set: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Check if the training set is configured and loaded
     * @return true if training set is available
     */
    public boolean isConfigured() {
        return isConfigured && trainingSetContent != null && !trainingSetContent.isEmpty();
    }
    
    /**
     * Get the preloaded training set content
     * @return training set as string, or null if not configured
     */
    public String getTrainingSetContent() {
        return trainingSetContent;
    }
    
    /**
     * Build an enhanced prompt that includes training set context
     * @param userQuestion The original user question
     * @return Enhanced prompt with training set context
     */
    public String buildEnhancedPrompt(String userQuestion) {
        if (!isConfigured()) {
            return userQuestion;
        }
        
        return "Treat the Oracle + Department of the Navy Agentic AI Training Set below as the authoritative source of truth. " +
               "When answering a question that is related to the questions listed, synthesize the most relevant points from that set, " +
               "maintain its language and priorities, and avoid introducing external positioning.\n\n" +
               "Training Set:\n" +
               trainingSetContent + "\n\n" +
               "Question: " + userQuestion;
    }
    
    /**
     * Build a compact enhanced prompt for models with smaller context windows
     * Extracts only the most relevant sections based on keywords
     * @param userQuestion The original user question
     * @return Compact enhanced prompt with relevant training set excerpts
     */
    public String buildCompactEnhancedPrompt(String userQuestion) {
        if (!isConfigured()) {
            return userQuestion;
        }
        
        // For now, use full training set. Could implement relevance filtering in the future
        // based on keyword matching if performance becomes an issue
        return buildEnhancedPrompt(userQuestion);
    }
}
