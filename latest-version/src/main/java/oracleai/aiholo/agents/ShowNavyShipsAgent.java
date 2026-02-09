package oracleai.aiholo.agents;

import oracleai.aiholo.util.OutputFileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Show Navy Ships agent that displays specific Navy ship images.
 * Triggered by "show ship" and searches for ship names in the question.
 */
public class ShowNavyShipsAgent implements Agent {
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
    
    private static final Map<String, String> SHIP_DESCRIPTIONS = new HashMap<>();
    static {
        SHIP_DESCRIPTIONS.put("zumwalt", "DDG-1000, advanced stealth destroyer with electric propulsion and integrated power system");
        SHIP_DESCRIPTIONS.put("ford", "CVN-78, newest Gerald R. Ford-class supercarrier with electromagnetic aircraft launch system");
        SHIP_DESCRIPTIONS.put("monsoor", "DDG-1001, Zumwalt-class destroyer with advanced weapon systems");
        SHIP_DESCRIPTIONS.put("john-finn", "DDG-113, Arleigh Burke-class guided missile destroyer");
        SHIP_DESCRIPTIONS.put("jack-lucas", "DDG-125, Flight III Arleigh Burke-class destroyer with enhanced radar capabilities");
        SHIP_DESCRIPTIONS.put("tripoli", "LHA-7, America-class amphibious assault ship optimized for aviation operations");
        SHIP_DESCRIPTIONS.put("columbia", "SSBN-826, lead ship of Columbia-class ballistic missile submarines");
        SHIP_DESCRIPTIONS.put("virginia", "SSN-774, Virginia-class fast-attack submarine with advanced stealth");
        SHIP_DESCRIPTIONS.put("canberra", "LCS-30, Independence-class littoral combat ship");
        SHIP_DESCRIPTIONS.put("miguel-keith", "ESB-5, Expeditionary Sea Base for special operations support");
    }
    
    public ShowNavyShipsAgent(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }
    
    @Override
    public String getName() {
        return "Navy Ships Agent";
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
    public boolean hasList() {
        return true;
    }

    @Override
    public String[] getListKeywords() {
        return new String[] {"ship", "ships"};
    }

    @Override
    public String[] getList() {
        String[] list = new String[AVAILABLE_SHIPS.length];
        for (int i = 0; i < AVAILABLE_SHIPS.length; i++) {
            list[i] = formatShipName(AVAILABLE_SHIPS[i]);
        }
        return list;
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Show Navy Ships Agent processing: " + question);
        
        String lowerQuestion = question.toLowerCase();
        
        // Check if user wants to list all ships
        if (lowerQuestion.contains("list")) {
            return listAvailableShips();
        }
        
        String matchedShip = null;
        
        // Search for any ship name in the question
        // Replace dashes with spaces for matching (e.g., "john-finn" matches "john finn")
        for (String ship : AVAILABLE_SHIPS) {
            String searchName = ship.replace("-", " ");
            if (lowerQuestion.contains(searchName)) {
                matchedShip = ship;
                break;
            }
        }
        
        if (matchedShip != null) {
            // Ship found - write to output file
            String shipValue = "ship-" + matchedShip;
            try {
                OutputFileWriter.writeData(outputFilePath, shipValue);
                String description = SHIP_DESCRIPTIONS.get(matchedShip);
                System.out.println("Showing Navy ship: " + matchedShip + " - " + description);
                return "Displaying " + formatShipName(matchedShip) + " now. " + description + ".";
            } catch (IOException e) {
                System.err.println("Error writing ship data to file: " + e.getMessage());
                return "Error displaying ship: " + e.getMessage();
            }
        } else {
            // No ship matched - list available ships
            StringBuilder response = new StringBuilder();
            response.append("I'm afraid there are no ships of that name in the image database. ");
            response.append(listAvailableShips());
            
            return response.toString();
        }
    }
    
    /**
     * Returns a formatted list of available ships
     */
    private String listAvailableShips() {
        StringBuilder response = new StringBuilder();
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
