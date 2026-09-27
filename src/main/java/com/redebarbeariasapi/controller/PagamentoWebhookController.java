package com.redebarbeariasapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.redebarbeariasapi.pagamento.SinalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook do Mercado Pago. Nao confia no corpo: pega so o id e consulta o pagamento direto na
 * API do Mercado Pago (com o nosso token) antes de marcar o sinal como pago.
 */
@Tag(name = "Pagamentos — webhook")
@RestController
@RequestMapping("/api/pagamentos/mercadopago/webhook")
public class PagamentoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PagamentoWebhookController.class);
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.redebarbeariasapi.sistema.Saude saude;
    private final SinalService sinal;

    public PagamentoWebhookController(SinalService sinal) {
        this.sinal = sinal;
    }

    @Operation(summary = "Aviso de pagamento do Mercado Pago (tipo payment)")
    @PostMapping
    public ResponseEntity<String> receber(@RequestParam(name = "data.id", required = false) String idQuery,
                                          @RequestParam(name = "id", required = false) String idAntigo,
                                          @RequestBody(required = false) JsonNode corpo) {
        String id = idQuery != null ? idQuery : corpo != null && corpo.path("data").hasNonNull("id") ? corpo.path("data").path("id").asText() : idAntigo;
        String tipo = corpo != null ? corpo.path("type").asText(corpo.path("topic").asText("")) : "";
        if (id != null && (tipo.isEmpty() || tipo.equals("payment"))) {
            try {
                boolean ok = sinal.aoNotificarGateway(id);
                log.info("Webhook Mercado Pago {}: {}", id, ok ? "sinal confirmado" : "nada a fazer");
                if (saude != null) saude.ok("webhook-pix");
            } catch (Exception e) {
                // 200 mesmo assim: a rotina de 5 min pergunta de novo ao gateway
                log.warn("Webhook Mercado Pago {} falhou: {}", id, e.getMessage());
                if (saude != null) saude.falha("webhook-pix", e.getMessage());
            }
        }
        return ResponseEntity.ok("ok");
    }
}
