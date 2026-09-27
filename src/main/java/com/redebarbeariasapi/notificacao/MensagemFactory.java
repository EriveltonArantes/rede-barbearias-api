package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.model.Agendamento;
import com.redebarbeariasapi.model.Barbeiro;
import com.redebarbeariasapi.model.Cliente;
import com.redebarbeariasapi.model.Cupom;
import com.redebarbeariasapi.model.ListaEspera;
import com.redebarbeariasapi.model.Unidade;
import com.redebarbeariasapi.model.TipoNotificacao;
import com.redebarbeariasapi.service.Textos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        MODELOS.put("lembrete_1h", "{{1}}, seu horário é daqui a pouco! ⏰\n\nÀs {{2}}: {{3}} com {{4}}\n📍 {{5}}\n\nVai atrasar ou não vai conseguir vir? Avise por aqui ou pelo link: {{6}}");
        MODELOS.put("agendamento_alterado", "{{1}}, seu horário foi alterado 🔁\n\n📅 {{2}} às {{3}}\n✂️ {{4}} com {{5}}\n📍 {{6}}\n\nDetalhes: {{7}}");
        MODELOS.put("agendamento_cancelado", "{{1}}, seu horário de {{2}} às {{3}} na {{4}} foi cancelado. Quando quiser, agende de novo: {{5}}");
        MODELOS.put("avaliacao_atendimento", "Obrigado pela visita, {{1}}! 💈 Como foi seu {{2}} com {{3}}? Avalie em 10 segundos: {{4}}\n\n{{5}}");
        MODELOS.put("sinal_pendente", "{{1}}, seu horário de {{2}} às {{3}} está reservado! 💈\n\nPra garantir, falta o sinal de {{4}} via Pix — ele é descontado no dia. Pague até {{5}}:\n{{6}}");
        MODELOS.put("vaga_liberada", "Boa notícia, {{1}}! 💈 Abriu um horário {{2}} às {{3}} na {{4}} — você estava na lista de espera.\n\nQuem agendar primeiro leva: {{5}}");
        MODELOS.put("aniversario_cliente", "Feliz aniversário, {{1}}! 🎉💈\n\nPra comemorar, seu próximo atendimento tem {{2}} de desconto com o cupom {{3}} (válido até {{4}}).\n\nAgende: {{5}}");
        MODELOS.put("retorno_cliente", "{{1}}, já faz {{2}} dias do seu último corte ✂️ Bora dar um tapa no visual?\n\n{{3}}\n\nAgende em 1 minuto: {{4}}");
    }

    /** Categoria de cada modelo na Meta (Marketing tem regra propria de consentimento e custa mais). */
    public static final Map<String, String> CATEGORIAS = new LinkedHashMap<>();
    static {
        MODELOS.keySet().forEach(k -> CATEGORIAS.put(k, "Utilidade"));
        CATEGORIAS.put("aniversario_cliente", "Marketing");
        CATEGORIAS.put("retorno_cliente", "Marketing");
    }

    /** Modelos com botoes de resposta rapida: cadastrar na Meta com esses textos, nessa ordem. */
    public static final Map<String, List<String>> BOTOES = new LinkedHashMap<>();
    static {
        BOTOES.put("lembrete_agendamento", List.of("✅ Confirmo", "❌ Preciso cancelar"));
        BOTOES.put("lembrete_1h", List.of("✅ Estou indo", "❌ Não vou conseguir"));
    }
    /** Rodape sugerido pros modelos (a Meta pede um jeito facil de parar de receber). */
    public static final String RODAPE = "Pra não receber mais mensagens, responda PARAR";
    public static final String CONFIRMAR = "CONFIRMAR:";
    public static final String CANCELAR = "CANCELAR:";

    private final String siteUrl;
    private final int pontosResgate;

    public MensagemFactory(@Value("${app.site-url}") String siteUrl,
                           @Value("${app.fidelidade.pontos-resgate:10}") int pontosResgate) {
        this.siteUrl = siteUrl.replaceAll("/+$", "");
        this.pontosResgate = pontosResgate;
    }

    public String siteUrl() {
        return siteUrl;
    }

    /** Nome da barbearia (tela "Marca e aparência"); sem o servico (testes de unidade) fica o padrao. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.redebarbeariasapi.service.MarcaService marcaService;

    public String marca() {
        return marcaService == null ? com.redebarbeariasapi.model.Marca.NOME_PADRAO : marcaService.nome();
    }

    /** "Rede Barbearias — Savassi" vira "Rede Barbearias Savassi" nas mensagens. */
    static String nomeUnidade(String nome) {
        return nome == null ? "" : nome.replace(" — ", " ");
    }

    /**
     * Cartela de fidelidade em texto: "✅✅✅⭕⭕⭕⭕⭕⭕⭕ 3 de 10 — faltam 7 pra ganhar um atendimento".
     * Funciona igual no e-mail e no WhatsApp.
     */
    public String cartelaFidelidade(int pontos) {
        if (pontosResgate <= 0) return "";
        if (pontos >= pontosResgate) {
            return "✅".repeat(pontosResgate) + "\n🎁 Cartela completa! Seu próximo atendimento pode sair de graça, é só avisar na recepção.";
        }
        int faltam = pontosResgate - pontos;
        return "✅".repeat(pontos) + "⭕".repeat(faltam) + "\n" + pontos + " de " + pontosResgate
                + " no cartão fidelidade: falta" + (faltam == 1 ? " 1" : "m " + faltam) + " pra você ganhar um atendimento 😎";
    }

    public Mensagem criar(Agendamento a, TipoNotificacao tipo) {
        String nome = primeiroNome(a.getCliente().getNome());
        String dia = DIA.format(a.getInicio());
        String hora = a.getInicio().format(Textos.HORA);
        String servico = a.getServico().getNome();
        String barbeiro = a.getBarbeiro().getApelido() != null ? a.getBarbeiro().getApelido() : primeiroNome(a.getBarbeiro().getNome());
        String unidade = nomeUnidade(a.getUnidade().getNome());
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
            case LEMBRETE_PROXIMO -> montar(a, tipo, "⏰ Daqui a pouco, às " + hora + ": seu " + servico,
                    "Seu horário é daqui a pouco, " + nome + "!",
                    "Às " + hora + " o " + barbeiro + " te espera. Se for atrasar ou não puder vir, avise pra gente reorganizar a agenda.",
                    detalhes(dia, hora, servico, barbeiro, unidade, endereco, valor, a.getCodigo()),
                    List.<String[]>of(botao("Como chegar", "https://www.google.com/maps/search/?api=1&query=" + enc(endereco + ", " + a.getUnidade().getCidade())), botao("Ver ou cancelar", linkHorario)),
                    "lembrete_1h", List.of(nome, hora, servico, barbeiro, unidade + ", " + endereco, linkHorario));
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
            case AVALIACAO -> {
                String cartela = cartelaFidelidade(a.getCliente().getPontos());
                Map<String, String> fidelidade = cartela.isEmpty() ? Map.of() : Map.of("Fidelidade", cartela);
                yield montar(a, tipo, "Como foi seu " + servico + "? ⭐",
                        "Obrigado pela visita, " + nome + "!",
                        "Sua opinião ajuda o " + barbeiro + " e toda a equipe. Leva 10 segundos.",
                        fidelidade,
                        List.<String[]>of(botao("⭐ Avaliar atendimento", linkHorario)),
                        "avaliacao_atendimento", List.of(nome, servico, barbeiro, linkHorario, cartela.isEmpty() ? "Até a próxima! 💈" : umaLinha(cartela)));
            }
            case SINAL_PENDENTE -> {
                String sinal = moeda(a.getSinalValor());
                String prazo = a.getSinalExpiraEm() == null ? "o horário"
                        : a.getSinalExpiraEm().toLocalDate().equals(a.getCriadoEm().toLocalDate())
                        ? "hoje às " + a.getSinalExpiraEm().format(Textos.HORA)
                        : DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm").format(a.getSinalExpiraEm());
                Map<String, String> d = detalhes(dia, hora, servico, barbeiro, unidade, endereco, valor, a.getCodigo());
                d.put("Sinal", sinal + " via Pix, até " + prazo);
                yield montar(a, tipo, "💈 Falta o sinal pra garantir seu horário de " + dia,
                        "Seu horário está reservado, " + nome + "!",
                        "Pra garantir a cadeira falta o sinal de " + sinal + " via Pix — ele é descontado do valor no dia. "
                                + "Abra o link, copie o código Pix e pague no app do seu banco.",
                        d, List.<String[]>of(botao("💠 Pagar o sinal com Pix", linkHorario)),
                        "sinal_pendente", List.of(nome, dia, hora, sinal, prazo, linkHorario));
            }
            case VAGA_LIBERADA, ANIVERSARIO, RETORNO ->
                    throw new IllegalArgumentException(tipo + " não é mensagem de um horário do próprio cliente");
        };
    }

    /** Aviso pra quem estava na lista de espera: abriu um horario no dia que ele queria. */
    public Mensagem vagaLiberada(ListaEspera e, LocalDateTime inicio, Barbeiro barbeiroLivre) {
        Cliente c = e.getCliente();
        String nome = primeiroNome(c.getNome());
        String dia = DIA.format(inicio);
        String quando = inicio.toLocalDate().equals(LocalDate.now()) ? "hoje" : inicio.toLocalDate().equals(LocalDate.now().plusDays(1)) ? "amanhã" : dia;
        String hora = inicio.format(Textos.HORA);
        String unidade = nomeUnidade(e.getUnidade().getNome());
        String link = siteUrl + "/#/agendar?unidade=" + e.getUnidade().getId() + "&servico=" + e.getServico().getId()
                + "&data=" + inicio.toLocalDate() + (e.getBarbeiro() != null ? "&barbeiro=" + e.getBarbeiro().getId() : "");
        Map<String, String> d = new LinkedHashMap<>();
        d.put("Quando", capitalizar(dia) + " às " + hora);
        d.put("Serviço", e.getServico().getNome());
        if (barbeiroLivre != null) d.put("Barbeiro", barbeiroLivre.getApelido() != null ? barbeiroLivre.getApelido() : primeiroNome(barbeiroLivre.getNome()));
        d.put("Onde", unidade);
        return montarPara(c, e.getUnidade(), null, TipoNotificacao.VAGA_LIBERADA,
                "💈 Abriu um horário " + quando + " às " + hora + "!",
                "Boa notícia, " + nome + ": abriu um horário!",
                "Você estava na lista de espera e alguém acabou de desmarcar. Quem agendar primeiro leva — corre!",
                d, List.<String[]>of(botao("Agendar agora", link)),
                "vaga_liberada", List.of(nome, quando, hora, unidade, link),
                "Você recebeu esta mensagem porque entrou na lista de espera.");
    }

    public Mensagem aniversario(Cliente c, Cupom cp) {
        String nome = primeiroNome(c.getNome());
        String desconto = percentual(cp.getPercentual());
        String validade = cp.getValidoAte().format(Textos.DATA);
        Map<String, String> d = new LinkedHashMap<>();
        d.put("Cupom", cp.getCodigo());
        d.put("Desconto", desconto);
        d.put("Válido até", validade);
        return montarPara(c, c.getUnidadePreferida(), null, TipoNotificacao.ANIVERSARIO,
                "🎉 Feliz aniversário, " + nome + "! Tem presente aqui",
                "Feliz aniversário, " + nome + "! 🎉",
                "Pra comemorar, seu próximo atendimento tem " + desconto + " de desconto. É só usar o cupom abaixo ao agendar.",
                d, List.<String[]>of(botao("Agendar com desconto", siteUrl + "/#/agendar")),
                "aniversario_cliente", List.of(nome, desconto, cp.getCodigo(), validade, siteUrl + "/#/agendar"),
                RODAPE_MARKETING);
    }

    public Mensagem retorno(Cliente c, long dias, Cupom cp) {
        String nome = primeiroNome(c.getNome());
        String oferta = cp == null ? "Tem horário livre essa semana — escolhe o seu 😉"
                : "Use o cupom " + cp.getCodigo() + " e ganhe " + percentual(cp.getPercentual()) + " de desconto (até " + cp.getValidoAte().format(Textos.DATA) + ").";
        Map<String, String> d = new LinkedHashMap<>();
        d.put("Última visita", "há " + dias + " dias");
        if (cp != null) d.put("Cupom", cp.getCodigo() + " · " + percentual(cp.getPercentual()));
        return montarPara(c, c.getUnidadePreferida(), null, TipoNotificacao.RETORNO,
                "✂️ " + nome + ", bora dar um tapa no visual?",
                "Já faz " + dias + " dias, " + nome + "!",
                "Seu último corte já tem " + dias + " dias. " + oferta,
                d, List.<String[]>of(botao("Agendar meu horário", siteUrl + "/#/agendar")),
                "retorno_cliente", List.of(nome, String.valueOf(dias), oferta, siteUrl + "/#/agendar"),
                RODAPE_MARKETING);
    }

    private static final String RODAPE_MARKETING = "Você recebeu porque aceitou receber novidades da barbearia. Pra não receber mais, responda PARAR no WhatsApp.";

    private Mensagem montar(Agendamento a, TipoNotificacao tipo, String assunto, String titulo, String intro,
                            Map<String, String> detalhes, List<String[]> botoes, String modelo, List<String> params) {
        return montarPara(a.getCliente(), a.getUnidade(), a.getCodigo(), tipo, assunto, titulo, intro, detalhes, botoes, modelo, params,
                "Você recebeu este e-mail porque agendou um horário com a gente.");
    }

    /** codigoBotoes: codigo do horario que os botoes Confirmo/Cancelar do modelo vao carregar (null = sem botoes). */
    private Mensagem montarPara(Cliente c, Unidade un, String codigoBotoes, TipoNotificacao tipo, String assunto, String titulo,
                                String intro, Map<String, String> detalhes, List<String[]> botoes, String modelo, List<String> params,
                                String porQueRecebeu) {
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
                    .append("</td><td style=\"padding:6px 0;font-size:15px;font-weight:bold\">").append(esc(v).replace("\n", "<br>")).append("</td></tr>"));
            html.append("</table>");
        }
        for (String[] b : botoes) {
            html.append("<a href=\"").append(esc(b[1])).append("\" style=\"display:inline-block;margin:0 8px 10px 0;background:#c0392b;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:999px;font-weight:bold;font-size:14px\">")
                .append(esc(b[0])).append("</a>");
        }
        html.append("<p style=\"margin:18px 0 0;font-size:12px;color:#8a7f72\">").append(esc(un != null ? nomeUnidade(un.getNome()) : marca()))
            .append(un != null && un.getTelefone() != null ? " · " + esc(un.getTelefone()) : "")
            .append("<br>").append(esc(porQueRecebeu))
            .append("<br><a href=\"").append(esc(siteUrl + "/#/privacidade")).append("\" style=\"color:#8a7f72\">Política de privacidade</a></p>")
            .append("</td></tr></table></td></tr></table></body></html>");

        List<String> payloads = BOTOES.containsKey(modelo) && codigoBotoes != null ? List.of(CONFIRMAR + codigoBotoes, CANCELAR + codigoBotoes) : List.of();
        StringBuilder texto = new StringBuilder(titulo).append("\n\n").append(intro).append("\n\n");
        detalhes.forEach((k, v) -> texto.append(k).append(": ").append(v).append("\n"));
        for (String[] b : botoes) texto.append("\n").append(b[0]).append(": ").append(b[1]);

        return new Mensagem(tipo, c.getNome(), c.getEmail(), c.getTelefone(),
                assunto, html.toString(), texto.toString(), modelo, params, payloads, c.isWhatsappBloqueado());
    }

    private static String percentual(BigDecimal p) {
        return p.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
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
                + "&text=" + enc(a.getServico().getNome() + " — " + marca())
                + "&dates=" + a.getInicio().format(f) + "/" + a.getFim().format(f)
                + "&location=" + enc(unidade + ", " + endereco)
                + "&details=" + enc("Código " + a.getCodigo() + " · " + siteUrl + "/#/meu-horario/" + a.getCodigo());
    }

    /** A Meta recusa parametro de modelo com quebra de linha, tab ou mais de 4 espacos seguidos. */
    static String umaLinha(String s) {
        return s.replaceAll("\\s*\\n\\s*", " — ").replaceAll("[\\t ]{2,}", " ");
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
