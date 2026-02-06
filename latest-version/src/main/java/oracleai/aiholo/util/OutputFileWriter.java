package oracleai.aiholo.util;

import org.json.JSONObject;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Utility class for writing data to the aiholo_output.txt file.
 * Centralizes all output file writing logic.
 */
public class OutputFileWriter {
    
    /**
     * Writes a data value to the output file in JSON format.
     * 
     * @param filePath The path to the output file
     * @param dataValue The value to write in the "data" field
     * @throws IOException If file writing fails
     */
    public static void writeData(String filePath, String dataValue) throws IOException {
        String actualPath = filePath != null ? filePath : "aiholo_output.txt";
        try (FileWriter writer = new FileWriter(actualPath)) {
            JSONObject json = new JSONObject();
            json.put("data", dataValue);
            writer.write(json.toString());
            writer.flush();
            System.out.println("Successfully wrote agent value '" + dataValue + "' to " + actualPath);
        }
    }
    
    /**
     * Writes a data value to the default output file (aiholo_output.txt).
     * 
     * @param dataValue The value to write in the "data" field
     * @throws IOException If file writing fails
     */
    public static void writeData(String dataValue) throws IOException {
        writeData(null, dataValue);
    }
}
