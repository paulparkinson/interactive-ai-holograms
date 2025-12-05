package oracleai.common;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class APIController {

    private static String theValue = "default value";

    @GetMapping("/getValue")
    public String getValue() {
        return theValue;
    }

    @GetMapping("/setValue")
    public String setValueViaGet(@RequestParam String newValue) {       
        theValue = newValue;
        return "Value set to: " + theValue;
    }

    /**
     * Public static method to set the value programmatically from other controllers
     */
    public static void setValue(String newValue) {
        theValue = newValue;
        System.out.println("APIController setValue called programmatically with: " + newValue);
    }
}