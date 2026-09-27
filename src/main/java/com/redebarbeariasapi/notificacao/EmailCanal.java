package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.model.CanalNotificacaoTipo;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** E-mail por SMTP (Brevo, Gmail com senha de app, SES...). HTML + versao texto. */
@Component
public class EmailCanal implements CanalNotificacao {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String remetente;
    private final String remetenteNome;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.redebarbeariasapi.service.MarcaService marca;

    public EmailCanal(ObjectProvider<JavaMailSender> mailSender,
                      @Value("${spring.mail.host:}") String host,
                      @Value("${app.notificacoes.remetente:}") String remetente,
                      @Value("${app.notificacoes.remetente-nome:}") String remetenteNome) {
        this.mailSender = mailSender;
        this.host = host;
        this.remetente = remetente;
        this.remetenteNome = remetenteNome;
    }

    @Override
    public CanalNotificacaoTipo tipo() {
        return CanalNotificacaoTipo.EMAIL;
    }

    @Override
    public boolean configurado() {
        return !host.isBlank() && !remetente.isBlank() && mailSender.getIfAvailable() != null;
    }

    @Override
    public String destino(Mensagem m) {
        return m.email() == null || m.email().isBlank() ? null : m.email().trim();
    }

    @Override
    public void enviar(Mensagem m, String destino) throws Exception {
        JavaMailSender sender = mailSender.getObject();
        MimeMessage msg = sender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
        h.setFrom(new InternetAddress(remetente, remetenteNome.isBlank() && marca != null ? marca.nome() : remetenteNome, StandardCharsets.UTF_8.name()));
        h.setTo(new InternetAddress(destino, m.nomeCliente(), StandardCharsets.UTF_8.name()));
        h.setSubject(m.assunto());
        h.setText(m.texto(), m.html());
        sender.send(msg);
    }

    @Override
    public String descricao() {
        return configurado() ? "SMTP " + host + " · remetente " + remetente : "Desligado: defina SMTP_HOST, SMTP_USER, SMTP_PASSWORD e MAIL_FROM";
    }
}
