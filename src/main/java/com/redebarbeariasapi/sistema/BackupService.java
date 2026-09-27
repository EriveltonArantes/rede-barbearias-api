package com.redebarbeariasapi.sistema;

import com.redebarbeariasapi.service.MarcaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Copia de seguranca FORA do servidor: todas as tabelas em CSV (abre no Excel) + as fotos,
 * num .zip. Sai sozinha toda semana pro Telegram e/ou e-mail do responsavel e pode ser
 * baixada no painel a qualquer hora. Senhas nao entram (quem precisar, usa "esqueci a senha").
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter ARQ = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");
    /** Colunas que nunca saem do servidor. */
    private static final Set<String> SENSIVEIS = Set.of("password", "codigo_hash");
    /** Limite do bot do Telegram e da maioria dos e-mails (com folga). */
    private static final int LIMITE_ENVIO = 45 * 1024 * 1024;
    private static final int LIMITE_EMAIL = 20 * 1024 * 1024;

    public record Resultado(String nome, byte[] zip, int tabelas, long linhas, int arquivos) {}

    private final DataSource dataSource;
    private final AlertaService alertas;
    private final Saude saude;
    private final MarcaService marca;
    private final String emailBackup;
    private volatile Map<String, Object> ultimo;

    public BackupService(DataSource dataSource, AlertaService alertas, Saude saude, MarcaService marca,
                         @Value("${app.backup.email:}") String emailBackup) {
        this.dataSource = dataSource;
        this.alertas = alertas;
        this.saude = saude;
        this.marca = marca;
        this.emailBackup = emailBackup == null ? "" : emailBackup.trim();
    }

    /** Pra onde o backup automatico vai (vazio = so da pra baixar pelo painel). */
    public List<String> destinos() {
        List<String> d = new ArrayList<>();
        if (alertas.telegramConfigurado()) d.add("Telegram");
        String email = destinoEmail();
        if (!email.isBlank() && alertas.podeMandarEmail()) d.add("E-mail " + email);
        return d;
    }

    private String destinoEmail() {
        return emailBackup.isBlank() ? alertas.email() : emailBackup;
    }

    public Map<String, Object> ultimo() {
        return ultimo;
    }

    public Resultado gerar() throws Exception {
        String nomeBase = "backup_" + slug(marca.nome()) + "_" + LocalDateTime.now().format(ARQ);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int tabelas = 0, fotos = 0;
        long linhas = 0;
        StringBuilder resumo = new StringBuilder();
        try (Connection c = dataSource.getConnection(); ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            c.setReadOnly(true);
            for (String tabela : tabelas(c)) {
                long n = exportar(c, tabela, zip);
                tabelas++;
                linhas += n;
                resumo.append(String.format("%-32s %8d linhas%n", tabela.toLowerCase(), n));
            }
            fotos = exportarArquivos(c, zip);
            zip.putNextEntry(new ZipEntry("LEIA-ME.txt"));
            zip.write(("Backup de " + marca.nome() + "\nGerado em " + LocalDateTime.now().withNano(0).toString().replace('T', ' ')
                    + "\nBanco: " + c.getMetaData().getDatabaseProductName()
                    + "\n\nCada tabela é um arquivo .csv (separado por ponto e vírgula, abre direto no Excel)."
                    + "\nA pasta arquivos/ tem as fotos e logos enviados pelo painel."
                    + "\nSenhas NÃO fazem parte do backup: depois de uma restauração, cada pessoa usa \"Esqueci minha senha\".\n\n"
                    + resumo + String.format("%nTotal: %d tabelas, %d linhas, %d arquivos%n", tabelas, linhas, fotos))
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new Resultado(nomeBase + ".zip", bytes.toByteArray(), tabelas, linhas, fotos);
    }

    /** Toda semana (domingo 3h por padrao): gera e manda pros destinos configurados. */
    @Scheduled(cron = "${app.backup.cron:0 0 3 * * SUN}", zone = "America/Sao_Paulo")
    public void rotinaSemanal() {
        if (destinos().isEmpty()) {
            log.info("Backup semanal pulado: nenhum destino configurado (Telegram ou e-mail).");
            return;
        }
        enviarAgora();
    }

    /** Gera e manda agora. Devolve o que aconteceu em cada destino. */
    public List<String> enviarAgora() {
        List<String> r = new ArrayList<>();
        Resultado b;
        try {
            b = gerar();
        } catch (Exception e) {
            saude.falha("backup", "não consegui gerar: " + e.getMessage());
            alertas.avisar("backup-gerar", "🔴 Backup não foi gerado", e.toString());
            return List.of("FALHOU ao gerar: " + e.getMessage());
        }
        String legenda = "💾 Backup semanal — " + marca.nome() + "\n" + b.tabelas() + " tabelas · " + b.linhas() + " linhas · "
                + b.arquivos() + " fotos · " + kb(b.zip().length);
        boolean algum = false, falhou = false;
        if (alertas.telegramConfigurado()) {
            if (b.zip().length > LIMITE_ENVIO) {
                r.add("Telegram: arquivo grande demais (" + kb(b.zip().length) + ") — baixe pelo painel");
                falhou = true;
            } else {
                try {
                    alertas.enviarArquivoTelegram(b.nome(), b.zip(), legenda);
                    r.add("Telegram: enviado");
                    algum = true;
                } catch (Exception e) {
                    r.add("Telegram: FALHOU — " + e.getMessage());
                    falhou = true;
                }
            }
        }
        String email = destinoEmail();
        if (!email.isBlank() && alertas.podeMandarEmail()) {
            if (b.zip().length > LIMITE_EMAIL) {
                r.add("E-mail: arquivo grande demais (" + kb(b.zip().length) + ") pra anexo");
                falhou = true;
            } else {
                try {
                    alertas.enviarArquivoEmail(email, b.nome(), b.zip(), "💾 Backup semanal — " + marca.nome(), legenda);
                    r.add("E-mail: enviado para " + email);
                    algum = true;
                } catch (Exception e) {
                    r.add("E-mail: FALHOU — " + e.getMessage());
                    falhou = true;
                }
            }
        }
        if (r.isEmpty()) r.add("Nenhum destino configurado: o backup só pode ser baixado pelo painel.");
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("quando", LocalDateTime.now().withNano(0));
        u.put("arquivo", b.nome());
        u.put("tamanho", kb(b.zip().length));
        u.put("tabelas", b.tabelas());
        u.put("linhas", b.linhas());
        u.put("fotos", b.arquivos());
        u.put("resultado", r);
        u.put("ok", algum && !falhou);
        ultimo = u;
        if (algum && !falhou) saude.ok("backup", b.nome() + " · " + kb(b.zip().length));
        else if (falhou) {
            saude.falha("backup", String.join(" | ", r));
            alertas.avisar("backup-envio", "🔴 Backup semanal não chegou em todos os destinos", String.join("\n", r));
        }
        return r;
    }

    // ---------------------------------------------------------------------------------------------

    private static List<String> tabelas(Connection c) throws SQLException {
        List<String> r = new ArrayList<>();
        String schema = c.getSchema();
        try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) r.add(rs.getString("TABLE_NAME"));
        }
        r.sort(String.CASE_INSENSITIVE_ORDER);
        return r;
    }

    private static long exportar(Connection c, String tabela, ZipOutputStream zip) throws Exception {
        long n = 0;
        String q = c.getMetaData().getIdentifierQuoteString().trim();
        try (Statement st = c.createStatement()) {
            st.setFetchSize(500);
            try (ResultSet rs = st.executeQuery("SELECT * FROM " + q + tabela + q)) {
                ResultSetMetaData md = rs.getMetaData();
                List<Integer> colunas = new ArrayList<>();
                StringBuilder cab = new StringBuilder();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    String nome = md.getColumnName(i).toLowerCase();
                    int tipo = md.getColumnType(i);
                    if (SENSIVEIS.contains(nome) || tipo == Types.BINARY || tipo == Types.VARBINARY
                            || tipo == Types.LONGVARBINARY || tipo == Types.BLOB) continue;
                    colunas.add(i);
                    if (cab.length() > 0) cab.append(';');
                    cab.append(csv(nome));
                }
                zip.putNextEntry(new ZipEntry(tabela.toLowerCase() + ".csv"));
                zip.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // BOM: Excel abre com acento certo
                zip.write((cab + "\r\n").getBytes(StandardCharsets.UTF_8));
                StringBuilder linha = new StringBuilder();
                while (rs.next()) {
                    linha.setLength(0);
                    for (int k = 0; k < colunas.size(); k++) {
                        if (k > 0) linha.append(';');
                        Object v = rs.getObject(colunas.get(k));
                        linha.append(v == null ? "" : csv(String.valueOf(v)));
                    }
                    linha.append("\r\n");
                    zip.write(linha.toString().getBytes(StandardCharsets.UTF_8));
                    n++;
                }
                zip.closeEntry();
            }
        }
        return n;
    }

    private static int exportarArquivos(Connection c, ZipOutputStream zip) {
        int n = 0;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id, nome, dados FROM arquivos ORDER BY id")) {
            while (rs.next()) {
                byte[] d = rs.getBytes("dados");
                if (d == null) continue;
                String nome = String.valueOf(rs.getString("nome")).replaceAll("[^A-Za-z0-9._-]", "_");
                zip.putNextEntry(new ZipEntry("arquivos/" + rs.getLong("id") + "_" + nome));
                zip.write(d);
                zip.closeEntry();
                n++;
            }
        } catch (Exception e) {
            log.warn("Backup sem as fotos: {}", e.getMessage());
        }
        return n;
    }

    private static String csv(String s) {
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static String slug(String s) {
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        t = t.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return t.isBlank() ? "barbearia" : t;
    }

    static String kb(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(1, bytes / 1024) + " KB";
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
