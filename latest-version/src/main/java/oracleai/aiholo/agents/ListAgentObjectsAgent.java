package oracleai.aiholo.agents;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists available objects from agents that have listable collections.
 * Triggered by "list" plus any list keyword from agents that hasList().
 * Reports the total count and shows up to the first 10 items.
 */
public class ListAgentObjectsAgent implements Agent {
    private final List<Agent> listableAgents;

    public ListAgentObjectsAgent(List<Agent> listableAgents) {
        this.listableAgents = listableAgents;
    }

    @Override
    public String getName() {
        return "List Agent Objects Agent";
    }

    @Override
    public String getValueName() {
        return "listagentobjects";
    }

    @Override
    public String[][] getKeywords() {
        List<String[]> keywordSets = new ArrayList<>();
        for (Agent agent : listableAgents) {
            if (agent.hasList()) {
                for (String keyword : agent.getListKeywords()) {
                    keywordSets.add(new String[] {"list", keyword});
                }
            }
        }
        return keywordSets.toArray(new String[0][]);
    }

    @Override
    public boolean isConfigured() {
        return !listableAgents.isEmpty();
    }

    @Override
    public boolean preserveOriginalQuestion() {
        return true;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("List Agent Objects Agent processing: " + question);

        String lowerQuestion = question.toLowerCase();

        // Find which listable agent matches the question
        for (Agent agent : listableAgents) {
            if (!agent.hasList()) continue;
            for (String keyword : agent.getListKeywords()) {
                if (lowerQuestion.contains(keyword.toLowerCase())) {
                    return buildListResponse(agent);
                }
            }
        }

        return "I couldn't determine which list you're looking for.";
    }

    private String buildListResponse(Agent agent) {
        String[] items = agent.getList();
        int total = items.length;
        int limit = Math.min(total, 10);

        StringBuilder response = new StringBuilder();
        response.append("There are ").append(total).append(" ").append(agent.getName().toLowerCase()
                .replace(" agent", "")).append(" items available");

        if (total > limit) {
            response.append(". Here are the first ").append(limit).append(": ");
        } else {
            response.append(": ");
        }

        for (int i = 0; i < limit; i++) {
            response.append(items[i]);
            if (i < limit - 2) {
                response.append(", ");
            } else if (i == limit - 2) {
                response.append(", and ");
            }
        }
        response.append(".");

        return response.toString();
    }
}
