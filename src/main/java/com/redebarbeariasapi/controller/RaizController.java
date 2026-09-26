package com.redebarbeariasapi.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** A raiz da API responde algo util em vez de 404. */
@RestController
public class RaizController {

    @GetMapping("/")
    public Map<String, String> raiz() {
        return Map.of(
                "sistema", "Rede Barbearias API",
                "status", "online",
                "documentacao", "/swagger-ui.html",
                "saude", "/actuator/health");
    }
}
