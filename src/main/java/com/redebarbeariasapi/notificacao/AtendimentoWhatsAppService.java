package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.AgendamentoRepository;
import com.redebarbeariasapi.repository.ClienteRepository;
import com.redebarbeariasapi.repository.ConfiguracaoAtendimentoRepository;
import com.redebarbeariasapi.repository.ConversaWhatsAppRepository;
import com.redebarbeariasapi.security.Sessao;
import com.redebarbeariasapi.service.AuditoriaService;
import com.redebarbeariasapi.service.Textos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Resposta automatica do WhatsApp: o cliente escreve ("oi", "tem horario?") e recebe na hora
 * a saudacao com o link do agendamento online. Se ja e cliente e tem horario marcado, a resposta
 * traz o horario e o link pra ver/cancelar. Depois disso fica quieto pelo intervalo configurado —
 * a conversa segue com a equipe, no proprio app do WhatsApp Business.
 */
@Service
public class AtendimentoWhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(AtendimentoWhatsAppService.class);
    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", BR);
    private static final Set<StatusAgendamento> ABERTOS = Set.of(StatusAgendamento.AGENDADO, StatusAgendamento.CONFIRMADO);

    private final ConversaWhatsAppRepository conversas;
    private final ConfiguracaoAtendimentoRepository configuracoes;
    private final ClienteRepository clientes;
    private final AgendamentoRepository agendamentos;
    private final WhatsAppCanal whatsapp;
    private final MensagemFactory mensagens;
    private final AuditoriaService auditoria;

    public AtendimentoWhatsAppService(ConversaWhatsAppRepository conversas, ConfiguracaoAtendimentoRepository configuracoes,
                                      ClienteRepository clientes, AgendamentoRepository agendamentos, WhatsAppCanal whatsapp,
                                      MensagemFactory mensagens, AuditoriaService auditoria) {
        this.conversas = conversas;
        this.configuracoes = configuracoes;
        this.clientes = clientes;
        this.agendamentos = agendamentos;
        this.whatsapp = whatsapp;
        this.mensagens = mensagens;
        this.auditoria = auditoria;
    }

    /** O que aconteceu com uma mensagem recebida (usado nos testes e no log). */
    public record Resultado(boolean respondeu, String resposta, String motivo) {
        static Resultado silencio(String motivo) { return new Resultado(false, null, motivo); }
    }

    @Transactional
    public ConfiguracaoAtendimento configuracao() {
        return configuracoes.findById(1L).orElseGet(() -> configuracoes.save(new ConfiguracaoAtendimento()));
    }

    @Transactional
    public ConfiguracaoAtendimento salvarConfiguracao(Boolean ligada, String saudacao, Integer intervaloHoras, Boolean mostrarProximo) {
        ConfiguracaoAtendimento c = configuracao();
        if (saudacao != null) {
            String s = saudacao.strip();
            if (s.isEmpty()) throw new ValidacaoException("Escreva a mensagem de boas-vindas.");
            if (s.length() > 1000) throw new ValidacaoException("A mensagem pode ter no máximo 1000 caracteres.");
            if (!s.contains("{link_agendar}")) throw new ValidacaoException("Inclua {link_agendar} na mensagem: é por ele que o cliente agenda.");
            c.setSaudacao(s);
        }
        if (intervaloHoras != null) {
            if (intervaloHoras < 1 || intervaloHoras > 168) throw new ValidacaoException("O intervalo deve ficar entre 1 e 168 horas.");
            c.setIntervaloHoras(intervaloHoras);
        }
        if (ligada != null) c.setRespostaAutomatica(ligada);
        if (mostrarProximo != null) c.setMostrarProximoHorario(mostrarProximo);
        c.setAtualizadoEm(LocalDateTime.now());
        c.setAtualizadoPor(Sessao.username());
        auditoria.registrar("CONFIGURAR", "AtendimentoWhatsApp", 1L,
                (c.isRespostaAutomatica() ? "ligada" : "desligada") + ", intervalo " + c.getIntervaloHoras() + "h");
        return c;
    }

    /** Chamado pelo webhook da Meta pra cada mensagem de cliente. */
    @Transactional
    public Resultado receber(String mensagemId, String telefoneWa, String nomePerfil, String texto, LocalDateTime agora) {
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
        if (nomePerfil != null && !nomePerfil.isBlank()) conv.setNome(nomePerfil.strip());
        else if (cliente != null && conv.getNome() == null) conv.setNome(cliente.getNome());
        conv.setUltimaMensagemId(mensagemId);
        conv.setUltimaMensagem(corta(texto == null || texto.isBlank() ? "(mídia / sem texto)" : texto, 500));
        conv.setUltimaRecebidaEm(agora);
        conv.setTotalRecebidas(conv.getTotalRecebidas() + 1);
        conversas.save(conv);

        ConfiguracaoAtendimento cfg = configuracao();
        if (!cfg.isRespostaAutomatica()) return Resultado.silencio("resposta automática desligada");
        if (conv.getUltimaRespostaEm() != null && conv.getUltimaRespostaEm().isAfter(agora.minusHours(cfg.getIntervaloHoras()))) {
            return Resultado.silencio("já respondido nas últimas " + cfg.getIntervaloHoras() + "h — conversa segue com a equipe");
        }
        String resposta = montarResposta(cfg, conv.getNome(), cliente, agora);
        if (!whatsapp.configurado()) {
            conv.setErroResposta("WhatsApp oficial não configurado no servidor");
            return Resultado.silencio("WhatsApp oficial não configurado");
        }
        try {
            whatsapp.enviarTexto(tel, resposta);
            conv.setUltimaRespostaEm(agora);
            conv.setUltimaResposta(corta(resposta, 1500));
            conv.setErroResposta(null);
            return new Resultado(true, resposta, "respondido");
        } catch (Exception e) {
            conv.setErroResposta(corta(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 500));
            log.warn("Resposta automatica pra {} falhou: {}", tel, conv.getErroResposta());
            return Resultado.silencio("falha ao enviar: " + conv.getErroResposta());
        }
    }

    /** Pra tela "simular": mostra a resposta que o cliente receberia, sem enviar nada. */
    @Transactional(readOnly = true)
    public String simular(String nome, String telefone, LocalDateTime agora) {
        ConfiguracaoAtendimento cfg = configuracoes.findById(1L).orElseGet(ConfiguracaoAtendimento::new);
        Cliente cliente = telefone == null || telefone.isBlank() ? null : clientePorTelefone(telefone.replaceAll("\\D", "")).orElse(null);
        String n = nome != null && !nome.isBlank() ? nome : cliente != null ? cliente.getNome() : null;
        return montarResposta(cfg, n, cliente, agora);
    }

    String montarResposta(ConfiguracaoAtendimento cfg, String nome, Cliente cliente, LocalDateTime agora) {
        String primeiro = primeiroNome(nome != null ? nome : cliente != null ? cliente.getNome() : null);
        String site = mensagens.siteUrl();
        String texto = cfg.getSaudacao()
                .replace("{link_agendar}", site + "/#/agendar")
                .replace("{link_site}", site)
                .replace("{nome}", primeiro);
        // "Olá, !" fica feio quando o perfil nao tem nome
        texto = texto.replaceAll("(?i)(ol[áa]|oi|bom dia|boa tarde|boa noite), !", "$1!").replace(" , ", " ");

        if (cfg.isMostrarProximoHorario() && cliente != null) {
            Optional<Agendamento> prox = agendamentos.filtrar(agora, agora.plusDays(90), null, null, cliente.getId(), null).stream()
                    .filter(a -> ABERTOS.contains(a.getStatus())).findFirst();
            if (prox.isPresent()) {
                Agendamento a = prox.get();
                String barbeiro = a.getBarbeiro().getApelido() != null ? a.getBarbeiro().getApelido() : primeiroNome(a.getBarbeiro().getNome());
                String quando = a.getInicio().toLocalDate().equals(agora.toLocalDate()) ? "hoje"
                        : a.getInicio().toLocalDate().equals(agora.toLocalDate().plusDays(1)) ? "amanhã"
                        : DIA.format(a.getInicio());
                texto += "\n\n📅 Seu próximo horário: " + quando + " às " + a.getInicio().format(Textos.HORA)
                        + " — " + a.getServico().getNome() + " com " + barbeiro
                        + "\nVer, remarcar ou cancelar: " + site + "/#/meu-horario/" + a.getCodigo();
            }
            String cartela = mensagens.cartelaFidelidade(cliente.getPontos());
            if (!cartela.isEmpty() && cliente.getPontos() > 0) texto += "\n\n🎯 " + cartela;
        }
        return texto;
    }

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
