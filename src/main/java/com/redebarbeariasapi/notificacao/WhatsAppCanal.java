package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.model.CanalNotificacaoTipo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * WhatsApp oficial (Meta Cloud API). Mensagem iniciada pela empresa exige modelo
 * aprovado na Meta: o nome do modelo e os parametros {{1}}, {{2}}... vem da Mensagem.
 * Fica pronto e desligado ate existirem WHATSAPP_TOKEN e WHATSAPP_PHONE_NUMBER_ID.
 */
@Component
public class WhatsAppCanal implements CanalNotificacao {

    private final String token;
    private final String phoneNumberId;
    private final String idioma;
    private final RestClient http;

    public WhatsAppCanal(@Value("${app.whatsapp.token:}") String token,
                         @Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
                         @Value("${app.whatsapp.api-versao:v21.0}") String versao,
                         @Value("${app.whatsapp.idioma:pt_BR}") String idioma) {
        this.token = token;
        this.phoneNumberId = phoneNumberId;
        this.idioma = idioma;
        this.http = RestClient.builder().baseUrl("https://graph.facebook.com/" + versao).build();
    }

    @Override
    public CanalNotificacaoTipo tipo() {
        return CanalNotificacaoTipo.WHATSAPP;
    }

    @Override
    public boolean configurado() {
        return !token.isBlank() && !phoneNumberId.isBlank();
    }

    @Override
    public String destino(Mensagem m) {
        if (m.telefone() == null || m.telefone().isBlank()) return null;
        String d = m.telefone().replaceAll("\\D", "");
        return d.startsWith("55") ? d : "55" + d;
    }

    @Override
    public void enviar(Mensagem m, String destino) {
        List<Map<String, String>> params = m.parametrosWhatsApp().stream()
                .map(p -> Map.of("type", "text", "text", p == null ? "" : p))
                .toList();
        Map<String, Object> corpo = Map.of(
                "messaging_product", "whatsapp",
                "to", destino,
                "type", "template",
                "template", Map.of(
                        "name", m.modeloWhatsApp(),
                        "language", Map.of("code", idioma),
                        "components", List.of(Map.of("type", "body", "parameters", params))));
        try {
            http.post().uri("/{id}/messages", phoneNumberId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String txt = e.getResponseBodyAsString();
            throw new IllegalStateException("Meta respondeu " + e.getStatusCode().value() + ": "
                    + (txt.length() > 300 ? txt.substring(0, 300) : txt));
        }
    }

    @Override
    public String descricao() {
        return configurado() ? "Meta Cloud API · número " + phoneNumberId
                : "Desligado: defina WHATSAPP_TOKEN e WHATSAPP_PHONE_NUMBER_ID (e aprove os modelos na Meta)";
    }
}
