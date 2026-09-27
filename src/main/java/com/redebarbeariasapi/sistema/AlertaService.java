package com.redebarbeariasapi.sistema;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Avisa o RESPONSAVEL PELO SISTEMA (nao o cliente da barbearia) quando algo quebra:
 * Telegram (bot gratis, chega na hora no celular) e/ou e-mail.
 * O mesmo problema so e avisado de novo depois de 1 hora, pra nao virar spam.
 */
@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);
    private static final Duration SILENCIO = Duration.ofHours(1);
    private static final int HISTORICO = 50;

    public record Alerta(LocalDateTime quando, String titulo, String detalhe, String chave, List<String> entregue) {}

    private final String telegramToken;
    private final String telegramChat;
    private final String email;
    private final String remetente;
    private final String sistema;
    private final String smtpHost;
    private final ObjectProvider<JavaMailSender> mail;
    private final RestClient telegram;
    private final Map<String, LocalDateTime> ultimoAviso = new ConcurrentHashMap<>();
    private final Deque<Alerta> historico = new ArrayDeque<>();
    private final ExecutorService fila = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "alertas");
        t.setDaemon(true);
        return t;
    });

    public AlertaService(@Value("${app.alertas.telegram-token:}") String telegramToken,
                         @Value("${app.alertas.telegram-chat:}") String telegramChat,
                         @Value("${app.alertas.email:}") String email,
                         @Value("${app.notificacoes.remetente:}") String remetente,
                         @Value("${app.alertas.nome-sistema:${spring.application.name:rede-barbearias-api}}") String sistema,
                         @Value("${spring.mail.host:}") String smtpHost,
                         ObjectProvider<JavaMailSender> mail) {
        this.smtpHost = smtpHost == null ? "" : smtpHost.trim();
        this.telegramToken = telegramToken == null ? "" : telegramToken.trim();
        this.telegramChat = telegramChat == null ? "" : telegramChat.trim();
        this.email = email == null ? "" : email.trim();
        this.remetente = remetente == null ? "" : remetente.trim();
        this.sistema = sistema;
        this.mail = mail;
        this.telegram = RestClient.builder().baseUrl("https://api.telegram.org").build();
    }

    public boolean telegramConfigurado() {
        return !telegramToken.isBlank() && !telegramChat.isBlank();
    }

    public boolean emailConfigurado() {
        return !email.isBlank() && podeMandarEmail();
    }

    public boolean algumConfigurado() {
        return telegramConfigurado() || emailConfigurado();
    }

    /** Registra e manda (em segundo plano). chave = identidade do problema pro anti-spam. */
    public void avisar(String chave, String titulo, String detalhe) {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime antes = ultimoAviso.get(chave);
        if (antes != null && antes.plus(SILENCIO).isAfter(agora)) return;
        ultimoAviso.put(chave, agora);
        if (ultimoAviso.size() > 2000) ultimoAviso.clear();
        log.warn("ALERTA {}: {}", titulo, detalhe == null ? "" : detalhe);
        List<String> entregue = Collections.synchronizedList(new ArrayList<>());
        Alerta a = new Alerta(agora, titulo, detalhe, chave, entregue);
        synchronized (historico) {
            historico.addFirst(a);
            while (historico.size() > HISTORICO) historico.removeLast();
        }
        fila.submit(() -> entregar(a));
    }

    /** Envio imediato (botao "testar alerta" do painel): devolve o resultado de cada canal. */
    public List<String> testar() {
        Alerta a = new Alerta(LocalDateTime.now(), "🧪 Teste de alerta", "Se você recebeu isto, os alertas do sistema estão funcionando.", "teste",
                Collections.synchronizedList(new ArrayList<>()));
        List<String> r = entregar(a);
        synchronized (historico) {
            historico.addFirst(a);
            while (historico.size() > HISTORICO) historico.removeLast();
        }
        return r;
    }

    public List<Alerta> recentes() {
        synchronized (historico) {
            return List.copyOf(historico);
        }
    }

    private List<String> entregar(Alerta a) {
        List<String> r = new ArrayList<>();
        String texto = "⚠️ " + sistema + "\n" + a.titulo() + (a.detalhe() == null || a.detalhe().isBlank() ? "" : "\n\n" + a.detalhe())
                + "\n\n🕒 " + a.quando().withNano(0).toString().replace('T', ' ');
        if (telegramConfigurado()) {
            try {
                telegram.post().uri("/bot{t}/sendMessage", telegramToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("chat_id", telegramChat, "text", texto.length() > 3900 ? texto.substring(0, 3900) : texto,
                                "disable_web_page_preview", true))
                        .retrieve().toBodilessEntity();
                r.add("Telegram: enviado");
                a.entregue().add("telegram");
            } catch (Exception e) {
                r.add("Telegram: FALHOU — " + e.getMessage());
                log.warn("Alerta nao foi pro Telegram: {}", e.getMessage());
            }
        }
        if (emailConfigurado()) {
            try {
                JavaMailSender s = mail.getObject();
                MimeMessage msg = s.createMimeMessage();
                MimeMessageHelper h = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());
                h.setFrom(new InternetAddress(remetente, sistema, StandardCharsets.UTF_8.name()));
                h.setTo(email);
                h.setSubject("[" + sistema + "] " + a.titulo());
                h.setText(texto);
                s.send(msg);
                r.add("E-mail: enviado");
                a.entregue().add("email");
            } catch (Exception e) {
                r.add("E-mail: FALHOU — " + e.getMessage());
                log.warn("Alerta nao foi por e-mail: {}", e.getMessage());
            }
        }
        if (r.isEmpty()) r.add("Nenhum canal de alerta configurado (defina ALERTA_TELEGRAM_TOKEN + ALERTA_TELEGRAM_CHAT e/ou ALERTA_EMAIL).");
        return r;
    }

    /** Manda um arquivo pro Telegram (backup semanal). Limite do bot: 50 MB. */
    public void enviarArquivoTelegram(String nome, byte[] dados, String legenda) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("chat_id", telegramChat);
        form.add("caption", legenda.length() > 1000 ? legenda.substring(0, 1000) : legenda);
        form.add("document", new ByteArrayResource(dados) {
            @Override
            public String getFilename() {
                return nome;
            }
        });
        telegram.post().uri("/bot{t}/sendDocument", telegramToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve().toBodilessEntity();
    }

    /** Manda um anexo por e-mail pro responsavel (backup semanal). */
    public void enviarArquivoEmail(String destino, String nome, byte[] dados, String assunto, String corpo) throws Exception {
        JavaMailSender s = mail.getObject();
        MimeMessage msg = s.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
        h.setFrom(new InternetAddress(remetente, sistema, StandardCharsets.UTF_8.name()));
        h.setTo(destino);
        h.setSubject(assunto);
        h.setText(corpo);
        h.addAttachment(nome, new ByteArrayResource(dados));
        s.send(msg);
    }

    public boolean podeMandarEmail() {
        return !smtpHost.isBlank() && !remetente.isBlank() && mail.getIfAvailable() != null;
    }

    public String email() {
        return email;
    }
}
