package oracleai.aiholo;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing conversation history across all interfaces
 * (web app, voice assistant, etc.)
 */
@Service
public class ConversationHistoryService {
    
    private final List<Map<String, String>> conversationHistory = new ArrayList<>();
    
    /**
     * Add a message to conversation history
     */
    public void addMessage(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        conversationHistory.add(message);
    }
    
    /**
     * Get the full conversation history
     */
    public List<Map<String, String>> getHistory() {
        return conversationHistory;
    }
    
    /**
     * Clear all conversation history
     */
    public void clearHistory() {
        int previousSize = conversationHistory.size();
        conversationHistory.clear();
        System.out.println("[ConversationHistoryService] History cleared. Removed " + previousSize + " entries.");
    }
    
    /**
     * Get the size of conversation history
     */
    public int getSize() {
        return conversationHistory.size();
    }
}
