package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.*;
import com.redebarbeariasapi.security.RateLimiter;
import com.redebarbeariasapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Autenticação")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;
    private final RateLimiter rateLimiter;

    @Operation(summary = "Login — devolve o token JWT e o papel do usuário")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO dto, HttpServletRequest req) {
        rateLimiter.verificar("login", req);
        return service.login(dto)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("erro", "Usuário ou senha inválidos.")));
    }

    @Operation(summary = "Cliente cria a própria conta (sempre papel CLIENTE)")
    @PostMapping("/registrar")
    public ResponseEntity<LoginResponseDTO> registrar(@Valid @RequestBody RegistroClienteDTO dto, HttpServletRequest req) {
        rateLimiter.verificar("registrar", req);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrarCliente(dto));
    }

    @Operation(summary = "Dados do usuário logado")
    @GetMapping("/me")
    public LoginResponseDTO eu() {
        return service.eu();
    }

    @Operation(summary = "Trocar a própria senha")
    @PostMapping("/trocar-senha")
    public ResponseEntity<Void> trocarSenha(@Valid @RequestBody TrocarSenhaDTO dto) {
        service.trocarSenha(dto);
        return ResponseEntity.noContent().build();
    }
}
