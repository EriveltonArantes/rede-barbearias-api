package com.redebarbeariasapi.sistema;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Ronda a cada 5 minutos: banco respondendo, memoria sobrando, lembretes rodando.
 * O que der errado vira alerta pro responsavel (Telegram/e-mail) antes do cliente perceber.
 * (Servidor fora do ar nao consegue avisar ninguem — isso o UptimeRobot cobre por fora.)
 */
@Component
public class Vigia {

    private final SaudeService saudeService;
    private final Saude saude;
    private final AlertaService alertas;

    public Vigia(SaudeService saudeService, Saude saude, AlertaService alertas) {
        this.saudeService = saudeService;
        this.saude = saude;
        this.alertas = alertas;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void ronda() {
        Map<String, Object> banco = saudeService.banco();
        if (Boolean.TRUE.equals(banco.get("ok"))) saude.ok("banco", banco.get("latenciaMs") + " ms");
        else saude.falha("banco", String.valueOf(banco.get("erro")));

        Runtime rt = Runtime.getRuntime();
        long usada = rt.totalMemory() - rt.freeMemory();
        if (usada > rt.maxMemory() * 0.92) {
            System.gc();
            usada = rt.totalMemory() - rt.freeMemory();
            if (usada > rt.maxMemory() * 0.92) {
                alertas.avisar("memoria", "🟠 Memória do servidor quase no limite",
                        (usada / 1024 / 1024) + " de " + (rt.maxMemory() / 1024 / 1024) + " MB. Se continuar, o servidor reinicia sozinho.");
            }
        }

        Saude.Estado lembretes = saude.estado("lembretes");
        if (lembretes != null && lembretes.ultimaVez != null && lembretes.ultimaVez.isBefore(LocalDateTime.now().minusMinutes(20))) {
            alertas.avisar("lembretes-parados", "🔴 Rodada de lembretes parou",
                    "Última execução: " + lembretes.ultimaVez.withNano(0) + ". Os clientes podem não receber o lembrete do horário.");
        }
    }
}
