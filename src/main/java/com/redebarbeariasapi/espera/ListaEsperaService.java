package com.redebarbeariasapi.espera;

import com.redebarbeariasapi.dto.SlotDTO;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.notificacao.MensagemFactory;
import com.redebarbeariasapi.notificacao.NotificacaoService;
import com.redebarbeariasapi.repository.*;
import com.redebarbeariasapi.security.Sessao;
import com.redebarbeariasapi.service.AgendamentoService;
import com.redebarbeariasapi.service.AuditoriaService;
import com.redebarbeariasapi.service.ClienteService;
import com.redebarbeariasapi.service.ConfiguracaoRedeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Lista de espera: o cliente queria um dia lotado e deixa o contato. Quando alguem cancela
 * naquele dia, os primeiros da fila (na ordem de chegada) que cabem no horario liberado recebem
 * o aviso com o link ja preenchido — quem agendar primeiro leva.
 */
@Service
public class ListaEsperaService {

    private static final Logger log = LoggerFactory.getLogger(ListaEsperaService.class);
    static final List<ListaEspera.Status> NA_FILA = List.of(ListaEspera.Status.AGUARDANDO, ListaEspera.Status.AVISADO);
    private static final int MAX_POR_CLIENTE = 3;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.redebarbeariasapi.sistema.Saude saude;
    private final ListaEsperaRepository repo;
    private final UnidadeRepository unidades;
    private final ServicoRepository servicos;
    private final BarbeiroRepository barbeiros;
    private final AgendamentoRepository agendamentos;
    private final ClienteService clientes;
    private final AgendamentoService agenda;
    private final ConfiguracaoRedeService configuracao;
    private final NotificacaoService notificacoes;
    private final MensagemFactory mensagens;
    private final AuditoriaService auditoria;

    public ListaEsperaService(ListaEsperaRepository repo, UnidadeRepository unidades, ServicoRepository servicos,
                              BarbeiroRepository barbeiros, AgendamentoRepository agendamentos, ClienteService clientes,
                              AgendamentoService agenda, ConfiguracaoRedeService configuracao, NotificacaoService notificacoes,
                              MensagemFactory mensagens, AuditoriaService auditoria) {
        this.repo = repo;
        this.unidades = unidades;
        this.servicos = servicos;
        this.barbeiros = barbeiros;
        this.agendamentos = agendamentos;
        this.clientes = clientes;
        this.agenda = agenda;
        this.configuracao = configuracao;
        this.notificacoes = notificacoes;
        this.mensagens = mensagens;
        this.auditoria = auditoria;
    }

    public record Entrada(Long unidadeId, Long servicoId, Long barbeiroId, LocalDate data, ListaEspera.Periodo periodo,
                          String nome, String telefone, String email, Boolean aceitaMarketing) {}

    /** Cliente (site ou area do cliente) entra na fila de um dia. */
    @Transactional
    public Map<String, Object> entrar(Entrada e, Cliente clienteFixo) {
        if (!configuracao.atual().isEsperaAtiva()) throw new BusinessException("A lista de espera não está disponível no momento.");
        if (e.unidadeId() == null || e.servicoId() == null || e.data() == null) throw new ValidacaoException("Informe unidade, serviço e dia.");
        Unidade u = unidades.findById(e.unidadeId()).orElseThrow(() -> ResourceNotFoundException.de("Unidade", e.unidadeId()));
        Servico s = servicos.findById(e.servicoId()).orElseThrow(() -> ResourceNotFoundException.de("Serviço", e.servicoId()));
        Barbeiro b = e.barbeiroId() == null ? null : barbeiros.findById(e.barbeiroId())
                .filter(x -> x.getUnidade().getId().equals(u.getId()))
                .orElseThrow(() -> new ValidacaoException("Esse barbeiro não atende nessa unidade."));
        LocalDate hoje = LocalDate.now();
        if (e.data().isBefore(hoje) || e.data().isAfter(hoje.plusDays(60))) throw new ValidacaoException("Escolha um dia entre hoje e os próximos 60 dias.");
        if (!u.isAtiva() || !u.dias().contains(e.data().getDayOfWeek())) throw new ValidacaoException("A unidade não abre nesse dia.");
        Cliente c = clienteFixo != null ? clienteFixo : clientes.obterOuCriar(e.nome(), e.telefone(), e.email(), e.aceitaMarketing());

        List<ListaEspera> minhas = repo.findByClienteIdAndStatusIn(c.getId(), NA_FILA);
        ListaEspera item = minhas.stream()
                .filter(x -> x.getUnidade().getId().equals(u.getId()) && x.getData().equals(e.data()))
                .findFirst().orElse(null);
        if (item == null) {
            if (minhas.size() >= MAX_POR_CLIENTE) throw new BusinessException("Você já está em " + MAX_POR_CLIENTE + " listas de espera. Saia de uma pra entrar em outra.");
            item = new ListaEspera();
            item.setCliente(c);
            item.setUnidade(u);
            item.setData(e.data());
        }
        // entrou de novo no mesmo dia: atualiza a preferencia sem perder o lugar na fila
        item.setServico(s);
        item.setBarbeiro(b);
        item.setPeriodo(e.periodo() == null ? ListaEspera.Periodo.QUALQUER : e.periodo());
        item.setStatus(ListaEspera.Status.AGUARDANDO);
        repo.save(item);
        final Long itemId = item.getId();
        long posicao = repo.findByUnidadeIdAndDataAndStatusInOrderByCriadoEm(u.getId(), e.data(), NA_FILA).stream()
                .map(ListaEspera::getId).toList().indexOf(itemId) + 1;
        auditoria.registrar("ESPERA", "ListaEspera", item.getId(), c.getNome() + " na fila de " + e.data());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", item.getId());
        r.put("posicao", posicao);
        r.put("mensagem", "Pronto! Você é o " + posicao + "º da lista de espera. Se abrir um horário nesse dia a gente te avisa na hora.");
        return r;
    }

    /** Depois que o cancelamento foi gravado (fora da requisicao, pra nao atrasar quem cancelou). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW) // chamada interna nao passa pelo proxy: a transacao abre aqui
    public void aoLiberarHorario(HorarioLiberadoEvento e) {
        try {
            avisar(e.agendamentoId());
        } catch (Exception ex) {
            log.warn("Lista de espera do horário {} não avisada: {}", e.agendamentoId(), ex.getMessage());
        }
    }

    /** Avisa os primeiros da fila que cabem num horario livre do dia. Devolve quantos foram avisados. */
    @Transactional
    public int avisar(Long agendamentoLiberadoId) {
        ConfiguracaoRede cfg = configuracao.atual();
        if (!cfg.isEsperaAtiva()) return 0;
        Agendamento liberado = agendamentos.findById(agendamentoLiberadoId).orElse(null);
        if (liberado == null || !liberado.getInicio().isAfter(LocalDateTime.now())) return 0;
        LocalDate dia = liberado.getInicio().toLocalDate();
        int avisados = 0;
        for (ListaEspera e : repo.findByUnidadeIdAndDataAndStatusInOrderByCriadoEm(liberado.getUnidade().getId(), dia, NA_FILA)) {
            if (avisados >= cfg.getEsperaAvisarQuantos()) break;
            if (e.getCliente().getId().equals(liberado.getCliente().getId())) continue;
            Optional<SlotDTO> vaga = vagaPara(e, liberado);
            if (vaga.isEmpty()) continue;
            SlotDTO slot = vaga.get();
            Barbeiro b = slot.barbeiros() == null || slot.barbeiros().isEmpty() ? null
                    : barbeiros.findById(slot.barbeiros().get(0).id()).orElse(null);
            int ok = notificacoes.enviarParaCliente(e.getCliente(), liberado, TipoNotificacao.VAGA_LIBERADA,
                    mensagens.vagaLiberada(e, slot.inicio(), b), "espera:" + e.getId() + ":" + liberado.getId());
            e.setStatus(ListaEspera.Status.AVISADO);
            e.setAvisos(e.getAvisos() + 1);
            e.setAvisadoEm(LocalDateTime.now());
            avisados++;
            log.info("Lista de espera: {} avisado da vaga de {} ({} envio(s))", e.getCliente().getNome(), slot.inicio(), ok);
        }
        return avisados;
    }

    /**
     * Horario livre que serve pra essa pessoa: consulta a agenda de verdade (o servico dela pode ser
     * mais longo que o que foi cancelado). Prefere o horario exato que liberou.
     */
    private Optional<SlotDTO> vagaPara(ListaEspera e, Agendamento liberado) {
        Long barbeiroId = e.getBarbeiro() == null ? null : e.getBarbeiro().getId();
        List<SlotDTO> livres = agenda.disponibilidade(e.getUnidade().getId(), e.getServico().getId(), e.getData(), barbeiroId).stream()
                .filter(s -> s.inicio().isAfter(LocalDateTime.now()) && e.cabeNoPeriodo(s.inicio().getHour()))
                .toList();
        return livres.stream().filter(s -> s.inicio().equals(liberado.getInicio())).findFirst()
                .or(() -> livres.stream().min(Comparator.comparing(s -> Math.abs(java.time.Duration.between(s.inicio(), liberado.getInicio()).toMinutes()))));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(LocalDate de, LocalDate ate, Long unidadeId) {
        Long un = Sessao.unidadeEscopo(unidadeId);
        return repo.periodo(de, ate, un).stream().map(ListaEsperaService::dto).toList();
    }

    @Transactional
    public Map<String, Object> alterarStatus(Long id, ListaEspera.Status status) {
        ListaEspera e = repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Lista de espera", id));
        Sessao.exigirUnidade(e.getUnidade().getId());
        e.setStatus(status);
        if (status == ListaEspera.Status.AVISADO) {
            e.setAvisos(e.getAvisos() + 1);
            e.setAvisadoEm(LocalDateTime.now());
        }
        auditoria.registrar("ESPERA", "ListaEspera", id, status.name());
        return dto(e);
    }

    /** Dia passou: sai da fila. */
    @Scheduled(initialDelay = 90_000, fixedDelay = 3_600_000)
    @Transactional
    public void expirar() {
        int n = repo.expirarAntesDe(LocalDate.now());
        if (n > 0) log.info("Lista de espera: {} pedido(s) de dias que já passaram expiraram", n);
        if (saude != null) saude.ok("lista-espera");
    }

    static Map<String, Object> dto(ListaEspera e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("data", e.getData());
        m.put("periodo", e.getPeriodo());
        m.put("status", e.getStatus());
        m.put("clienteId", e.getCliente().getId());
        m.put("clienteNome", e.getCliente().getNome());
        m.put("clienteTelefone", e.getCliente().getTelefone());
        m.put("unidadeId", e.getUnidade().getId());
        m.put("unidadeNome", e.getUnidade().getNome());
        m.put("servicoId", e.getServico().getId());
        m.put("servicoNome", e.getServico().getNome());
        m.put("barbeiroNome", e.getBarbeiro() == null ? null : e.getBarbeiro().getNome());
        m.put("avisos", e.getAvisos());
        m.put("avisadoEm", e.getAvisadoEm());
        m.put("criadoEm", e.getCriadoEm());
        return m;
    }
}
