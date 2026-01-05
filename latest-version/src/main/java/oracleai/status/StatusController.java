package oracleai.status;

import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@RestController
@RequestMapping("/status/aiholo")
public class StatusController {
    
    private static String statusValue = "default";
    private static final String STATUS_FILE_PATH = oracleai.aiholo.Configuration.getStatusFilePath();
    
    /**
     * Get the current status value
     * GET http://localhost:8080/status/aiholo/get
     */
    @GetMapping("/get")
    @ResponseBody
    public String getStatus() {
        System.out.println("StatusController get: " + statusValue);
        
        // If STATUS_FILE_PATH is set, try to read from file
        if (STATUS_FILE_PATH != null) {
            try {
                String fileContent = new String(Files.readAllBytes(Paths.get(STATUS_FILE_PATH)));
                JSONObject json = new JSONObject(fileContent);
                statusValue = json.getString("data");
                System.out.println("Read status from file: " + statusValue);
            } catch (Exception e) {
                System.err.println("Could not read status from file: " + e.getMessage());
            }
        }
        
        return statusValue;
    }
    
    /**
     * Set the status value
     * GET http://localhost:8080/status/aiholo/set?value=myvalue
     */
    @GetMapping("/set")
    @ResponseBody
    public String setStatus(@RequestParam("value") String value) {
        return setStatusValueStatic(value);
    }
    
    /**
     * Static method to set status value from other classes (like agents)
     */
    public static String setStatusValueStatic(String value) {
        statusValue = value;
        System.out.println("StatusController set: " + statusValue);
        
        // If STATUS_FILE_PATH is set, write to file
        if (STATUS_FILE_PATH != null) {
            try (FileWriter writer = new FileWriter(STATUS_FILE_PATH)) {
                JSONObject json = new JSONObject();
                json.put("data", value);
                writer.write(json.toString());
                writer.flush();
                System.out.println("Wrote status to file: " + STATUS_FILE_PATH);
            } catch (IOException e) {
                System.err.println("Error writing status to file: " + e.getMessage());
                return "Error writing to file: " + e.getMessage();
            }
        }
        
        return "Status set successfully: " + statusValue;
    }
}
