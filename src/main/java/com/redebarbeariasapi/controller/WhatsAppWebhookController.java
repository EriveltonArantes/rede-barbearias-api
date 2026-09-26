package com.redebarbeariasapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redebarbeariasapi.notificacao.AtendimentoWhatsAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Webhook do WhatsApp oficial (Meta). Na Meta: Callback URL = {api}/api/whatsapp/webhook,
 * Verify token = WHATSAPP_VERIFY_TOKEN, e assinar o campo "messages".
 * Toda chamada POST precisa vir assinada com o App Secret (WHATSAPP_APP_SECRET) — sem isso
 * qualquer um poderia fazer o sistema mandar mensagens em nome da barbearia.
 */
@Tag(name = "WhatsApp — webhook da Meta")
@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final AtendimentoWhatsAppService atendimento;
    private final ObjectMapper json;
    private final String verifyToken;
    private final String appSecret;

    public WhatsAppWebhookController(AtendimentoWhatsAppService atendimento, ObjectMapper json,
                                     @Value("${app.whatsapp.verify-token:}") String verifyToken,
                                     @Value("${app.whatsapp.app-secret:}") String appSecret) {
        this.atendimento = atendimento;
        this.json = json;
        this.verifyToken = verifyToken;
        this.appSecret = appSecret;
    }

    @Operation(summary = "Verificação do webhook (a Meta chama uma vez ao cadastrar a URL)")
    @GetMapping
    public ResponseEntity<String> verificar(@RequestParam(name = "hub.mode", required = false) String modo,
                                            @RequestParam(name = "hub.verify_token", required = false) String token,
                                            @RequestParam(name = "hub.challenge", required = false) String desafio) {
        if (!verifyToken.isBlank() && "subscribe".equals(modo) && iguais(verifyToken, token) && desafio != null) {
            return ResponseEntity.ok(desafio);
        }
        return ResponseEntity.status(403).body("verify token inválido");
    }

    @Operation(summary = "Mensagens recebidas (a Meta chama a cada mensagem de cliente)")
    @PostMapping
    public ResponseEntity<String> receber(@RequestBody byte[] corpo,
                                          @RequestHeader(name = "X-Hub-Signature-256", required = false) String assinatura) {
        if (appSecret.isBlank()) {
            log.warn("Webhook do WhatsApp chamado mas WHATSAPP_APP_SECRET não está configurado — ignorado");
            return ResponseEntity.status(403).body("app secret não configurado");
        }
        if (!assinaturaValida(corpo, assinatura)) {
            return ResponseEntity.status(403).body("assinatura inválida");
        }
        try {
            JsonNode raiz = json.readTree(corpo);
            for (JsonNode entry : raiz.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    JsonNode valor = change.path("value");
                    // status de entrega/leitura tambem chegam aqui, sem "messages": nada a fazer
                    for (JsonNode msg : valor.path("messages")) {
                        String de = msg.path("from").asText(null);
                        String nome = null;
                        for (JsonNode contato : valor.path("contacts")) {
                            if (de != null && de.equals(contato.path("wa_id").asText())) nome = contato.path("profile").path("name").asText(null);
                        }
                        String texto = switch (msg.path("type").asText("")) {
                            case "text" -> msg.path("text").path("body").asText("");
                            case "button" -> msg.path("button").path("text").asText("");
                            case "interactive" -> msg.path("interactive").path("button_reply").path("title").asText("");
                            default -> "(" + msg.path("type").asText("mensagem") + ")";
                        };
                        String payload = switch (msg.path("type").asText("")) {
                            case "button" -> msg.path("button").path("payload").asText(null);
                            case "interactive" -> msg.path("interactive").path("button_reply").path("id").asText(null);
                            default -> null;
                        };
                        var r = atendimento.receber(msg.path("id").asText(null), de, nome, texto, payload, LocalDateTime.now());
                        log.info("WhatsApp de {}: {}", de, r.motivo());
                    }
                }
            }
        } catch (Exception e) {
            // responde 200 mesmo assim: se devolver erro a Meta fica reenviando a mesma mensagem
            log.warn("Webhook do WhatsApp com conteúdo inesperado: {}", e.getMessage());
        }
        return ResponseEntity.ok("ok");
    }

    private boolean assinaturaValida(byte[] corpo, String cabecalho) {
        if (cabecalho == null || !cabecalho.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String esperado = "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo));
            return iguais(esperado, cabecalho.toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean iguais(String a, String b) {
        return b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
