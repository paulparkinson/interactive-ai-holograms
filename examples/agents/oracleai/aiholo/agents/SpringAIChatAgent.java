package oracleai.aiholo.agents;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Spring AI Chat Agent that uses Spring AI's ChatClient with Oracle Database
 * as a tool/function-calling backend.
 *
 * This agent leverages:
 * - Spring AI ChatClient for LLM interaction (OpenAI, OCI GenAI, etc.)
 * - Oracle Database as a data source for grounding responses
 * - Spring AI's structured output capabilities
 *
 * The agent queries Oracle for relevant context, then passes it to the
 * ChatClient to generate a grounded response.
 */
@Component
public class SpringAIChatAgent implements Agent {

    @Autowired(required = false)
    @Qualifier("openAiChatClient")
    private ChatClient chatClient;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Override
    public String getName() {
        return "Spring AI Chat Agent";
    }

    @Override
    public String getValueName() {
        return "springaichatagent";
    }

    @Override
    public String[][] getKeywords() {
        return new String[][] {
            {"chat", "database"},
            {"ask", "ai", "database"},
            {"spring", "ai", "chat"},
            {"grounded", "chat"}
        };
    }

    @Override
    public boolean isConfigured() {
        return chatClient != null;
    }

    @Override
    public boolean preserveOriginalQuestion() {
        return true;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Spring AI Chat Agent processing: " + question);

        if (!isConfigured()) {
            return "I'm sorry, the Spring AI Chat feature is not available right now. Please check the OpenAI configuration.";
        }

        try {
            // Optionally retrieve database context for grounding
            String dbContext = "";
            if (jdbcTemplate != null) {
                try {
                    // Example: pull relevant metadata from Oracle to ground the response
                    dbContext = jdbcTemplate.queryForObject(
                        "SELECT 'Database: Oracle ' || version_full FROM product_component_version WHERE ROWNUM = 1",
                        String.class
                    );
                } catch (Exception e) {
                    // DB context is optional — proceed without it
                    dbContext = "";
                }
            }

            String prompt = question;
            if (!dbContext.isEmpty()) {
                prompt = "Context: " + dbContext + "\n\nQuestion: " + question + "\n\nAnswer concisely.";
            }

            ChatResponse response = chatClient.call(new Prompt(prompt));
            String answer = response.getResult().getOutput().getContent();

            System.out.println("Spring AI Chat Agent successfully processed query");
            return answer;

        } catch (Exception e) {
            System.err.println("Error in Spring AI Chat Agent: " + e.getMessage());
            e.printStackTrace();
            return "I'm having difficulty processing your chat request right now. Error: " + e.getMessage();
        }
    }
}
