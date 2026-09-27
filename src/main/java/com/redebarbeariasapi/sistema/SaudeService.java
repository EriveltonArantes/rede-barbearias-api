package com.redebarbeariasapi.sistema;

import com.redebarbeariasapi.model.CanalNotificacaoTipo;
import com.redebarbeariasapi.model.Papel;
import com.redebarbeariasapi.model.StatusNotificacao;
import com.redebarbeariasapi.notificacao.EmailCanal;
import com.redebarbeariasapi.notificacao.WhatsAppCanal;
import com.redebarbeariasapi.pagamento.GatewayPix;
import com.redebarbeariasapi.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Tudo que o responsavel precisa ver em 10 segundos: esta no ar? banco ok? WhatsApp,
 * e-mail e Pix funcionando? lembretes rodando? backup saindo? alguma configuracao perigosa?
 */
@Service
public class SaudeService {

    public record Item(String nivel, String titulo, String detalhe) {}

    private final Saude saude;
    private final AlertaService alertas;
    private final BackupService backup;
    private final DataSource dataSource;
    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final AgendamentoRepository agendamentos;
    private final NotificacaoRepository notificacoes;
    private final RecuperacaoSenhaRepository recuperacoes;
    private final PasswordEncoder encoder;
    private final WhatsAppCanal whatsapp;
    private final EmailCanal email;
    private final GatewayPix gateway;
    private final String jwtSecret;
    private final String siteUrl;
    private final String webhookSecret;
    private final String versao;

    public SaudeService(Saude saude, AlertaService alertas, BackupService backup, DataSource dataSource,
                        UsuarioRepository usuarios, ClienteRepository clientes, AgendamentoRepository agendamentos,
                        NotificacaoRepository notificacoes, RecuperacaoSenhaRepository recuperacoes, PasswordEncoder encoder,
                        WhatsAppCanal whatsapp, EmailCanal email, GatewayPix gateway,
                        @Value("${jwt.secret}") String jwtSecret,
                        @Value("${app.site-url}") String siteUrl,
                        @Value("${app.whatsapp.app-secret:}") String webhookSecret,
                        @Value("${app.versao:${RENDER_GIT_COMMIT:local}}") String versao) {
        this.saude = saude;
        this.alertas = alertas;
        this.backup = backup;
        this.dataSource = dataSource;
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.agendamentos = agendamentos;
        this.notificacoes = notificacoes;
        this.recuperacoes = recuperacoes;
        this.encoder = encoder;
        this.whatsapp = whatsapp;
        this.email = email;
        this.gateway = gateway;
        this.jwtSecret = jwtSecret;
        this.siteUrl = siteUrl;
        this.webhookSecret = webhookSecret;
        this.versao = versao == null || versao.length() < 7 ? versao : versao.substring(0, 7);
    }

    /** Checagem rapida do banco (usada pelo painel e pela vigia). */
    public Map<String, Object> banco() {
        Map<String, Object> b = new LinkedHashMap<>();
        long t0 = System.nanoTime();
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            String produto = c.getMetaData().getDatabaseProductName();
            st.execute("SELECT 1");
            b.put("ok", true);
            b.put("latenciaMs", (System.nanoTime() - t0) / 1_000_000);
            b.put("tipo", produto);
            b.put("temporario", produto.toLowerCase().contains("h2"));
            if (produto.toLowerCase().contains("postgres")) {
                try (ResultSet rs = st.executeQuery("SELECT pg_database_size(current_database())")) {
                    if (rs.next()) b.put("tamanhoMb", Math.round(rs.getLong(1) / 1024.0 / 1024.0 * 10) / 10.0);
                }
            }
        } catch (Exception e) {
            b.put("ok", false);
            b.put("erro", e.getMessage());
        }
        return b;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> painel() {
        LocalDateTime agora = LocalDateTime.now();
        Map<String, Object> r = new LinkedHashMap<>();
        Runtime rt = Runtime.getRuntime();
        long usada = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long max = rt.maxMemory() / 1024 / 1024;

        Map<String, Object> sistema = new LinkedHashMap<>();
        sistema.put("versao", versao);
        sistema.put("iniciadoEm", saude.iniciadoEm.withNano(0));
        sistema.put("noArHa", duracao(Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime())));
        sistema.put("memoriaMb", usada);
        sistema.put("memoriaMaxMb", max);
        sistema.put("java", System.getProperty("java.version"));
        sistema.put("siteUrl", siteUrl);
        r.put("sistema", sistema);

        Map<String, Object> banco = banco();
        if (Boolean.TRUE.equals(banco.get("ok"))) {
            banco.put("clientes", clientes.count());
            banco.put("agendamentos", agendamentos.count());
        }
        r.put("banco", banco);

        LocalDateTime dia = agora.minusHours(24);
        Map<String, Object> canais = new LinkedHashMap<>();
        canais.put("whatsapp", canal(whatsapp.configurado(), whatsapp.descricao(), "whatsapp",
                notificacoes.countByCanalAndStatusAndDataHoraAfter(CanalNotificacaoTipo.WHATSAPP, StatusNotificacao.ENVIADA, dia),
                notificacoes.countByCanalAndStatusAndDataHoraAfter(CanalNotificacaoTipo.WHATSAPP, StatusNotificacao.FALHOU, dia)));
        canais.put("email", canal(email.configurado(), email.descricao(), "email",
                notificacoes.countByCanalAndStatusAndDataHoraAfter(CanalNotificacaoTipo.EMAIL, StatusNotificacao.ENVIADA, dia),
                notificacoes.countByCanalAndStatusAndDataHoraAfter(CanalNotificacaoTipo.EMAIL, StatusNotificacao.FALHOU, dia)));
        Map<String, Object> pix = new LinkedHashMap<>();
        pix.put("configurado", gateway.configurado());
        pix.put("descricao", gateway.configurado() ? gateway.nome() + " (confirma o sinal sozinho)" : "Pix estático — a recepção confere o pagamento");
        canais.put("pix", pix);
        r.put("canais", canais);

        r.put("tarefas", saude.todas());

        Map<String, Object> bk = new LinkedHashMap<>();
        bk.put("destinos", backup.destinos());
        bk.put("ultimo", backup.ultimo());
        r.put("backup", bk);

        Map<String, Object> al = new LinkedHashMap<>();
        al.put("telegram", alertas.telegramConfigurado());
        al.put("email", alertas.emailConfigurado());
        al.put("recentes", alertas.recentes());
        r.put("alertas", al);

        r.put("senhasPendentes", recuperacoes.pendentes(dia).size());
        r.put("verificacoes", verificacoes(banco, agora));
        return r;
    }

    /** Lista de "semaforo": o que esta bom, o que merece atencao e o que e perigoso. */
    private List<Item> verificacoes(Map<String, Object> banco, LocalDateTime agora) {
        List<Item> v = new ArrayList<>();
        if (!Boolean.TRUE.equals(banco.get("ok"))) {
            v.add(new Item("erro", "Banco de dados fora do ar", String.valueOf(banco.get("erro"))));
        } else if (Boolean.TRUE.equals(banco.get("temporario"))) {
            v.add(new Item("erro", "Banco temporário (H2 em memória)", "Tudo que for cadastrado some quando o servidor reiniciar. Defina DATABASE_URL (Neon ou Postgres do Render)."));
        } else {
            v.add(new Item("ok", "Banco de dados permanente", banco.get("tipo") + " · " + banco.get("latenciaMs") + " ms"
                    + (banco.get("tamanhoMb") != null ? " · " + banco.get("tamanhoMb") + " MB" : "")));
        }
        if (jwtSecret.startsWith("dev-only")) {
            v.add(new Item("erro", "Chave de login (JWT_SECRET) é a de desenvolvimento", "Qualquer pessoa que conheça o código conseguiria forjar um login. Defina JWT_SECRET no servidor."));
        }
        boolean adminPadrao = usuarios.findByPapelAndAtivoTrue(Papel.ADMIN).stream().anyMatch(u -> encoder.matches("admin123", u.getPassword()));
        if (adminPadrao) {
            v.add(new Item("aviso", "Tem administrador com a senha padrão (admin123)", "Troque em Minha conta antes de entregar pro cliente."));
        }
        if (!alertas.algumConfigurado()) {
            v.add(new Item("aviso", "Alertas desligados", "Ninguém fica sabendo se algo quebrar. Configure o bot do Telegram (ALERTA_TELEGRAM_TOKEN + ALERTA_TELEGRAM_CHAT) ou ALERTA_EMAIL."));
        } else {
            v.add(new Item("ok", "Alertas ligados", (alertas.telegramConfigurado() ? "Telegram " : "") + (alertas.emailConfigurado() ? "E-mail" : "")));
        }
        if (backup.destinos().isEmpty()) {
            v.add(new Item("aviso", "Backup semanal sem destino", "O backup só pode ser baixado à mão. Com o Telegram configurado ele sai sozinho todo domingo."));
        } else {
            Map<String, Object> u = backup.ultimo();
            v.add(new Item(u == null || Boolean.TRUE.equals(u.get("ok")) ? "ok" : "erro", "Backup semanal",
                    u == null ? "Vai para " + String.join(" e ", backup.destinos()) + " (ainda não rodou desde que o servidor ligou)"
                            : "Último: " + u.get("quando") + " · " + u.get("tamanho")));
        }
        if (!whatsapp.configurado()) {
            v.add(new Item("info", "WhatsApp oficial desligado", "Lembretes e respostas automáticas saem só depois de ligar o número na Meta."));
        } else if (webhookSecret == null || webhookSecret.isBlank()) {
            v.add(new Item("aviso", "WhatsApp sem WHATSAPP_APP_SECRET", "A resposta automática fica desligada até o App Secret da Meta ser configurado."));
        }
        if (!email.configurado()) {
            v.add(new Item("info", "E-mail desligado", "Sem SMTP, confirmações e códigos de senha vão só pelo WhatsApp."));
        }
        if (!whatsapp.configurado() && !email.configurado()) {
            v.add(new Item("aviso", "\"Esqueci minha senha\" depende da equipe", "Sem WhatsApp oficial nem e-mail, o código não sai sozinho: o pedido aparece em Usuários e a recepção manda pelo WhatsApp."));
        }
        Saude.Estado lembretes = saude.estado("lembretes");
        if (lembretes != null && lembretes.ultimaVez != null && lembretes.ultimaVez.isBefore(agora.minusMinutes(20))) {
            v.add(new Item("erro", "Rodada de lembretes parada", "Última vez: " + lembretes.ultimaVez.withNano(0)));
        }
        saude.todas().forEach((peca, estado) -> {
            @SuppressWarnings("unchecked") Map<String, Object> e = (Map<String, Object>) estado;
            int falhas = (int) e.get("falhasSeguidas");
            if (falhas > 0) v.add(new Item(falhas >= Saude.FALHAS_PRA_ALERTAR ? "erro" : "aviso",
                    Saude.nomeLegivel(peca) + " com falha", falhas + " seguida(s): " + e.get("ultimoErro")));
        });
        long usada = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        if (usada > Runtime.getRuntime().maxMemory() * 0.9) {
            v.add(new Item("aviso", "Memória quase no limite", "Se continuar assim o servidor pode reiniciar sozinho. Considere um plano com mais memória."));
        }
        Map<String, Integer> ordem = Map.of("erro", 0, "aviso", 1, "info", 2, "ok", 3);
        v.sort(Comparator.comparing(i -> ordem.getOrDefault(i.nivel(), 9)));
        return v;
    }

    private Map<String, Object> canal(boolean configurado, String descricao, String peca, long enviadas, long falhas) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configurado", configurado);
        m.put("descricao", descricao);
        m.put("enviadas24h", enviadas);
        m.put("falhas24h", falhas);
        Saude.Estado e = saude.estado(peca);
        m.put("ultimoOk", e == null ? null : e.ultimoOk);
        m.put("ultimoErro", e == null ? null : e.ultimoErro);
        return m;
    }

    static String duracao(Duration d) {
        long dias = d.toDays(), horas = d.toHoursPart(), min = d.toMinutesPart();
        if (dias > 0) return dias + "d " + horas + "h";
        if (horas > 0) return horas + "h " + min + "min";
        return min + " min";
    }
}
