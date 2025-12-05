package oracleai.aiholo.agents;

import oracleai.aiholo.ChatGPTService;

/**
 * Default fallback agent that provides helpful responses using ChatGPT when no other agents match.
 * Uses OpenAI's ChatGPT API to provide intelligent responses for general questions.
 */
public class DefaultFallbackAgent implements Agent {
    
    private final ChatGPTService chatGPTService;
    
    public DefaultFallbackAgent() {
        this.chatGPTService = new ChatGPTService();
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
        String apiKey = System.getenv("OPENAI_API_KEY");
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Default ChatGPT Agent processing: " + question);
        
        // Add instruction to keep responses concise
        String enhancedQuestion = question + " (Please respond in 25 words or less)";
        
        // Use ChatGPT for intelligent responses (use default model)
        String response = chatGPTService.queryChatGPT(enhancedQuestion, "gpt-4");
        
        // Fallback to simple response if ChatGPT fails
        if (response == null || response.contains("error") || response.contains("not configured")) {
            return "I'm here to help!. However, the LLM seems to be unavailable at the moment.";
        }
        
        return response;
    }
}