package com.redebarbeariasapi.pagamento;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pix do Mercado Pago (API de pagamentos v1). Liga com MERCADOPAGO_ACCESS_TOKEN.
 * Webhook: {api}/api/pagamentos/mercadopago/webhook — informado em cada cobranca (notification_url).
 */
@Component
public class MercadoPagoPix implements GatewayPix {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final String token;
    private final String apiUrl;
    private final String emailPadrao;
    private final RestClient http;

    public MercadoPagoPix(@Value("${app.mercadopago.access-token:}") String token,
                          @Value("${app.api-url:}") String apiUrl,
                          @Value("${app.mercadopago.email-padrao:pagamentos@redebarbearias.com.br}") String emailPadrao) {
        this.token = token;
        this.apiUrl = apiUrl.replaceAll("/+$", "");
        this.emailPadrao = emailPadrao;
        this.http = RestClient.builder().baseUrl("https://api.mercadopago.com").build();
    }

    @Override
    public boolean configurado() {
        return !token.isBlank();
    }

    @Override
    public String nome() {
        return "Mercado Pago";
    }

    @Override
    public Cobranca criar(BigDecimal valor, String descricao, String emailPagador, String referencia, LocalDateTime expiraEm) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("transaction_amount", valor);
        corpo.put("description", descricao);
        corpo.put("payment_method_id", "pix");
        corpo.put("external_reference", referencia);
        corpo.put("payer", Map.of("email", emailPagador == null || emailPagador.isBlank() ? emailPadrao : emailPagador));
        // o Mercado Pago exige no minimo 30 min de validade
        LocalDateTime exp = expiraEm.isBefore(LocalDateTime.now().plusMinutes(31)) ? LocalDateTime.now().plusMinutes(31) : expiraEm;
        corpo.put("date_of_expiration", exp.atZone(ZoneId.of("America/Sao_Paulo")).format(ISO));
        if (!apiUrl.isBlank()) corpo.put("notification_url", apiUrl + "/api/pagamentos/mercadopago/webhook");
        try {
            JsonNode r = http.post().uri("/v1/payments")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Idempotency-Key", referencia + "-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode dados = r.path("point_of_interaction").path("transaction_data");
            return new Cobranca(r.path("id").asText(), dados.path("qr_code").asText(null), dados.path("qr_code_base64").asText(null));
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Mercado Pago recusou a cobrança (" + e.getStatusCode().value() + "): "
                    + corta(e.getResponseBodyAsString()));
        }
    }

    @Override
    public Situacao consultar(String id) {
        try {
            JsonNode r = http.get().uri("/v1/payments/{id}", id)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
            return new Situacao(r.path("id").asText(), "approved".equals(r.path("status").asText()),
                    r.path("external_reference").asText(null),
                    r.hasNonNull("transaction_amount") ? r.path("transaction_amount").decimalValue() : null);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Mercado Pago não respondeu a consulta (" + e.getStatusCode().value() + ")");
        }
    }

    private static String corta(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) : s;
    }
}
