package oracleai.aiholo.agents;

import oracleai.aiholo.AgenticTrainingSetService;
import oracleai.aiholo.ChatGPTService;

/**
 * Default fallback agent that provides helpful responses using ChatGPT when no other agents match.
 * Can be configured to use Oracle + DoN Agentic AI Training Set as authoritative source.
 */
public class DefaultFallbackAgent implements Agent {
    
    private final ChatGPTService chatGPTService;
    private final AgenticTrainingSetService trainingSetService;
    
    public DefaultFallbackAgent() {
        this(null);
    }
    
    public DefaultFallbackAgent(AgenticTrainingSetService trainingSetService) {
        this.chatGPTService = new ChatGPTService();
        this.trainingSetService = trainingSetService;
    }
    
    @Override
    public String getName() {
        return "Default ChatGPT Agent";
    }

    @Override
    public String getValueName() {
        return "generalagent"; // Write same value as FinancialAgent for consistency
    }

    @Override
    public String[][] getKeywords() {
        // No keywords - this is the default fallback agent
        return new String[][] {};
    }

    @Override
    public boolean isConfigured() {
        // Check if OpenAI API key is available (use same env var as ChatGPTService)
        String apiKey = oracleai.aiholo.Configuration.getOpenAiApiKey();
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Default ChatGPT Agent processing: " + question);
        
        // Use training set if configured, otherwise use default prompt
        String enhancedQuestion;
        if (trainingSetService != null && trainingSetService.isConfigured()) {
            enhancedQuestion = trainingSetService.buildEnhancedPrompt(question) + "\n\n(Please respond in 25 words or less)";
            System.out.println("Default ChatGPT Agent using training set context");
        } else {
            enhancedQuestion = question + " (Please respond in 25 words or less)";
        }
        
        // Use ChatGPT for intelligent responses (use default model)
        String response = chatGPTService.queryChatGPT(enhancedQuestion, "gpt-4");
        
        // Fallback to simple response if ChatGPT fails
        // Check for specific error prefixes from ChatGPTService, not the generic word "error"
        if (response == null || response.startsWith("An error occurred") ||
            response.startsWith("ChatGPT service is not configured") ||
            response.startsWith("Sorry, I couldn't get a response")) {
            return "I'm here to help! However, the LLM seems to be unavailable at the moment.";
        }
        
        return response;
    }
}