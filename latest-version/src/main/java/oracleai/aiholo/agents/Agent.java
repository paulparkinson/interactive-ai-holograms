package oracleai.aiholo.agents;

/**
 * Interface for AI agents that can handle specific types of questions.
 * Agents register themselves with AIHoloController and are selected based
 * on keyword matching in user questions.
 */
public interface Agent {
    /**
     * @return The name of this agent (e.g., "Financial Agent", "Gamer Agent")
     */
    String getName();

    /**
     * @return The value to write to aiholo_output.txt (e.g., "financialagent", "gameragent")
     */
    default String getValueName() {
        return getName().toLowerCase().replace(" ", "");
    }

    /**
     * @return Array of keywords that trigger this agent. Questions containing
     *         any of these keywords (case-insensitive) will be routed to this agent.
     *         For multi-word triggers, all words must be present.
     *         Example: ["financ", "agent"] requires both words to match
     */
    String[][] getKeywords();

    /**
     * Process a question and return an answer.
     * 
     * @param question The user's question
     * @return The agent's response
     */
    String processQuestion(String question);

    /**
     * @return true if this agent is properly configured and ready to use
     */
    boolean isConfigured();
    
    /**
     * @return true if this agent writes its own value to the output file.
     *         When true, the controller will skip calling AgentStateService.writeAgentResponse.
     *         Default is false (controller handles file writing).
     */
    default boolean handlesOwnFileWriting() {
        return false;
    }
}
