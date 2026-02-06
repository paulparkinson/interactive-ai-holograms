package oracleai.aiholo.agents;

import oracleai.aiholo.util.OutputFileWriter;
import java.io.IOException;

/**
 * Show Me Navy Ships agent that displays specific Navy ship images.
 * Triggered by "show ship" and searches for ship names in the question.
 */
public class ShowMeNavyShipsAgent implements Agent {
    private final String outputFilePath;
    
    private static final String[] AVAILABLE_SHIPS = {
        "zumwalt",
        "ford",
        "monsoor",
        "john-finn",
        "jack-lucas",
        "tripoli",
        "columbia",
        "virginia",
        "canberra",
        "miguel-keith"
    };
    
    public ShowMeNavyShipsAgent(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }
    
    @Override
    public String getName() {
        return "Show Me Navy Ships Agent";
    }

    @Override
    public String getValueName() {
        return "navyship";
    }

    @Override
    public String[][] getKeywords() {
        // Triggers on "show" AND "ship" together
        return new String[][] {
            {"show", "ship"}
        };
    }

    @Override
    public boolean isConfigured() {
        // This agent doesn't need external configuration
        return true;
    }
    
    @Override
    public boolean handlesOwnFileWriting() {
        return true;  // Writes dynamic ship-specific values
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Show Me Navy Ships Agent processing: " + question);
        
        String lowerQuestion = question.toLowerCase();
        String matchedShip = null;
        
        // Search for any ship name in the question
        for (String ship : AVAILABLE_SHIPS) {
            if (lowerQuestion.contains(ship)) {
                matchedShip = ship;
                break;
            }
        }
        
        if (matchedShip != null) {
            // Ship found - write to output file
            String shipValue = "ship-" + matchedShip;
            try {
                OutputFileWriter.writeData(outputFilePath, shipValue);
                System.out.println("Showing Navy ship: " + matchedShip);
                return "Displaying " + formatShipName(matchedShip) + " now.";
            } catch (IOException e) {
                System.err.println("Error writing ship data to file: " + e.getMessage());
                return "Error displaying ship: " + e.getMessage();
            }
        } else {
            // No ship matched - list available ships
            StringBuilder response = new StringBuilder();
            response.append("I'm afraid there are no ships of that name in the image database. ");
            response.append("Current ships include: ");
            
            for (int i = 0; i < AVAILABLE_SHIPS.length; i++) {
                response.append(formatShipName(AVAILABLE_SHIPS[i]));
                if (i < AVAILABLE_SHIPS.length - 2) {
                    response.append(", ");
                } else if (i == AVAILABLE_SHIPS.length - 2) {
                    response.append(", and ");
                }
            }
            response.append(".");
            
            return response.toString();
        }
    }
    
    /**
     * Formats ship name for display (e.g., "john-finn" -> "John Finn")
     */
    private String formatShipName(String shipName) {
        String[] parts = shipName.split("-");
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            formatted.append(Character.toUpperCase(parts[i].charAt(0)));
            formatted.append(parts[i].substring(1));
            if (i < parts.length - 1) {
                formatted.append(" ");
            }
        }
        return formatted.toString();
    }
}
