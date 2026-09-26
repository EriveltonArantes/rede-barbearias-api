package com.redebarbeariasapi.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiracao-horas:12}")
    private long expiracaoHoras;

    private SecretKey chave;

    @PostConstruct
    void iniciar() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("jwt.secret precisa ter pelo menos 32 caracteres");
        }
        chave = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String gerarToken(String username, String papel) {
        Date agora = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("papel", papel)
                .issuedAt(agora)
                .expiration(new Date(agora.getTime() + expiracaoHoras * 3_600_000L))
                .signWith(chave)
                .compact();
    }

    /** Username do token, ou vazio se o token for invalido/expirado. */
    public Optional<String> usernameValido(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload();
            return Optional.ofNullable(c.getSubject());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
