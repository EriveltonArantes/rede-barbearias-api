package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.dto.NotificacaoResponseDTO;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.AgendamentoRepository;
import com.redebarbeariasapi.repository.AvaliacaoRepository;
import com.redebarbeariasapi.repository.NotificacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Decide o que mandar, por quais canais, e registra o resultado.
 * Regras: nunca manda a mesma mensagem duas vezes pelo mesmo canal; tenta de novo
 * no maximo 3 vezes quando falha; cliente sem e-mail simplesmente nao recebe e-mail.
 */
@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);
    private static final int MAX_FALHAS = 3;
    private static final Set<StatusAgendamento> ABERTOS = Set.of(StatusAgendamento.AGENDADO, StatusAgendamento.CONFIRMADO);

    private final List<CanalNotificacao> canais;
    private final MensagemFactory mensagens;
    private final NotificacaoRepository repo;
    private final AgendamentoRepository agendamentos;
    private final AvaliacaoRepository avaliacoes;
    private final int horaLembrete;
    private final int avaliacaoAposMinutos;

    public NotificacaoService(List<CanalNotificacao> canais, MensagemFactory mensagens, NotificacaoRepository repo,
                              AgendamentoRepository agendamentos, AvaliacaoRepository avaliacoes,
                              @Value("${app.notificacoes.hora-lembrete:7}") int horaLembrete,
                              @Value("${app.notificacoes.avaliacao-apos-minutos:90}") int avaliacaoAposMinutos) {
        this.canais = canais;
        this.mensagens = mensagens;
        this.repo = repo;
        this.agendamentos = agendamentos;
        this.avaliacoes = avaliacoes;
        this.horaLembrete = horaLembrete;
        this.avaliacaoAposMinutos = avaliacaoAposMinutos;
    }

    /** Envia uma mensagem de um agendamento por todos os canais disponiveis. Devolve quantos envios deram certo. */
    @Transactional
    public int enviar(Long agendamentoId, TipoNotificacao tipo) {
        return enviar(agendamentoId, tipo, false);
    }

    /** forcar=true: reenvio manual pedido pela recepcao (ignora o "ja enviado"). */
    @Transactional
    public int enviar(Long agendamentoId, TipoNotificacao tipo, boolean forcar) {
        Agendamento a = agendamentos.findById(agendamentoId).orElse(null);
        if (a == null || !faz_sentido(a, tipo)) return 0;
        Mensagem m = mensagens.criar(a, tipo);
        // a mesma mensagem nao sai duas vezes pro MESMO horario; se o horario mudar, vale de novo
        String referencia = a.getInicio().toString();
        int ok = 0;
        for (CanalNotificacao canal : canais) {
            if (!canal.configurado()) continue;
            String destino = canal.destino(m);
            if (destino == null) continue;
            if (!forcar && repo.existsByAgendamentoIdAndTipoAndCanalAndStatusAndReferencia(a.getId(), tipo, canal.tipo(), StatusNotificacao.ENVIADA, referencia)) continue;
            if (!forcar && repo.countByAgendamentoIdAndTipoAndCanalAndStatus(a.getId(), tipo, canal.tipo(), StatusNotificacao.FALHOU) >= MAX_FALHAS) continue;
            Notificacao n = new Notificacao();
            n.setAgendamento(a);
            n.setTipo(tipo);
            n.setCanal(canal.tipo());
            n.setDestino(destino);
            n.setReferencia(referencia);
            try {
                canal.enviar(m, destino);
                n.setStatus(StatusNotificacao.ENVIADA);
                ok++;
            } catch (Exception e) {
                n.setStatus(StatusNotificacao.FALHOU);
                String erro = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                n.setErro(erro.length() > 500 ? erro.substring(0, 500) : erro);
                log.warn("Falha enviando {} por {} para agendamento {}: {}", tipo, canal.tipo(), a.getId(), n.getErro());
            }
            repo.save(n);
        }
        if (ok > 0 && tipo == TipoNotificacao.LEMBRETE) a.setLembreteEnviado(true);
        return ok;
    }

    private boolean faz_sentido(Agendamento a, TipoNotificacao tipo) {
        return switch (tipo) {
            case CONFIRMACAO, REAGENDAMENTO, LEMBRETE -> ABERTOS.contains(a.getStatus()) && a.getInicio().isAfter(LocalDateTime.now());
            case CANCELAMENTO -> a.getStatus() == StatusAgendamento.CANCELADO && a.getInicio().isAfter(LocalDateTime.now());
            case AVALIACAO -> a.getStatus() == StatusAgendamento.CONCLUIDO && !avaliacoes.existsByAgendamentoId(a.getId());
        };
    }

    /**
     * Rodada periodica: lembrete do dia (a partir da hora configurada, so pra quem marcou
     * antes de hoje — quem marcou hoje acabou de receber a confirmacao) e pedido de avaliacao.
     */
    @Transactional
    public Map<String, Integer> processarAutomaticos(LocalDateTime agora) {
        int lembretes = 0, pedidos = 0;
        if (algumCanalConfigurado()) {
            LocalDate hoje = agora.toLocalDate();
            if (agora.getHour() >= horaLembrete) {
                for (Agendamento a : agendamentos.filtrar(hoje.atStartOfDay(), hoje.plusDays(1).atStartOfDay(), null, null, null, null)) {
                    if (ABERTOS.contains(a.getStatus()) && a.getInicio().isAfter(agora) && a.getCriadoEm().isBefore(hoje.atStartOfDay())) {
                        lembretes += enviar(a.getId(), TipoNotificacao.LEMBRETE);
                    }
                }
            }
            for (Agendamento a : agendamentos.pagosNoPeriodo(agora.minusHours(24), agora.minusMinutes(avaliacaoAposMinutos), null)) {
                if (a.getStatus() == StatusAgendamento.CONCLUIDO && a.getValorFinal() != null) {
                    pedidos += enviar(a.getId(), TipoNotificacao.AVALIACAO);
                }
            }
        }
        Map<String, Integer> r = new LinkedHashMap<>();
        r.put("lembretes", lembretes);
        r.put("pedidosAvaliacao", pedidos);
        return r;
    }

    public boolean algumCanalConfigurado() {
        return canais.stream().anyMatch(CanalNotificacao::configurado);
    }

    public List<Map<String, Object>> statusCanais() {
        return canais.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("canal", c.tipo());
            m.put("configurado", c.configurado());
            m.put("descricao", c.descricao());
            return m;
        }).toList();
    }

    /** Envia um e-mail/WhatsApp de teste usando um agendamento real como exemplo. */
    @Transactional(readOnly = true)
    public List<String> teste(String email, String telefone) {
        Agendamento exemplo = agendamentos.findAll().stream()
                .filter(a -> a.getInicio().isAfter(LocalDateTime.now())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Crie um agendamento futuro pra usar como exemplo."));
        Mensagem base = mensagens.criar(exemplo, TipoNotificacao.CONFIRMACAO);
        Mensagem m = new Mensagem(base.tipo(), "Teste", email, telefone, "[TESTE] " + base.assunto(), base.html(),
                base.texto(), base.modeloWhatsApp(), base.parametrosWhatsApp());
        List<String> resultado = new ArrayList<>();
        for (CanalNotificacao c : canais) {
            if (!c.configurado()) { resultado.add(c.tipo() + ": não configurado"); continue; }
            String destino = c.destino(m);
            if (destino == null) { resultado.add(c.tipo() + ": sem destino informado"); continue; }
            try {
                c.enviar(m, destino);
                resultado.add(c.tipo() + ": enviado para " + destino);
            } catch (Exception e) {
                resultado.add(c.tipo() + ": FALHOU — " + e.getMessage());
            }
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponseDTO> doAgendamento(Long agendamentoId) {
        return repo.findByAgendamentoIdOrderByDataHoraDesc(agendamentoId).stream().map(NotificacaoService::dto).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponseDTO> recentes() {
        return repo.findTop200ByOrderByDataHoraDesc().stream().map(NotificacaoService::dto).toList();
    }

    static NotificacaoResponseDTO dto(Notificacao n) {
        return new NotificacaoResponseDTO(n.getId(), n.getAgendamento().getId(), n.getAgendamento().getCodigo(),
                n.getAgendamento().getCliente().getNome(), n.getTipo(), n.getCanal(), n.getStatus(),
                n.getDestino(), n.getErro(), n.getDataHora());
    }
}
