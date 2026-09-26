package com.redebarbeariasapi.security;

import com.redebarbeariasapi.exception.LimiteRequisicoesException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite simples por IP (janela deslizante em memoria) pras rotas sem login:
 * agendamento online, cancelamento, avaliacao e tentativa de login.
 * Evita robo lotando a agenda ou forcando senha.
 */
@Component
public class RateLimiter {

    private final int limite;
    private final long janelaMs;
    private final Map<String, Deque<Long>> acessos = new ConcurrentHashMap<>();

    public RateLimiter(@Value("${app.publico.limite-por-janela:30}") int limite,
                       @Value("${app.publico.janela-minutos:10}") int janelaMinutos) {
        this.limite = limite;
        this.janelaMs = janelaMinutos * 60_000L;
    }

    public void verificar(String acao, HttpServletRequest request) {
        String chave = acao + "|" + ip(request);
        long agora = System.currentTimeMillis();
        Deque<Long> fila = acessos.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            while (!fila.isEmpty() && agora - fila.peekFirst() > janelaMs) fila.pollFirst();
            if (fila.size() >= limite) throw new LimiteRequisicoesException();
            fila.addLast(agora);
        }
        if (acessos.size() > 50_000) acessos.clear();
    }

    private static String ip(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
