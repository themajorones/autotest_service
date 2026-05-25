package dev.themajorones.ats.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ClientForwardController {

    @GetMapping("/{path:[^\\.]*}")
    public String forwardClientRoutes() {
        return "forward:/index.html";
    }
}
