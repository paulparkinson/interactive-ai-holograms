package oracleai.aiholo.agents;

import oracleai.aiholo.ConversationHistoryService;

/**
 * Agent that clears conversation history.
 * Triggered by keywords like "clear history", "reset history", "forget history".
 */
public class ClearHistoryAgent implements Agent {
    private final ConversationHistoryService conversationHistoryService;
    
    public ClearHistoryAgent(ConversationHistoryService conversationHistoryService) {
        this.conversationHistoryService = conversationHistoryService;
    }
    
    @Override
    public String getName() {
        return "Clear History Agent";
    }
    
    @Override
    public String getValueName() {
        return "clearhistory";
    }
    
    @Override
    public String[][] getKeywords() {
        return new String[][] {
            {"clear", "history"},
            {"reset", "history"},
            {"forget", "history"},
            {"delete", "history"}
        };
    }
    
    @Override
    public boolean isConfigured() {
        // Always configured - no external dependencies
        return true;
    }
    
    @Override
    public String processQuestion(String question) {
        System.out.println("Clear History Agent processing: " + question);
        
        conversationHistoryService.clearHistory();
        return "History cleared. I've forgotten our previous conversation.";
    }
}
