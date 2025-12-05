package oracleai.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/status")
public class GetSetController {

    // Simple static string storage without file operations
    private static String simpleValue = "default";
    private static String aiholoType = "default";
    private static String aiholoValue = "default";

    @GetMapping("/simple/set")
    @ResponseBody
    public String setSimpleValue(@RequestParam("value") String value) { 
        simpleValue = value;
        System.out.println("Simple set: " + simpleValue);
        return "Simple value set successfully: " + simpleValue;
    }

    @GetMapping("/simple/get")
    @ResponseBody
    public String getSimpleValue() {
       // System.out.println("Simple get: " + simpleValue);
        return simpleValue;
    }

    // Removed @GetMapping("/aiholo/set") and @GetMapping("/aiholo/get") to avoid conflict with StatusController
    // Use StatusController endpoints instead: /status/aiholo/set and /status/aiholo/get
    
    /**
     * Set aiholo value (for backward compatibility - delegates to StatusController)
     */
    public String setValue(@RequestParam("value") String value,
                          @RequestParam(value = "type", defaultValue = "default") String type) {
        try {
            // URL decode the parameters since they come from GET request
            aiholoValue = URLDecoder.decode(value, StandardCharsets.UTF_8);
            aiholoType = URLDecoder.decode(type, StandardCharsets.UTF_8);
            
            System.out.println("aiholoValue set - value: " + aiholoValue + ", type: " + aiholoType);
            return "Value and type set successfully: value=" + aiholoValue + ", type=" + aiholoType;
        } catch (Exception e) {
            System.err.println("Error decoding parameters: " + e.getMessage());
            return "Error setting values: " + e.getMessage();
        }
    }

    /**
     * Get aiholo value (for backward compatibility - delegates to StatusController)
     */
    public String getValue() {
        // Return JSON format with both type and value
        return "{\"type\":\"" + aiholoType + "\",\"value\":\"" + aiholoValue + "\"}";
    }

    /**
     * Static method for programmatic access to set value (for redirect service)
     */
    public static void setValue(String value) {
        aiholoValue = value;
        aiholoType = "redirect";
        System.out.println("Static setValue called with: " + value);    
    }
}
