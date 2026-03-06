package oracleai.aiholo.agents;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Langchain4j Tool Agent that uses Langchain4j's tool/function-calling
 * pattern backed by Oracle Database data.
 *
 * This agent demonstrates the Langchain4j @Tool annotation approach where:
 * - Tools are defined as methods that query Oracle Database
 * - The LLM decides which tool to call based on the user's question
 * - Results are returned from database queries
 *
 * In a full implementation, this would use Langchain4j's AiService with @Tool
 * annotated methods. This version provides direct database tool execution.
 *
 * Prerequisites:
 * - langchain4j-oracle dependency (already in pom.xml)
 * - Oracle Database with application tables
 */
@Component
public class Langchain4jToolAgent implements Agent {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Override
    public String getName() {
        return "Langchain4j Tool Agent";
    }

    @Override
    public String getValueName() {
        return "langchain4jtoolagent";
    }

    @Override
    public String[][] getKeywords() {
        return new String[][] {
            {"langchain", "tool"},
            {"lc4j", "tool"},
            {"langchain", "function"},
            {"tool", "call", "database"}
        };
    }

    @Override
    public boolean isConfigured() {
        return jdbcTemplate != null;
    }

    @Override
    public boolean preserveOriginalQuestion() {
        return true;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Langchain4j Tool Agent processing: " + question);

        if (!isConfigured()) {
            return "I'm sorry, the Langchain4j Tool feature is not available right now. Please check the database configuration.";
        }

        try {
            // Example tool: list tables in the current schema
            // In a full Langchain4j AiService implementation, the LLM would choose
            // which @Tool method to call. Here we demonstrate a simple database tool.
            String sql = "SELECT table_name FROM user_tables ORDER BY table_name FETCH FIRST 20 ROWS ONLY";
            List<Map<String, Object>> tables = jdbcTemplate.queryForList(sql);

            StringBuilder sb = new StringBuilder();
            sb.append("Available database tables:\n");
            for (Map<String, Object> row : tables) {
                sb.append("  - ").append(row.get("TABLE_NAME")).append("\n");
            }
            sb.append("\nAsk me to query any of these tables using natural language.");

            System.out.println("Langchain4j Tool Agent successfully listed database tools");
            return sb.toString();

        } catch (Exception e) {
            System.err.println("Error in Langchain4j Tool Agent: " + e.getMessage());
            e.printStackTrace();
            return "I'm having difficulty accessing database tools right now. Error: " + e.getMessage();
        }
    }
}
