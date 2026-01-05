package oracleai.aiholo;

import oracleai.aiholo.agents.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Common service for managing and routing questions to appropriate AI agents.
 * This service is shared between AIHoloController and VoiceAssistantService
 * to ensure consistent agent-based question handling across all interfaces.
 */
@Service
public class AgentService {
    
    private final List<Agent> registeredAgents = new ArrayList<>();
    private final DataSource dataSource;
    private final ChatGPTService chatGPTService;
    
    /**
     * Initialize and register all agents at service creation
     */
    @Autowired
    public AgentService(DataSource dataSource, ChatGPTService chatGPTService) {
        this.dataSource = dataSource;
        this.chatGPTService = chatGPTService;
        initializeAgents();
    }
    
    /**
     * Initialize agents in priority order (first match wins)
     */
    private void initializeAgents() {
        // Get configuration values
        String outputFilePath = Configuration.getOutputFilePath();
        String ociVisionEndpoint = Configuration.getOciVisionEndpoint();
        String ociCompartmentId = Configuration.getOciCompartmentId();
        String ociApiKey = Configuration.getOciApiKey();
        String sandboxApiUrl = Configuration.getSandboxApiUrl();
        String aiOptimizer = Configuration.getAiOptimizer();
        String langflowServerUrl = Configuration.getLangflowServerUrl();
        String langflowFlowId = Configuration.getLangflowFlowId();
        String langflowApiKey = Configuration.getLangflowApiKey();
        String openaiKey = Configuration.getOpenAiKey();
        
        // Register agents in priority order
        registerAgent(new MirrorMeAgent(outputFilePath));
        registerAgent(new DigitalTwinAgent(outputFilePath));
        registerAgent(new SignAgent(outputFilePath));
        registerAgent(new VisionAIAgent(ociVisionEndpoint, ociCompartmentId, ociApiKey));
        registerAgent(new AIToolkitAgent(sandboxApiUrl, aiOptimizer));
        registerAgent(new FinancialAgent(langflowServerUrl, langflowFlowId, langflowApiKey));
        registerAgent(new GamerAgent(openaiKey));
        
        // Oracle Doc Agent with vector database integration (requires DataSource and ChatGPT)
        registerAgent(new OracleDocAgent(dataSource, chatGPTService));
        
        // DirectLLMAgent is the preferred fallback if OpenAI key is available
        registerAgent(new DirectLLMAgent(openaiKey));
        
        // DefaultFallbackAgent is always available as final fallback
        registerAgent(new DefaultFallbackAgent());
    }
    
    /**
     * Register an agent if it's properly configured
     */
    private void registerAgent(Agent agent) {
        if (agent.isConfigured()) {
            registeredAgents.add(agent);
            System.out.println("[AgentService] Registered agent: " + agent.getName());
        } else {
            System.out.println("[AgentService] Skipping agent (not configured): " + agent.getName());
        }
    }
    
    /**
     * Find the appropriate agent for a question
     * @param question The user's question (will be normalized internally)
     * @return The matching agent, or a fallback agent if no specific match
     */
    public Agent findAgentForQuestion(String question) {
        String normalized = question.toLowerCase();
        Agent fallbackAgent = null;
        
        for (Agent agent : registeredAgents) {
            String[][] keywords = agent.getKeywords();
            
            // If agent has no keywords, it's a fallback agent (like DirectLLMAgent)
            if (keywords.length == 0) {
                fallbackAgent = agent;
                continue;
            }
            
            // Check if any keyword set matches
            for (String[] keywordSet : keywords) {
                boolean allMatch = true;
                for (String keyword : keywordSet) {
                    if (!normalized.contains(keyword.toLowerCase())) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return agent;
                }
            }
        }
        
        // If no specific agent matched, return the fallback agent
        return fallbackAgent;
    }
    
    /**
     * Process a question using the appropriate agent
     * @param question The user's question
     * @param maxWords Maximum words for response (optional, agent may ignore)
     * @return AgentResponse containing the answer and metadata
     */
    public AgentResponse processQuestion(String question, Integer maxWords) {
        String normalized = question.toLowerCase();
        Agent matchingAgent = findAgentForQuestion(normalized);
        
        if (matchingAgent == null) {
            return new AgentResponse(
                "I'm sorry, I couldn't process that question.",
                "NoAgent",
                "Unknown",
                0.0
            );
        }
        
        // Strip trigger keywords before processing
        String cleanedQuestion = stripTriggerKeywords(question, matchingAgent);
        
        // Add word limit instruction if specified
        if (maxWords != null && maxWords > 0) {
            cleanedQuestion += String.format(". Respond in %d words or less.", maxWords);
        }
        
        System.out.println("[AgentService] Using agent: " + matchingAgent.getName());
        System.out.println("[AgentService] Original question: " + question);
        System.out.println("[AgentService] Cleaned question: " + cleanedQuestion);
        
        long startNs = System.nanoTime();
        String answer = matchingAgent.processQuestion(cleanedQuestion);
        double durationMs = (System.nanoTime() - startNs) / 1_000_000.0;
        
        // Strip any "A:", "A2:", etc. prefixes
        if (answer != null) {
            answer = answer.replaceFirst("^A\\d*:\\s*", "");
        }
        
        return new AgentResponse(
            answer,
            matchingAgent.getName(),
            matchingAgent.getValueName(),
            durationMs
        );
    }
    
    /**
     * Strip trigger keywords from the question before passing to agent
     */
    private String stripTriggerKeywords(String question, Agent agent) {
        if (agent == null) return question;
        
        String cleanQuestion = question;
        String[][] keywords = agent.getKeywords();
        
        // Find the matching keyword set and remove those keywords
        for (String[] keywordSet : keywords) {
            boolean allMatch = true;
            String lowerQuestion = question.toLowerCase();
            for (String keyword : keywordSet) {
                if (!lowerQuestion.contains(keyword.toLowerCase())) {
                    allMatch = false;
                    break;
                }
            }
            
            // If this keyword set matched, remove these keywords
            if (allMatch) {
                for (String keyword : keywordSet) {
                    // Case-insensitive keyword removal with regex
                    String pattern1 = "(?i)\\s+" + keyword + "\\s+";
                    String pattern2 = "(?i)\\s+" + keyword + "(?=\\b|$)";
                    String pattern3 = "(?i)(?<=^|\\b)" + keyword + "\\s+";
                    String pattern4 = "(?i)\\b" + keyword + "\\b";
                    
                    cleanQuestion = cleanQuestion.replaceAll(pattern1, " ");
                    cleanQuestion = cleanQuestion.replaceAll(pattern2, " ");
                    cleanQuestion = cleanQuestion.replaceAll(pattern3, " ");
                    cleanQuestion = cleanQuestion.replaceAll(pattern4, " ");
                }
                break;
            }
        }
        
        // Clean up extra whitespace and trim
        cleanQuestion = cleanQuestion.replaceAll("\\s+", " ").trim();
        
        return cleanQuestion;
    }
    
    /**
     * Get list of all registered agents
     */
    public List<Agent> getRegisteredAgents() {
        return new ArrayList<>(registeredAgents);
    }
    
    /**
     * Response from agent processing
     */
    public static class AgentResponse {
        private final String answer;
        private final String agentName;
        private final String valueName;
        private final double durationMs;
        
        public AgentResponse(String answer, String agentName, String valueName, double durationMs) {
            this.answer = answer;
            this.agentName = agentName;
            this.valueName = valueName;
            this.durationMs = durationMs;
        }
        
        public String getAnswer() {
            return answer;
        }
        
        public String getAgentName() {
            return agentName;
        }
        
        public String getValueName() {
            return valueName;
        }
        
        public double getDurationMs() {
            return durationMs;
        }
    }
}
