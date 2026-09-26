package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.AgendamentoRepository;
import com.redebarbeariasapi.repository.ClienteRepository;
import com.redebarbeariasapi.repository.ConfiguracaoAtendimentoRepository;
import com.redebarbeariasapi.repository.ConversaWhatsAppRepository;
import com.redebarbeariasapi.repository.UnidadeRepository;
import com.redebarbeariasapi.security.Sessao;
import com.redebarbeariasapi.service.AgendamentoService;
import com.redebarbeariasapi.service.AuditoriaService;
import com.redebarbeariasapi.service.Textos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Atendimento automatico do WhatsApp. Pra cada mensagem de cliente decide UMA coisa:
 * <ol>
 *   <li>PARAR / VOLTAR — liga e desliga as mensagens automaticas pra esse numero;</li>
 *   <li>confirmar ou cancelar o horario — pelo botao do lembrete ou respondendo 1 / 2;</li>
 *   <li>fora do horario de todas as unidades — aviso de "fechados" com o link pra agendar;</li>
 *   <li>boas-vindas com o link do agendamento (e o horario marcado, se ja tem).</li>
 * </ol>
 * Boas-vindas e aviso de fechado tem intervalo: depois disso fica quieto e a conversa segue
 * com a equipe no proprio app do WhatsApp Business.
 */
@Service
public class AtendimentoWhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(AtendimentoWhatsAppService.class);
    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", BR);
    private static final Set<StatusAgendamento> ABERTOS = Set.of(StatusAgendamento.AGENDADO, StatusAgendamento.CONFIRMADO);

    static final Set<String> PARAR = Set.of("parar", "pare", "sair", "stop", "descadastrar", "nao quero mais mensagens", "nao quero receber");
    static final Set<String> VOLTAR = Set.of("voltar", "quero receber", "voltar a receber");
    static final Set<String> CONFIRMA = Set.of("1", "confirmo", "confirmado", "confirmar", "estou indo", "to indo", "vou sim");
    static final Set<String> CANCELA = Set.of("2", "cancelar", "cancela", "preciso cancelar", "desmarcar", "nao vou conseguir", "nao vou poder");

    /** Mensagem de "confirmar/cancelar" so vale pra horario que recebeu lembrete e comeca nas proximas horas. */
    private static final int JANELA_RESPOSTA_HORAS = 30;

    private final ConversaWhatsAppRepository conversas;
    private final ConfiguracaoAtendimentoRepository configuracoes;
    private final ClienteRepository clientes;
    private final AgendamentoRepository agendamentos;
    private final UnidadeRepository unidades;
    private final AgendamentoService agenda;
    private final WhatsAppCanal whatsapp;
    private final MensagemFactory mensagens;
    private final AuditoriaService auditoria;

    public AtendimentoWhatsAppService(ConversaWhatsAppRepository conversas, ConfiguracaoAtendimentoRepository configuracoes,
                                      ClienteRepository clientes, AgendamentoRepository agendamentos, UnidadeRepository unidades,
                                      AgendamentoService agenda, WhatsAppCanal whatsapp, MensagemFactory mensagens,
                                      AuditoriaService auditoria) {
        this.conversas = conversas;
        this.configuracoes = configuracoes;
        this.clientes = clientes;
        this.agendamentos = agendamentos;
        this.unidades = unidades;
        this.agenda = agenda;
        this.whatsapp = whatsapp;
        this.mensagens = mensagens;
        this.auditoria = auditoria;
    }

    /** INFORMAR = so responde (ex.: "esse horario ja passou"), sem mudar nada. */
    public enum Acao { OPT_OUT, OPT_IN, CONFIRMAR, CANCELAR, INFORMAR, FORA_HORARIO, SAUDACAO, NENHUMA }

    /** O que aconteceu com uma mensagem recebida (usado nos testes, no simulador e no log). */
    public record Resultado(boolean respondeu, String resposta, String motivo, Acao acao) {
        static Resultado silencio(String motivo) { return new Resultado(false, null, motivo, Acao.NENHUMA); }
    }

    /** Decisao tomada antes de executar: o simulador usa so isso, sem mexer em nada. */
    private record Plano(Acao acao, String resposta, Agendamento agendamento, String motivo) {}

    // ------------------------------------------------------------------ configuracao

    @Transactional
    public ConfiguracaoAtendimento configuracao() {
        return configuracoes.findById(1L).orElseGet(() -> configuracoes.save(new ConfiguracaoAtendimento()));
    }

    @Transactional
    public ConfiguracaoAtendimento salvarConfiguracao(Map<String, Object> corpo) {
        ConfiguracaoAtendimento c = configuracao();
        if (corpo.get("saudacao") instanceof String s) c.setSaudacao(validarTexto(s, "boas-vindas"));
        if (corpo.get("mensagemForaHorario") instanceof String s) c.setMensagemForaHorario(validarTexto(s, "fora do horário"));
        if (corpo.get("intervaloHoras") instanceof Number n) {
            if (n.intValue() < 1 || n.intValue() > 168) throw new ValidacaoException("O intervalo deve ficar entre 1 e 168 horas.");
            c.setIntervaloHoras(n.intValue());
        }
        if (corpo.get("respostaAutomatica") instanceof Boolean b) c.setRespostaAutomatica(b);
        if (corpo.get("mostrarProximoHorario") instanceof Boolean b) c.setMostrarProximoHorario(b);
        if (corpo.get("foraHorarioAtivo") instanceof Boolean b) c.setForaHorarioAtivo(b);
        c.setAtualizadoEm(LocalDateTime.now());
        c.setAtualizadoPor(Sessao.username());
        auditoria.registrar("CONFIGURAR", "AtendimentoWhatsApp", 1L,
                (c.isRespostaAutomatica() ? "ligada" : "desligada") + ", intervalo " + c.getIntervaloHoras() + "h"
                        + (c.isForaHorarioAtivo() ? ", aviso fora do horário ligado" : ""));
        return c;
    }

    private static String validarTexto(String bruto, String qual) {
        String s = bruto.strip();
        if (s.isEmpty()) throw new ValidacaoException("Escreva a mensagem de " + qual + ".");
        if (s.length() > 1000) throw new ValidacaoException("A mensagem de " + qual + " pode ter no máximo 1000 caracteres.");
        if (!s.contains("{link_agendar}")) throw new ValidacaoException("Inclua {link_agendar} na mensagem de " + qual + ": é por ele que o cliente agenda.");
        return s;
    }

    // ------------------------------------------------------------------ mensagem recebida

    /** Chamado pelo webhook da Meta pra cada mensagem de cliente. payload = botao tocado (ou null). */
    @Transactional
    public Resultado receber(String mensagemId, String telefoneWa, String nomePerfil, String texto, String payload, LocalDateTime agora) {
        String tel = telefoneWa == null ? "" : telefoneWa.replaceAll("\\D", "");
        if (tel.length() < 10) return Resultado.silencio("telefone inválido");

        ConversaWhatsApp conv = conversas.findByTelefone(tel).orElseGet(() -> {
            ConversaWhatsApp n = new ConversaWhatsApp();
            n.setTelefone(tel);
            return n;
        });
        if (mensagemId != null && mensagemId.equals(conv.getUltimaMensagemId())) {
            return Resultado.silencio("mensagem repetida (reenvio da Meta)");
        }
        Cliente cliente = clientePorTelefone(tel).orElse(null);
        conv.setCliente(cliente);
        // bloqueio pode ter sido feito pela equipe no cadastro, ou por outro numero do mesmo cliente
        if (cliente != null && cliente.isWhatsappBloqueado()) conv.setOptOut(true);
        if (nomePerfil != null && !nomePerfil.isBlank()) conv.setNome(nomePerfil.strip());
        else if (cliente != null && conv.getNome() == null) conv.setNome(cliente.getNome());
        conv.setUltimaMensagemId(mensagemId);
        conv.setUltimaMensagem(corta(texto == null || texto.isBlank() ? "(mídia / sem texto)" : texto, 500));
        conv.setUltimaRecebidaEm(agora);
        conv.setTotalRecebidas(conv.getTotalRecebidas() + 1);
        conversas.save(conv);

        ConfiguracaoAtendimento cfg = configuracao();
        Plano plano = planejar(cfg, conv, cliente, texto, payload, agora, null, false);
        if (plano.acao() == Acao.NENHUMA) return Resultado.silencio(plano.motivo());

        // o plano ja checou as regras (horario passado, ja finalizado...): aqui so executa
        switch (plano.acao()) {
            case OPT_OUT -> {
                conv.setOptOut(true);
                if (cliente != null) cliente.setWhatsappBloqueado(true);
                conv.setUltimaAcao("🚫 Pediu pra não receber mais mensagens");
                auditoria.registrar("OPT_OUT", "WhatsApp", cliente == null ? null : cliente.getId(), tel);
            }
            case OPT_IN -> {
                conv.setOptOut(false);
                if (cliente != null) cliente.setWhatsappBloqueado(false);
                conv.setUltimaAcao("🔔 Voltou a receber mensagens");
            }
            case CONFIRMAR -> {
                agenda.confirmarPeloCliente(plano.agendamento(), "WhatsApp");
                conv.setUltimaAcao("✅ Confirmou presença · #" + plano.agendamento().getCodigo());
            }
            case CANCELAR -> {
                agenda.cancelarPeloWhatsApp(plano.agendamento());
                conv.setUltimaAcao("❌ Cancelou pelo WhatsApp · #" + plano.agendamento().getCodigo());
            }
            case FORA_HORARIO -> conv.setUltimoForaHorarioEm(agora);
            default -> { }
        }
        return enviar(conv, plano.resposta(), plano.acao(), agora);
    }

    private Resultado enviar(ConversaWhatsApp conv, String resposta, Acao acao, LocalDateTime agora) {
        if (!whatsapp.configurado()) {
            conv.setErroResposta("WhatsApp oficial não configurado no servidor");
            return new Resultado(false, resposta, "WhatsApp oficial não configurado", acao);
        }
        try {
            whatsapp.enviarTexto(conv.getTelefone(), resposta);
            conv.setUltimaRespostaEm(agora);
            conv.setUltimaResposta(corta(resposta, 1500));
            conv.setErroResposta(null);
            return new Resultado(true, resposta, "respondido (" + acao + ")", acao);
        } catch (Exception e) {
            conv.setErroResposta(corta(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 500));
            log.warn("Resposta automatica pra {} falhou: {}", conv.getTelefone(), conv.getErroResposta());
            return new Resultado(false, resposta, "falha ao enviar: " + conv.getErroResposta(), acao);
        }
    }

    /**
     * Pra tela "simular": mostra o que o cliente receberia, sem enviar nem alterar nada.
     * foraDoHorario: null = relogio de verdade; true/false = forca pra testar as duas mensagens.
     */
    @Transactional(readOnly = true)
    public Resultado simular(String nome, String telefone, String texto, Boolean foraDoHorario, LocalDateTime agora) {
        ConfiguracaoAtendimento cfg = configuracoes.findById(1L).orElseGet(ConfiguracaoAtendimento::new);
        Cliente cliente = telefone == null || telefone.isBlank() ? null : clientePorTelefone(telefone.replaceAll("\\D", "")).orElse(null);
        ConversaWhatsApp conv = new ConversaWhatsApp();
        conv.setNome(nome != null && !nome.isBlank() ? nome : cliente != null ? cliente.getNome() : null);
        conv.setOptOut(cliente != null && cliente.isWhatsappBloqueado());
        Plano p = planejar(cfg, conv, cliente, texto, null, agora, foraDoHorario, true);
        return new Resultado(p.acao() != Acao.NENHUMA, p.resposta(), p.motivo(), p.acao());
    }

    private Plano planejar(ConfiguracaoAtendimento cfg, ConversaWhatsApp conv, Cliente cliente, String texto, String payload,
                           LocalDateTime agora, Boolean foraForcado, boolean simulacao) {
        String t = normalizar(texto);
        String nome = primeiroNome(conv.getNome() != null ? conv.getNome() : cliente != null ? cliente.getNome() : null);

        // 1) PARAR / VOLTAR valem sempre, mesmo com a resposta automatica desligada (pedido do cliente e lei)
        if (PARAR.contains(t)) {
            return new Plano(Acao.OPT_OUT, "Pronto" + virgulaNome(nome) + " ✅ Você não vai mais receber mensagens automáticas por aqui."
                    + "\nSe mudar de ideia, é só mandar VOLTAR. E pode continuar falando com a gente normalmente 😉", null, "pediu pra parar");
        }
        if (conv.isOptOut()) {
            if (VOLTAR.contains(t)) {
                return new Plano(Acao.OPT_IN, "Que bom te ver de volta" + virgulaNome(nome) + "! 💈 Você volta a receber os lembretes dos seus horários por aqui.", null, "voltou a receber");
            }
            return new Plano(Acao.NENHUMA, null, null, "cliente pediu pra não receber mensagens automáticas");
        }

        // 2) confirmar / cancelar horario (botao do lembrete tem prioridade sobre o texto)
        Plano horario = planoDoHorario(cliente, t, payload, nome, agora);
        if (horario != null) return horario;

        if (!cfg.isRespostaAutomatica()) return new Plano(Acao.NENHUMA, null, null, "resposta automática desligada");

        // 3) fora do horario: aviso de fechados (intervalo proprio)
        boolean fora = foraForcado != null ? foraForcado : !algumaUnidadeAberta(agora);
        if (fora && cfg.isForaHorarioAtivo()) {
            if (!simulacao && conv.getUltimoForaHorarioEm() != null && conv.getUltimoForaHorarioEm().isAfter(agora.minusHours(cfg.getIntervaloHoras()))) {
                return new Plano(Acao.NENHUMA, null, null, "já avisado que está fechado");
            }
            String abre = proximaAbertura(unidades.findByAtivaTrueOrderByNome(), agora);
            String txt = aplicar(cfg.getMensagemForaHorario(), nome, abre == null ? "em breve" : abre);
            return new Plano(Acao.FORA_HORARIO, txt + blocoCliente(cfg, cliente, agora), null, "fora do horário");
        }

        // 4) boas-vindas
        if (!simulacao && conv.getUltimaRespostaEm() != null && conv.getUltimaRespostaEm().isAfter(agora.minusHours(cfg.getIntervaloHoras()))) {
            return new Plano(Acao.NENHUMA, null, null, "já respondido nas últimas " + cfg.getIntervaloHoras() + "h — conversa segue com a equipe");
        }
        return new Plano(Acao.SAUDACAO, aplicar(cfg.getSaudacao(), nome, null) + blocoCliente(cfg, cliente, agora), null, "boas-vindas");
    }

    /** Botao "Confirmo/Preciso cancelar" do lembrete, ou resposta 1/2 a um lembrete recente. */
    private Plano planoDoHorario(Cliente cliente, String t, String payload, String nome, LocalDateTime agora) {
        Agendamento alvo = null;
        Boolean confirmar = null;
        if (payload != null && (payload.startsWith(MensagemFactory.CONFIRMAR) || payload.startsWith(MensagemFactory.CANCELAR))) {
            confirmar = payload.startsWith(MensagemFactory.CONFIRMAR);
            String codigo = payload.substring(payload.indexOf(':') + 1);
            alvo = agendamentos.findByCodigo(codigo).orElse(null);
            // o botao so vale pro dono do horario (o numero que recebeu o lembrete)
            if (alvo != null && (cliente == null || !alvo.getCliente().getId().equals(cliente.getId()))) alvo = null;
            if (alvo == null) return null;
        } else if (cliente != null && (CONFIRMA.contains(t) || CANCELA.contains(t))) {
            confirmar = CONFIRMA.contains(t);
            alvo = agendamentos.filtrar(agora, agora.plusHours(JANELA_RESPOSTA_HORAS), null, null, cliente.getId(), null).stream()
                    .filter(a -> ABERTOS.contains(a.getStatus()) && a.isLembreteEnviado())
                    .findFirst().orElse(null);
            if (alvo == null) return null; // "1" solto sem lembrete: segue pra boas-vindas normal
        } else {
            return null;
        }

        String quando = quando(alvo, agora) + " às " + alvo.getInicio().format(Textos.HORA);
        String barbeiro = alvo.getBarbeiro().getApelido() != null ? alvo.getBarbeiro().getApelido() : primeiroNome(alvo.getBarbeiro().getNome());
        if (alvo.getStatus() == StatusAgendamento.CANCELADO) {
            return new Plano(Acao.INFORMAR, "Esse horário de " + quando + " já está cancelado" + virgulaNome(nome)
                    + ". Quer marcar outro? " + mensagens.siteUrl() + "/#/agendar", alvo, "já cancelado");
        }
        if (alvo.getStatus().finalizado() || !alvo.getInicio().isAfter(agora)) {
            return new Plano(Acao.INFORMAR, "Esse horário (" + quando + ") já passou" + virgulaNome(nome)
                    + ". Se precisar de algo, é só escrever que um atendente te responde 😉", alvo, "horário já passou");
        }
        if (confirmar && alvo.getStatus() == StatusAgendamento.CONFIRMADO) {
            return new Plano(Acao.INFORMAR, "Sua presença já está confirmada" + virgulaNome(nome) + " ✅ Te esperamos " + quando
                    + " com " + barbeiro + " 💈", alvo, "já estava confirmado");
        }
        if (confirmar) {
            return new Plano(Acao.CONFIRMAR, "✅ Presença confirmada" + virgulaNome(nome) + "! Te esperamos " + quando
                    + " com " + barbeiro + " 💈\n📍 " + alvo.getUnidade().getEndereco()
                    + (alvo.getUnidade().getBairro() != null ? " — " + alvo.getUnidade().getBairro() : ""), alvo, "confirmou");
        }
        return new Plano(Acao.CANCELAR, "Tudo certo" + virgulaNome(nome) + ", cancelamos seu horário de " + quando
                + ". Obrigado por avisar! 🙏\n\nQuer marcar outro dia? É rapidinho: " + mensagens.siteUrl() + "/#/agendar", alvo, "cancelou");
    }

    private String blocoCliente(ConfiguracaoAtendimento cfg, Cliente cliente, LocalDateTime agora) {
        if (!cfg.isMostrarProximoHorario() || cliente == null) return "";
        StringBuilder sb = new StringBuilder();
        agendamentos.filtrar(agora, agora.plusDays(90), null, null, cliente.getId(), null).stream()
                .filter(a -> ABERTOS.contains(a.getStatus())).findFirst().ifPresent(a -> {
                    String barbeiro = a.getBarbeiro().getApelido() != null ? a.getBarbeiro().getApelido() : primeiroNome(a.getBarbeiro().getNome());
                    sb.append("\n\n📅 Seu próximo horário: ").append(quando(a, agora)).append(" às ").append(a.getInicio().format(Textos.HORA))
                            .append(" — ").append(a.getServico().getNome()).append(" com ").append(barbeiro)
                            .append("\nVer, remarcar ou cancelar: ").append(mensagens.siteUrl()).append("/#/meu-horario/").append(a.getCodigo());
                });
        String cartela = mensagens.cartelaFidelidade(cliente.getPontos());
        if (!cartela.isEmpty() && cliente.getPontos() > 0) sb.append("\n\n🎯 ").append(cartela);
        return sb.toString();
    }

    private String aplicar(String modelo, String nome, String abre) {
        String site = mensagens.siteUrl();
        String texto = modelo
                .replace("{link_agendar}", site + "/#/agendar")
                .replace("{link_site}", site)
                .replace("{abre}", abre == null ? "" : abre)
                .replace("{nome}", nome);
        // "Olá, !" fica feio quando o perfil nao tem nome
        return texto.replaceAll("(?i)(ol[áa]|oi|bom dia|boa tarde|boa noite), !", "$1!").replace(" , ", " ");
    }

    // ------------------------------------------------------------------ horario de funcionamento

    boolean algumaUnidadeAberta(LocalDateTime agora) {
        List<Unidade> ativas = unidades.findByAtivaTrueOrderByNome();
        if (ativas.isEmpty()) return true; // sem cadastro nao da pra saber: trata como aberto
        return ativas.stream().anyMatch(u -> u.dias().contains(agora.getDayOfWeek())
                && !agora.toLocalTime().isBefore(u.getHoraAbertura()) && agora.toLocalTime().isBefore(u.getHoraFechamento()));
    }

    /** "hoje às 9:00", "amanhã às 9:00", "segunda às 9:00" — a primeira abertura entre as unidades ativas. */
    static String proximaAbertura(List<Unidade> ativas, LocalDateTime agora) {
        LocalDateTime melhor = null;
        for (Unidade u : ativas) {
            for (int d = 0; d <= 7; d++) {
                LocalDate dia = agora.toLocalDate().plusDays(d);
                if (!u.dias().contains(dia.getDayOfWeek())) continue;
                LocalDateTime abre = dia.atTime(u.getHoraAbertura());
                if (abre.isAfter(agora)) {
                    if (melhor == null || abre.isBefore(melhor)) melhor = abre;
                    break;
                }
            }
        }
        if (melhor == null) return null;
        String hora = melhor.getMinute() == 0 ? melhor.getHour() + "h" : melhor.format(Textos.HORA);
        LocalDate hoje = agora.toLocalDate();
        if (melhor.toLocalDate().equals(hoje)) return "hoje às " + hora;
        if (melhor.toLocalDate().equals(hoje.plusDays(1))) return "amanhã às " + hora;
        DayOfWeek dow = melhor.getDayOfWeek();
        return (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY ? "no " : "na ")
                + dow.getDisplayName(TextStyle.FULL, BR) + " às " + hora;
    }

    // ------------------------------------------------------------------ utilitarios

    /**
     * A Meta manda o numero como 55 + DDD + numero, e em muitos celulares brasileiros SEM o 9
     * da frente (5531 9999-8888 chega como 553199998888). Procura o cliente nas duas formas.
     */
    Optional<Cliente> clientePorTelefone(String digitos) {
        String d = digitos.startsWith("55") && digitos.length() >= 12 ? digitos.substring(2) : digitos;
        List<String> candidatos = new ArrayList<>(List.of(d));
        if (d.length() == 10 && "6789".indexOf(d.charAt(2)) >= 0) candidatos.add(d.substring(0, 2) + "9" + d.substring(2));
        if (d.length() == 11 && d.charAt(2) == '9') candidatos.add(d.substring(0, 2) + d.substring(3));
        for (String c : candidatos) {
            Optional<Cliente> achou = clientes.findByTelefone(c);
            if (achou.isPresent()) return achou;
        }
        return Optional.empty();
    }

    /** minusculo, sem acento, sem emoji/pontuacao: "❌ Preciso cancelar!" -> "preciso cancelar". */
    static String normalizar(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(BR);
        return n.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String quando(Agendamento a, LocalDateTime agora) {
        LocalDate d = a.getInicio().toLocalDate();
        if (d.equals(agora.toLocalDate())) return "hoje";
        if (d.equals(agora.toLocalDate().plusDays(1))) return "amanhã";
        return DIA.format(a.getInicio());
    }

    private static String virgulaNome(String nome) {
        return nome == null || nome.isBlank() ? "" : ", " + nome;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> conversasRecentes() {
        return conversas.findTop100ByOrderByUltimaRecebidaEmDesc().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("telefone", c.getTelefone());
            m.put("nome", c.getNome());
            m.put("clienteId", c.getCliente() == null ? null : c.getCliente().getId());
            m.put("clienteNome", c.getCliente() == null ? null : c.getCliente().getNome());
            m.put("ultimaMensagem", c.getUltimaMensagem());
            m.put("ultimaRecebidaEm", c.getUltimaRecebidaEm());
            m.put("totalRecebidas", c.getTotalRecebidas());
            m.put("ultimaRespostaEm", c.getUltimaRespostaEm());
            m.put("ultimaAcao", c.getUltimaAcao());
            m.put("optOut", c.isOptOut());
            m.put("erroResposta", c.getErroResposta());
            return m;
        }).toList();
    }

    private static String primeiroNome(String n) {
        return n == null || n.isBlank() ? "" : n.trim().split("\\s+")[0];
    }

    private static String corta(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
