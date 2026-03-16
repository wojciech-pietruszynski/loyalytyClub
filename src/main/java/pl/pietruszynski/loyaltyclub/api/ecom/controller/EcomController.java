package pl.pietruszynski.loyaltyclub.api.ecom.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ecom")
public class EcomController {

    @GetMapping
    public Map<String, String> info() {
        return Map.of(
                "name", "ecom",
                "status", "ready"
        );
    }
}


