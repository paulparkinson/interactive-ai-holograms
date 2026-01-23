package oracleai;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {
    
    private static final String AIHOLO_HOST_URL = oracleai.aiholo.Configuration.getAiholoHostUrl();
    
    @GetMapping("/")
    public String index(Model model) {
        // Redirect directly to /aiholo instead of using a template
        return "redirect:/aiholo";
    }
}
