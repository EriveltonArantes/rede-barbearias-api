package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.model.Marca;
import com.redebarbeariasapi.service.MarcaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Identidade da barbearia (nome, logo, cores) e o "app" instalavel no celular. */
@Tag(name = "Marca e app no celular")
@RestController
public class MarcaController {

    private final MarcaService marca;
    private final String apiUrl;

    public MarcaController(MarcaService marca, @Value("${app.api-url:}") String apiUrl) {
        this.marca = marca;
        this.apiUrl = apiUrl == null ? "" : apiUrl.replaceAll("/+$", "");
    }

    @Operation(summary = "Nome, logo, cores e contatos (o site e o painel se pintam com isso)")
    @GetMapping("/api/publico/marca")
    public ResponseEntity<Map<String, Object>> publica() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.MINUTES).cachePublic()).body(marca.publico());
    }

    @Operation(summary = "Manifesto do app (PWA): nome e ícone que aparecem no celular")
    @GetMapping(value = "/api/publico/manifest.webmanifest", produces = "application/manifest+json")
    public ResponseEntity<Map<String, Object>> manifesto(@RequestParam(required = false) String origem) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.parseMediaType("application/manifest+json"))
                .body(marca.manifesto(origem, base()));
    }

    @Operation(summary = "Ícone do app (logo enviada ou as iniciais nas cores da marca)")
    @GetMapping(value = "/api/publico/icone/{tamanho}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> icone(@PathVariable int tamanho, @RequestParam(defaultValue = "false") boolean maskable) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.IMAGE_PNG).body(marca.icone(tamanho, maskable));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @GetMapping("/api/configuracoes/marca")
    public Marca atual() {
        return marca.atual();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/api/configuracoes/marca")
    public Marca salvar(@RequestBody Map<String, Object> corpo) {
        return marca.salvar(corpo);
    }

    /** Endereco publico desta API (o Render fica atras de proxy https). */
    private String base() {
        if (!apiUrl.isBlank()) return apiUrl;
        String b = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return b.startsWith("http://") && !b.contains("localhost") && !b.contains("127.0.0.1") ? "https://" + b.substring(7) : b;
    }
}
