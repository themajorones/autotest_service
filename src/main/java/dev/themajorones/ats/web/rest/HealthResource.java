package dev.themajorones.ats.web.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthResource {
    
    @GetMapping("/health")
    public String health() {
        return "OK";
    }

}