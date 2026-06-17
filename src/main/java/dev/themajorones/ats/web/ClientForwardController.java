package dev.themajorones.ats.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ClientForwardController {

    @GetMapping("/")
    public String forwardRoot() {
        return "forward:/index.html";
    }

    @GetMapping({
        "/ollama",
        "/docker",
        "/android",
        "/artifacts",
        "/tests",
        "/logs"
    })
    public String forwardClientRoutes() {
        return "forward:/index.html";
    }
}
