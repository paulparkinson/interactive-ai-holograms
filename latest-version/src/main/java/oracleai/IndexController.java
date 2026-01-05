package oracleai;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {
    
    private static final String AIHOLO_HOST_URL = oracleai.aiholo.Configuration.getAiholoHostUrl();
    
    @GetMapping("/")
    public String index(Model model) {
        String hostUrl = AIHOLO_HOST_URL != null ? AIHOLO_HOST_URL : "http://localhost:8080";
        model.addAttribute("aiholoHostUrl", hostUrl);
        return "index";
    }
}
