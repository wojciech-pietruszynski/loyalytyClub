package pl.pietruszynski.loyaltyclub.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping("/")
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}

