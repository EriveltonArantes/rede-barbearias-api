package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.model.CanalNotificacaoTipo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
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
        // cliente respondeu PARAR: nada automatico por WhatsApp
        if (m.whatsappBloqueado() || m.telefone() == null || m.telefone().isBlank()) return null;
        String d = m.telefone().replaceAll("\\D", "");
        return d.startsWith("55") ? d : "55" + d;
    }

    @Override
    public void enviar(Mensagem m, String destino) {
        List<Map<String, String>> params = m.parametrosWhatsApp().stream()
                .map(p -> Map.of("type", "text", "text", p == null ? "" : p))
                .toList();
        List<Map<String, Object>> componentes = new ArrayList<>();
        componentes.add(Map.of("type", "body", "parameters", params));
        // botoes de resposta rapida: o payload volta no webhook quando o cliente toca (CONFIRMAR:codigo...)
        List<String> botoes = m.botoesWhatsApp() == null ? List.of() : m.botoesWhatsApp();
        for (int i = 0; i < botoes.size(); i++) {
            componentes.add(Map.of("type", "button", "sub_type", "quick_reply", "index", String.valueOf(i),
                    "parameters", List.of(Map.of("type", "payload", "payload", botoes.get(i)))));
        }
        Map<String, Object> corpo = Map.of(
                "messaging_product", "whatsapp",
                "to", destino,
                "type", "template",
                "template", Map.of(
                        "name", m.modeloWhatsApp(),
                        "language", Map.of("code", idioma),
                        "components", componentes));
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

    /**
     * Texto livre (sem modelo). A Meta so aceita dentro das 24h depois da ultima mensagem
     * do cliente — e o caso da resposta automatica, que sai logo que ele escreve.
     */
    public void enviarTexto(String destino, String texto) {
        Map<String, Object> corpo = Map.of(
                "messaging_product", "whatsapp",
                "to", destino,
                "type", "text",
                "text", Map.of("body", texto, "preview_url", true));
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
                : "Desligado: ligue o número da barbearia na Meta e defina WHATSAPP_TOKEN e WHATSAPP_PHONE_NUMBER_ID (passo a passo abaixo)";
    }
}
