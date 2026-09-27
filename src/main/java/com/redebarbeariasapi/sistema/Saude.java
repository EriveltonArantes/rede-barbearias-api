package com.redebarbeariasapi.sistema;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Batimentos de cada peca do sistema (rodada de lembretes, WhatsApp, e-mail, Pix, backup...).
 * Cada peca avisa "rodei ok" ou "falhei: motivo"; o painel de saude le daqui e o
 * alerta dispara quando a mesma peca falha varias vezes seguidas.
 */
@Component
public class Saude {

    /** Falhas seguidas da mesma peca antes de avisar o responsavel. */
    public static final int FALHAS_PRA_ALERTAR = 3;

    public static final class Estado {
        public volatile LocalDateTime ultimaVez;
        public volatile LocalDateTime ultimoOk;
        public volatile LocalDateTime ultimaFalha;
        public volatile String ultimoErro;
        public volatile String detalhe;
        public volatile int falhasSeguidas;
        public volatile long total;
        public volatile long falhas;

        Map<String, Object> mapa() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ultimaVez", ultimaVez);
            m.put("ultimoOk", ultimoOk);
            m.put("ultimaFalha", ultimaFalha);
            m.put("ultimoErro", ultimoErro);
            m.put("detalhe", detalhe);
            m.put("falhasSeguidas", falhasSeguidas);
            m.put("total", total);
            m.put("falhas", falhas);
            m.put("ok", falhasSeguidas == 0);
            return m;
        }
    }

    private final Map<String, Estado> pecas = new ConcurrentHashMap<>();
    private final AlertaService alertas;
    public final LocalDateTime iniciadoEm = LocalDateTime.now();

    public Saude(@Lazy AlertaService alertas) {
        this.alertas = alertas;
    }

    public void ok(String peca) {
        ok(peca, null);
    }

    public void ok(String peca, String detalhe) {
        Estado e = pecas.computeIfAbsent(peca, k -> new Estado());
        LocalDateTime agora = LocalDateTime.now();
        boolean voltou = e.falhasSeguidas >= FALHAS_PRA_ALERTAR;
        e.ultimaVez = agora;
        e.ultimoOk = agora;
        e.falhasSeguidas = 0;
        e.total++;
        if (detalhe != null) e.detalhe = detalhe;
        if (voltou) alertas.avisar("voltou:" + peca, "✅ " + nomeLegivel(peca) + " voltou a funcionar", null);
    }

    public void falha(String peca, String erro) {
        Estado e = pecas.computeIfAbsent(peca, k -> new Estado());
        LocalDateTime agora = LocalDateTime.now();
        e.ultimaVez = agora;
        e.ultimaFalha = agora;
        e.ultimoErro = erro == null ? "erro sem mensagem" : (erro.length() > 400 ? erro.substring(0, 400) : erro);
        e.falhasSeguidas++;
        e.total++;
        e.falhas++;
        if (e.falhasSeguidas == FALHAS_PRA_ALERTAR) {
            alertas.avisar("falha:" + peca, "🔴 " + nomeLegivel(peca) + " falhou " + FALHAS_PRA_ALERTAR + " vezes seguidas", e.ultimoErro);
        }
    }

    public Estado estado(String peca) {
        return pecas.get(peca);
    }

    public Map<String, Object> todas() {
        Map<String, Object> m = new TreeMap<>();
        pecas.forEach((k, v) -> m.put(k, v.mapa()));
        return m;
    }

    public static String nomeLegivel(String peca) {
        return switch (peca) {
            case "whatsapp" -> "Envio pelo WhatsApp";
            case "email" -> "Envio de e-mail";
            case "lembretes" -> "Rodada de lembretes automáticos";
            case "sinal" -> "Rotina do sinal por Pix";
            case "relacionamento" -> "Mensagens de aniversário/retorno";
            case "clube" -> "Renovação do clube";
            case "lista-espera" -> "Lista de espera";
            case "backup" -> "Backup automático";
            case "banco" -> "Banco de dados";
            case "webhook-whatsapp" -> "Recebimento de mensagens do WhatsApp";
            case "webhook-pix" -> "Confirmação automática do Pix";
            case "recuperacao-senha" -> "Envio de código de senha";
            default -> peca;
        };
    }
}
