package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.model.Agendamento;
import com.redebarbeariasapi.model.TipoNotificacao;
import com.redebarbeariasapi.service.Textos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Textos das mensagens. Os modelos do WhatsApp (MODELOS) precisam ser cadastrados
 * na Meta com exatamente esse nome e a mesma ordem de parametros {{1}}, {{2}}...
 */
@Component
public class MensagemFactory {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", BR);

    /** Nome do modelo -> texto a cadastrar na Meta (categoria "Utilidade", idioma pt_BR). */
    public static final Map<String, String> MODELOS = new LinkedHashMap<>();
    static {
        MODELOS.put("agendamento_confirmado", "Olá, {{1}}! Seu horário está confirmado ✅\n\n📅 {{2}} às {{3}}\n✂️ {{4}} com {{5}}\n📍 {{6}}\n\nCódigo: {{7}}\nPara consultar ou cancelar: {{8}}");
        MODELOS.put("lembrete_agendamento", "Bom dia, {{1}}! Não esqueça: hoje às {{2}} tem {{3}} com {{4}} 💈\n📍 {{5}}\n\nPrecisa remarcar ou cancelar? {{6}}");
        MODELOS.put("agendamento_alterado", "{{1}}, seu horário foi alterado 🔁\n\n📅 {{2}} às {{3}}\n✂️ {{4}} com {{5}}\n📍 {{6}}\n\nDetalhes: {{7}}");
        MODELOS.put("agendamento_cancelado", "{{1}}, seu horário de {{2}} às {{3}} na {{4}} foi cancelado. Quando quiser, agende de novo: {{5}}");
        MODELOS.put("avaliacao_atendimento", "Obrigado pela visita, {{1}}! 💈 Como foi seu {{2}} com {{3}}? Avalie em 10 segundos: {{4}}");
    }

    private final String siteUrl;

    public MensagemFactory(@Value("${app.site-url}") String siteUrl) {
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    public Mensagem criar(Agendamento a, TipoNotificacao tipo) {
        String nome = primeiroNome(a.getCliente().getNome());
        String dia = DIA.format(a.getInicio());
        String hora = a.getInicio().format(Textos.HORA);
        String servico = a.getServico().getNome();
        String barbeiro = a.getBarbeiro().getApelido() != null ? a.getBarbeiro().getApelido() : primeiroNome(a.getBarbeiro().getNome());
        String unidade = a.getUnidade().getNome().replace("Rede Barbearias — ", "Rede Barbearias ");
        String endereco = a.getUnidade().getEndereco() + (a.getUnidade().getBairro() != null ? " — " + a.getUnidade().getBairro() : "");
        String linkHorario = siteUrl + "/#/meu-horario/" + a.getCodigo();
        String linkAgendar = siteUrl + "/#/agendar";
        String valor = moeda(a.valorAPagar());

        return switch (tipo) {
            case CONFIRMACAO -> montar(a, tipo, "✅ Horário confirmado: " + dia + " às " + hora,
                    "Horário confirmado, " + nome + "!",
                    "Te esperamos na cadeira. Guarde o código abaixo pra consultar ou cancelar.",
                    detalhes(dia, hora, servico, barbeiro, unidade, endereco, valor, a.getCodigo()),
                    List.<String[]>of(botao("Ver ou cancelar meu horário", linkHorario), botao("📅 Salvar na agenda", linkGoogle(a, unidade, endereco))),
                    "agendamento_confirmado", List.of(nome, dia, hora, servico, barbeiro, unidade + ", " + endereco, a.getCodigo(), linkHorario));
            case LEMBRETE -> montar(a, tipo, "⏰ Hoje às " + hora + ": seu " + servico,
                    "Não esqueça, " + nome + ": é hoje! 💈",
                    "Seu horário é hoje às " + hora + ". Se não puder vir, cancele pelo link pra liberar a vaga pra outra pessoa.",
                    detalhes(dia, hora, servico, barbeiro, unidade, endereco, valor, a.getCodigo()),
                    List.<String[]>of(botao("Ver ou cancelar", linkHorario), botao("Como chegar", "https://www.google.com/maps/search/?api=1&query=" + enc(endereco + ", " + a.getUnidade().getCidade()))),
                    "lembrete_agendamento", List.of(nome, hora, servico, barbeiro, unidade + ", " + endereco, linkHorario));
            case REAGENDAMENTO -> montar(a, tipo, "🔁 Seu horário mudou: " + dia + " às " + hora,
                    "Seu horário foi alterado, " + nome,
                    "Confira os novos dados abaixo. O código continua o mesmo.",
                    detalhes(dia, hora, servico, barbeiro, unidade, endereco, valor, a.getCodigo()),
                    List.<String[]>of(botao("Ver meu horário", linkHorario)),
                    "agendamento_alterado", List.of(nome, dia, hora, servico, barbeiro, unidade + ", " + endereco, linkHorario));
            case CANCELAMENTO -> montar(a, tipo, "Horário cancelado — " + dia + " às " + hora,
                    "Horário cancelado",
                    nome + ", o horário de " + servico + " em " + dia + " às " + hora + " na " + unidade + " foi cancelado. Quando quiser, é só agendar de novo.",
                    Map.of(),
                    List.<String[]>of(botao("Agendar novo horário", linkAgendar)),
                    "agendamento_cancelado", List.of(nome, dia, hora, unidade, linkAgendar));
            case AVALIACAO -> montar(a, tipo, "Como foi seu " + servico + "? ⭐",
                    "Obrigado pela visita, " + nome + "!",
                    "Sua opinião ajuda o " + barbeiro + " e toda a equipe. Leva 10 segundos.",
                    Map.of(),
                    List.<String[]>of(botao("⭐ Avaliar atendimento", linkHorario)),
                    "avaliacao_atendimento", List.of(nome, servico, barbeiro, linkHorario));
        };
    }

    private Mensagem montar(Agendamento a, TipoNotificacao tipo, String assunto, String titulo, String intro,
                            Map<String, String> detalhes, List<String[]> botoes, String modelo, List<String> params) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><body style=\"margin:0;background:#f2ead9;font-family:Arial,Helvetica,sans-serif;color:#2a2118\">")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f2ead9;padding:24px 12px\"><tr><td align=\"center\">")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:520px;background:#ffffff;border-radius:16px;overflow:hidden\">")
            .append("<tr><td style=\"background:#0d0b0a;padding:18px 24px;color:#f0d78c;font-size:20px;font-weight:bold;letter-spacing:1px\">💈 REDE BARBEARIAS</td></tr>")
            .append("<tr><td style=\"padding:24px\">")
            .append("<h1 style=\"margin:0 0 10px;font-size:22px;color:#1c1613\">").append(esc(titulo)).append("</h1>")
            .append("<p style=\"margin:0 0 18px;font-size:15px;line-height:1.5;color:#4a4036\">").append(esc(intro)).append("</p>");
        if (!detalhes.isEmpty()) {
            html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f7f2e8;border-radius:12px;padding:6px 14px;margin-bottom:18px\">");
            detalhes.forEach((k, v) -> html.append("<tr><td style=\"padding:6px 0;color:#6b5f52;font-size:13px;width:90px\">").append(esc(k))
                    .append("</td><td style=\"padding:6px 0;font-size:15px;font-weight:bold\">").append(esc(v)).append("</td></tr>"));
            html.append("</table>");
        }
        for (String[] b : botoes) {
            html.append("<a href=\"").append(esc(b[1])).append("\" style=\"display:inline-block;margin:0 8px 10px 0;background:#c0392b;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:999px;font-weight:bold;font-size:14px\">")
                .append(esc(b[0])).append("</a>");
        }
        html.append("<p style=\"margin:18px 0 0;font-size:12px;color:#8a7f72\">").append(esc(a.getUnidade().getNome()))
            .append(a.getUnidade().getTelefone() != null ? " · " + esc(a.getUnidade().getTelefone()) : "")
            .append("<br>Você recebeu este e-mail porque agendou um horário com a gente.</p>")
            .append("</td></tr></table></td></tr></table></body></html>");

        StringBuilder texto = new StringBuilder(titulo).append("\n\n").append(intro).append("\n\n");
        detalhes.forEach((k, v) -> texto.append(k).append(": ").append(v).append("\n"));
        for (String[] b : botoes) texto.append("\n").append(b[0]).append(": ").append(b[1]);

        return new Mensagem(tipo, a.getCliente().getNome(), a.getCliente().getEmail(), a.getCliente().getTelefone(),
                assunto, html.toString(), texto.toString(), modelo, params);
    }

    private static Map<String, String> detalhes(String dia, String hora, String servico, String barbeiro,
                                                String unidade, String endereco, String valor, String codigo) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Quando", capitalizar(dia) + " às " + hora);
        m.put("Serviço", servico + " · " + valor);
        m.put("Barbeiro", barbeiro);
        m.put("Onde", unidade + " — " + endereco);
        m.put("Código", codigo);
        return m;
    }

    private String linkGoogle(Agendamento a, String unidade, String endereco) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        return "https://calendar.google.com/calendar/render?action=TEMPLATE&ctz=America/Sao_Paulo"
                + "&text=" + enc(a.getServico().getNome() + " — Rede Barbearias")
                + "&dates=" + a.getInicio().format(f) + "/" + a.getFim().format(f)
                + "&location=" + enc(unidade + ", " + endereco)
                + "&details=" + enc("Código " + a.getCodigo() + " · " + siteUrl + "/#/meu-horario/" + a.getCodigo());
    }

    private static String[] botao(String rotulo, String url) {
        return new String[]{rotulo, url};
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s, "UTF-8");
    }

    private static String moeda(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(BR).format(v).replace(' ', ' ');
    }

    private static String primeiroNome(String n) {
        return n == null || n.isBlank() ? "" : n.trim().split("\\s+")[0];
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
