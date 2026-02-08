package oracleai.aiholo.agents;

import oracleai.aiholo.util.OutputFileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Show Navy Equipment agent that displays specific Navy equipment images.
 * Triggered by "show equipment" and searches for equipment names in the question.
 */
public class ShowNavyEquipmentAgent implements Agent {
    private final String outputFilePath;
    
    private static final String[] AVAILABLE_EQUIPMENT = {
        "link-16",
        "mids-lvt",
        "mids-jtrs",
        "urc-138",
        "bats-d",
        "mids-on-ship"
    };
    
    private static final Map<String, String> EQUIPMENT_DESCRIPTIONS = new HashMap<>();
    static {
        EQUIPMENT_DESCRIPTIONS.put("link-16", "Tactical data exchange network for NATO and coalition forces");
        EQUIPMENT_DESCRIPTIONS.put("mids-lvt", "Multifunctional Information Distribution System - Low Volume Terminal for tactical data links");
        EQUIPMENT_DESCRIPTIONS.put("mids-jtrs", "MIDS Joint Tactical Radio System with enhanced waveform capabilities");
        EQUIPMENT_DESCRIPTIONS.put("urc-138", "Software-defined radio system for multi-band communications");
        EQUIPMENT_DESCRIPTIONS.put("bats-d", "Battlefield Air Targeting System - Delta for terminal attack control");
        EQUIPMENT_DESCRIPTIONS.put("mids-on-ship", "Ship-mounted MIDS terminal for integrated tactical communications");
    }
    
    public ShowNavyEquipmentAgent(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }
    
    @Override
    public String getName() {
        return "Show Navy Equipment Agent";
    }

    @Override
    public String getValueName() {
        return "navyequipment";
    }

    @Override
    public String[][] getKeywords() {
        // Triggers on "show" AND "equipment" together
        return new String[][] {
            {"show", "equipment"}
        };
    }

    @Override
    public boolean isConfigured() {
        // This agent doesn't need external configuration
        return true;
    }
    
    @Override
    public boolean handlesOwnFileWriting() {
        return true;  // Writes dynamic equipment-specific values
    }

    @Override
    public String processQuestion(String question) {
        System.out.println("Show Navy Equipment Agent processing: " + question);
        
        String lowerQuestion = question.toLowerCase();
        
        // Check if user wants to list all equipment
        if (lowerQuestion.contains("list")) {
            return listAvailableEquipment();
        }
        
        String matchedEquipment = null;
        
        // Search for any equipment name in the question
        // Replace dashes with spaces for matching (e.g., "link-16" matches "link 16")
        for (String equipment : AVAILABLE_EQUIPMENT) {
            String searchName = equipment.replace("-", " ");
            if (lowerQuestion.contains(searchName)) {
                matchedEquipment = equipment;
                break;
            }
        }
        
        if (matchedEquipment != null) {
            // Equipment found - write to output file
            String equipmentValue = "equipment-" + matchedEquipment;
            try {
                OutputFileWriter.writeData(outputFilePath, equipmentValue);
                String description = EQUIPMENT_DESCRIPTIONS.get(matchedEquipment);
                System.out.println("Showing Navy equipment: " + matchedEquipment + " - " + description);
                return "Displaying " + formatEquipmentName(matchedEquipment) + " now. " + description + ".";
            } catch (IOException e) {
                System.err.println("Error writing equipment data to file: " + e.getMessage());
                return "Error displaying equipment: " + e.getMessage();
            }
        } else {
            // No equipment matched - list available equipment
            StringBuilder response = new StringBuilder();
            response.append("I'm afraid there is no equipment of that name in the image database. ");
            response.append(listAvailableEquipment());
            
            return response.toString();
        }
    }
    
    /**
     * Returns a formatted list of available equipment
     */
    private String listAvailableEquipment() {
        StringBuilder response = new StringBuilder();
        response.append("Current equipment includes: ");
        
        for (int i = 0; i < AVAILABLE_EQUIPMENT.length; i++) {
            response.append(formatEquipmentName(AVAILABLE_EQUIPMENT[i]));
            if (i < AVAILABLE_EQUIPMENT.length - 2) {
                response.append(", ");
            } else if (i == AVAILABLE_EQUIPMENT.length - 2) {
                response.append(", and ");
            }
        }
        response.append(".");
        
        return response.toString();
    }
    
    /**
     * Formats equipment name for display (e.g., "link-16" -> "Link 16", "mids-lvt" -> "MIDS LVT")
     */
    private String formatEquipmentName(String equipmentName) {
        String[] parts = equipmentName.split("-");
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            // Keep acronyms uppercase
            if (parts[i].equals("mids") || parts[i].equals("lvt") || parts[i].equals("jtrs") || 
                parts[i].equals("urc") || parts[i].equals("bats") || parts[i].equals("d")) {
                formatted.append(parts[i].toUpperCase());
            } else {
                formatted.append(Character.toUpperCase(parts[i].charAt(0)));
                formatted.append(parts[i].substring(1));
            }
            if (i < parts.length - 1) {
                formatted.append(" ");
            }
        }
        return formatted.toString();
    }
}
