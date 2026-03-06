package oracleai.aiholo.agents;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * DB Property Graph Agent that performs relationship-based queries
 * using Oracle Database 23ai's SQL/PGQ (Property Graph Queries).
 *
 * This agent leverages:
 * - Oracle 23ai SQL/PGQ syntax for graph pattern matching
 * - GRAPH_TABLE function with MATCH clauses
 * - Relationship traversal within the database
 *
 * Prerequisites:
 * - Oracle Database 23ai with SQL/PGQ support
 * - A property graph created via CREATE PROPERTY GRAPH
 *
 * Example graph creation:
 *   CREATE PROPERTY GRAPH my_graph
 *     VERTEX TABLES (ships, equipment)
 *     EDGE TABLES (ship_equipment BETWEEN ships AND equipment);
 */
@Component
public class DBPropertyGraphAgent implements Agent {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Override
    public String getName() {
        return "DB Property Graph Agent";
    }

    @Override
    public String getValueName() {
        return "dbpropertygraphagent";
    }

    @Override
    public String[][] getKeywords() {
        return new String[][] {
            {"graph", "query"},
            {"relationship", "query"},
            {"connected", "to"},
            {"related", "to"},
            {"graph", "search"},
            {"property", "graph"}
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
        System.out.println("DB Property Graph Agent processing: " + question);

        if (!isConfigured()) {
            return "I'm sorry, the property graph feature is not available right now. Please check the database configuration.";
        }

        try {
            // Example: query a property graph using SQL/PGQ GRAPH_TABLE syntax
            // This is a template — customize the graph name and MATCH pattern for your schema.
            String sql = """
                SELECT *
                FROM GRAPH_TABLE ( my_graph
                    MATCH (v1) -[e]-> (v2)
                    WHERE v1.name LIKE ?
                    COLUMNS (v1.name AS source, e.relationship AS rel, v2.name AS target)
                )
                FETCH FIRST 10 ROWS ONLY
                """;

            // Extract a search term from the question
            String searchTerm = "%" + question.replaceAll("(?i)(graph|query|search|find|show|related|connected|to|property)\\s*", "").trim() + "%";

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, searchTerm);

            if (results.isEmpty()) {
                return "No graph relationships found matching your query.";
            }

            StringBuilder sb = new StringBuilder("Graph relationships found:\n");
            for (Map<String, Object> row : results) {
                sb.append("  ").append(row.get("SOURCE"))
                  .append(" --[").append(row.get("REL")).append("]--> ")
                  .append(row.get("TARGET")).append("\n");
            }

            System.out.println("DB Property Graph Agent successfully processed graph query");
            return sb.toString();

        } catch (Exception e) {
            System.err.println("Error in DB Property Graph Agent: " + e.getMessage());
            e.printStackTrace();
            return "I'm having difficulty with the graph query right now. " +
                   "Ensure a property graph is created in the database. Error: " + e.getMessage();
        }
    }
}
