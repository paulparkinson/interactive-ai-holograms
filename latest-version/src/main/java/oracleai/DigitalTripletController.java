package oracleai;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/digitaltriplets")
public class DigitalTripletController {

    @GetMapping("")
    public String home(Model model) {
        return "digitaltriplets";
    }
}
