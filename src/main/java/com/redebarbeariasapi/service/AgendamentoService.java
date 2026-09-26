package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.*;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.mapper.AgendamentoMapper;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.notificacao.AgendamentoEvento;
import com.redebarbeariasapi.repository.*;
import com.redebarbeariasapi.security.Sessao;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Coracao do sistema: agenda de cada barbeiro, com checagem de conflito,
 * horario de funcionamento, folgas/bloqueios, disponibilidade pro agendamento
 * online, ciclo de status e fechamento (pagamento + comissao + fidelidade + clube).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AgendamentoService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Set<StatusAgendamento> OCUPAM = StatusAgendamento.OCUPAM_HORARIO;

    private final SecureRandom random = new SecureRandom();
    private final AgendamentoRepository repo;
    private final BarbeiroRepository barbeiros;
    private final ServicoRepository servicos;
    private final UnidadeRepository unidades;
    private final BloqueioAgendaRepository bloqueios;
    private final ClienteRepository clientes;
    private final ClienteService clienteService;
    private final CupomRepository cupons;
    private final AssinaturaRepository assinaturas;
    private final AvaliacaoRepository avaliacoes;
    private final AuditoriaService auditoria;
    private final NotificacaoRepository notificacoes;
    private final ApplicationEventPublisher eventos;

    @Value("${app.agenda.intervalo-minutos:15}")
    private int intervaloMinutos;
    @Value("${app.agenda.antecedencia-cancelamento-horas:2}")
    private int antecedenciaCancelamentoHoras;
    @Value("${app.agenda.dias-maximos-antecedencia:60}")
    private int diasMaximos;
    @Value("${app.fidelidade.pontos-resgate:10}")
    private int pontosResgate;

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public List<AgendamentoResponseDTO> listar(LocalDate de, LocalDate ate, Long unidadeId, Long barbeiroId,
                                               Long clienteId, StatusAgendamento status) {
        if (ate.isBefore(de)) throw new ValidacaoException("A data final precisa ser depois da inicial.");
        if (de.plusDays(400).isBefore(ate)) throw new ValidacaoException("Período máximo de consulta: 400 dias.");
        Long un = Sessao.unidadeEscopo(unidadeId);
        if (Sessao.eh(Papel.BARBEIRO)) barbeiroId = Sessao.atual().barbeiroId();
        List<Agendamento> lista = repo.filtrar(de.atStartOfDay(), ate.plusDays(1).atStartOfDay(), un, barbeiroId, clienteId, status);
        return responder(lista);
    }

    public List<AgendamentoResponseDTO> responder(List<Agendamento> lista) {
        Map<Long, Integer> notas = notasDe(avaliacoes, lista);
        return lista.stream().map(a -> AgendamentoMapper.toResponse(a, notas.get(a.getId()))).toList();
    }

    public static Map<Long, Integer> notasDe(AvaliacaoRepository avaliacoes, Collection<Agendamento> lista) {
        if (lista.isEmpty()) return Map.of();
        Map<Long, Integer> m = new HashMap<>();
        List<Long> ids = lista.stream().map(Agendamento::getId).toList();
        for (int i = 0; i < ids.size(); i += 500) {
            for (Object[] l : avaliacoes.notasDe(ids.subList(i, Math.min(ids.size(), i + 500)))) {
                m.put((Long) l[0], (Integer) l[1]);
            }
        }
        return m;
    }

    public Agendamento obter(Long id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Agendamento", id));
    }

    @Transactional(readOnly = true)
    public AgendamentoResponseDTO buscar(Long id) {
        Agendamento a = obter(id);
        exigirAcesso(a);
        return AgendamentoMapper.toResponse(a, avaliacoes.findByAgendamentoId(id).map(Avaliacao::getNota).orElse(null));
    }

    /** Horarios livres de um dia pra um servico, com os barbeiros disponiveis em cada um. */
    @Transactional(readOnly = true)
    public List<SlotDTO> disponibilidade(Long unidadeId, Long servicoId, LocalDate data, Long barbeiroId) {
        Unidade u = unidades.findById(unidadeId).orElseThrow(() -> ResourceNotFoundException.de("Unidade", unidadeId));
        Servico s = servicos.findById(servicoId).orElseThrow(() -> ResourceNotFoundException.de("Serviço", servicoId));
        LocalDate hoje = LocalDate.now();
        if (!u.isAtiva() || !s.isAtivo() || data.isBefore(hoje) || data.isAfter(hoje.plusDays(diasMaximos))) return List.of();
        if (!u.dias().contains(data.getDayOfWeek())) return List.of();

        List<Barbeiro> candidatos;
        if (barbeiroId != null) {
            Barbeiro b = barbeiros.findById(barbeiroId).orElseThrow(() -> ResourceNotFoundException.de("Barbeiro", barbeiroId));
            if (!b.getUnidade().getId().equals(unidadeId)) throw new ValidacaoException("Esse barbeiro não atende nessa unidade.");
            candidatos = List.of(b);
        } else {
            candidatos = barbeiros.findByUnidadeIdAndAtivoTrueOrderByNome(unidadeId);
        }
        candidatos = candidatos.stream().filter(b -> b.isAtivo() && b.trabalhaEm(data.getDayOfWeek())).toList();
        if (candidatos.isEmpty()) return List.of();

        LocalDateTime abre = data.atTime(u.getHoraAbertura());
        LocalDateTime fecha = data.atTime(u.getHoraFechamento());
        List<Agendamento> ocupados = repo.ocupados(candidatos.stream().map(Barbeiro::getId).toList(), abre, fecha, OCUPAM);
        List<BloqueioAgenda> bloqs = bloqueios.sobrepostos(unidadeId, abre, fecha);
        LocalDateTime agora = LocalDateTime.now();

        List<SlotDTO> slots = new ArrayList<>();
        for (LocalDateTime t = abre; !t.plusMinutes(s.getDuracaoMinutos()).isAfter(fecha); t = t.plusMinutes(intervaloMinutos)) {
            if (!t.isAfter(agora)) continue;
            LocalDateTime inicio = t, fim = t.plusMinutes(s.getDuracaoMinutos());
            List<SlotDTO.Opcao> livres = candidatos.stream()
                    .filter(b -> livre(b.getId(), inicio, fim, ocupados, bloqs))
                    .map(b -> new SlotDTO.Opcao(b.getId(), b.getApelido() != null ? b.getApelido() : b.getNome()))
                    .toList();
            if (!livres.isEmpty()) slots.add(new SlotDTO(inicio.toLocalTime().format(Textos.HORA), inicio, livres));
        }
        return slots;
    }

    private static boolean livre(Long barbeiroId, LocalDateTime inicio, LocalDateTime fim,
                                 List<Agendamento> ocupados, List<BloqueioAgenda> bloqs) {
        for (Agendamento a : ocupados) {
            if (a.getBarbeiro().getId().equals(barbeiroId) && a.getInicio().isBefore(fim) && a.getFim().isAfter(inicio)) return false;
        }
        for (BloqueioAgenda b : bloqs) {
            boolean afeta = b.getBarbeiro() == null || b.getBarbeiro().getId().equals(barbeiroId);
            if (afeta && b.getInicio().isBefore(fim) && b.getFim().isAfter(inicio)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ criacao

    /** Agendamento feito pela equipe (balcao, telefone, WhatsApp). */
    public AgendamentoResponseDTO criar(AgendamentoRequestDTO dto) {
        Barbeiro b = barbeiros.findById(dto.barbeiroId()).orElseThrow(() -> ResourceNotFoundException.de("Barbeiro", dto.barbeiroId()));
        Sessao.exigirUnidade(b.getUnidade().getId());
        Sessao.exigirBarbeiro(b.getId());
        Servico s = servicoAtivo(dto.servicoId());
        Cliente c;
        if (dto.clienteId() != null) {
            c = clientes.findById(dto.clienteId()).orElseThrow(() -> ResourceNotFoundException.de("Cliente", dto.clienteId()));
        } else {
            if (Textos.vazio(dto.clienteNome()) || Textos.vazio(dto.clienteTelefone())) {
                throw new ValidacaoException("Escolha um cliente cadastrado ou informe nome e telefone.");
            }
            c = clienteService.obterOuCriar(dto.clienteNome(), dto.clienteTelefone(), dto.clienteEmail());
        }
        OrigemAgendamento origem = dto.origem() == null ? OrigemAgendamento.BALCAO : dto.origem();
        Agendamento a = novo(b, s, c, dto.inicio(), origem, dto.observacao(), dto.cupom(), true);
        return AgendamentoMapper.toResponse(a, null);
    }

    /** Agendamento online (site ou app do cliente). clienteFixo != null quando o cliente esta logado. */
    public Agendamento agendarOnline(AgendamentoPublicoRequestDTO dto, Cliente clienteFixo, OrigemAgendamento origem) {
        Unidade u = unidades.findById(dto.unidadeId()).orElseThrow(() -> ResourceNotFoundException.de("Unidade", dto.unidadeId()));
        if (!u.isAtiva()) throw new BusinessException("Essa unidade não está recebendo agendamentos.");
        Servico s = servicoAtivo(dto.servicoId());
        Cliente c = clienteFixo != null ? clienteFixo : clienteService.obterOuCriar(dto.nome(), dto.telefone(), dto.email());

        LocalDateTime inicio = normalizar(dto.inicio());
        LocalDateTime fim = inicio.plusMinutes(s.getDuracaoMinutos());
        Barbeiro b;
        if (dto.barbeiroId() != null) {
            b = barbeiros.findById(dto.barbeiroId()).orElseThrow(() -> ResourceNotFoundException.de("Barbeiro", dto.barbeiroId()));
            if (!b.getUnidade().getId().equals(u.getId())) throw new ValidacaoException("Esse barbeiro não atende nessa unidade.");
        } else {
            b = escolherBarbeiroLivre(u, inicio, fim);
        }
        long abertosDoCliente = repo.findByClienteIdOrderByInicioDesc(c.getId()).stream()
                .filter(a -> !a.getStatus().finalizado() && a.getInicio().isAfter(LocalDateTime.now())).count();
        if (abertosDoCliente >= 3) {
            throw new BusinessException("Você já tem 3 horários marcados. Compareça ou cancele um antes de marcar outro.");
        }
        return novo(b, s, c, inicio, origem, dto.observacao(), dto.cupom(), false);
    }

    /** "Sem preferencia": o barbeiro livre com menos atendimentos no dia (distribui a agenda). */
    private Barbeiro escolherBarbeiroLivre(Unidade u, LocalDateTime inicio, LocalDateTime fim) {
        List<Barbeiro> candidatos = barbeiros.findByUnidadeIdAndAtivoTrueOrderByNome(u.getId()).stream()
                .filter(b -> b.trabalhaEm(inicio.getDayOfWeek())).toList();
        if (candidatos.isEmpty()) throw new BusinessException("Nenhum barbeiro atende nesse dia.");
        LocalDateTime d0 = inicio.toLocalDate().atStartOfDay(), d1 = d0.plusDays(1);
        List<Agendamento> doDia = repo.ocupados(candidatos.stream().map(Barbeiro::getId).toList(), d0, d1, OCUPAM);
        List<BloqueioAgenda> bloqs = bloqueios.sobrepostos(u.getId(), d0, d1);
        Map<Long, Long> carga = doDia.stream().collect(Collectors.groupingBy(a -> a.getBarbeiro().getId(), Collectors.counting()));
        return candidatos.stream()
                .filter(b -> livre(b.getId(), inicio, fim, doDia, bloqs))
                .min(Comparator.comparing((Barbeiro b) -> carga.getOrDefault(b.getId(), 0L)).thenComparing(Barbeiro::getId))
                .orElseThrow(() -> new BusinessException("Esse horário acabou de ser preenchido. Escolha outro, por favor."));
    }

    private Agendamento novo(Barbeiro barbeiro, Servico s, Cliente c, LocalDateTime inicioBruto, OrigemAgendamento origem,
                             String observacao, String cupomCodigo, boolean equipe) {
        LocalDateTime inicio = normalizar(inicioBruto);
        LocalDateTime fim = inicio.plusMinutes(s.getDuracaoMinutos());
        validarJanela(barbeiro, inicio, fim, equipe);
        Barbeiro b = barbeiros.travar(barbeiro.getId()).orElseThrow();
        validarConflito(b, inicio, fim, null);

        Agendamento a = new Agendamento();
        a.setCodigo(gerarCodigo());
        a.setUnidade(b.getUnidade());
        a.setBarbeiro(b);
        a.setCliente(c);
        a.setServico(s);
        a.setInicio(inicio);
        a.setFim(fim);
        a.setOrigem(origem);
        a.setObservacao(observacao);
        a.setValor(s.getPreco());
        if (!Textos.vazio(cupomCodigo)) {
            Cupom cp = cupons.findByCodigoIgnoreCase(cupomCodigo.trim())
                    .filter(x -> x.valido(LocalDate.now()))
                    .orElseThrow(() -> new ValidacaoException("Cupom inválido, expirado ou esgotado."));
            a.setDesconto(cp.descontoPara(s.getPreco()));
            a.setCupomCodigo(cp.getCodigo());
            cp.setUsos(cp.getUsos() + 1);
        }
        repo.save(a);
        eventos.publishEvent(new AgendamentoEvento(a.getId(), TipoNotificacao.CONFIRMACAO));
        auditoria.registrar("AGENDAR", "Agendamento", a.getId(), c.getNome() + " com " + b.getNome() + " em "
                + inicio.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")) + " (" + origem + ")");
        return a;
    }

    private Servico servicoAtivo(Long id) {
        Servico s = servicos.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Serviço", id));
        if (!s.isAtivo()) throw new BusinessException("Esse serviço não está disponível.");
        return s;
    }

    private static LocalDateTime normalizar(LocalDateTime t) {
        return t.withSecond(0).withNano(0);
    }

    private void validarJanela(Barbeiro b, LocalDateTime inicio, LocalDateTime fim, boolean equipe) {
        LocalDateTime agora = LocalDateTime.now();
        if (!b.isAtivo()) throw new BusinessException(b.getNome() + " está inativo e não recebe agendamentos.");
        if (!b.getUnidade().isAtiva()) throw new BusinessException("A unidade " + b.getUnidade().getNome() + " está inativa.");
        if (equipe) {
            // a equipe pode lancar um encaixe que ja aconteceu hoje, mas nao reescrever o passado distante
            if (inicio.isBefore(agora.toLocalDate().atStartOfDay())) {
                throw new ValidacaoException("Não dá pra agendar em dia que já passou.");
            }
        } else if (!inicio.isAfter(agora)) {
            throw new ValidacaoException("Esse horário já passou. Escolha outro.");
        }
        if (inicio.toLocalDate().isAfter(agora.toLocalDate().plusDays(diasMaximos))) {
            throw new ValidacaoException("Agendamentos só podem ser feitos com até " + diasMaximos + " dias de antecedência.");
        }
        if (!fim.toLocalDate().equals(inicio.toLocalDate())) {
            throw new ValidacaoException("O atendimento precisa terminar no mesmo dia.");
        }
        if (!b.trabalhaEm(inicio.getDayOfWeek())) {
            throw new BusinessException(b.getNome() + " não atende nesse dia da semana.");
        }
        Unidade u = b.getUnidade();
        if (inicio.toLocalTime().isBefore(u.getHoraAbertura()) || fim.toLocalTime().isAfter(u.getHoraFechamento())) {
            throw new BusinessException("Fora do horário de funcionamento da unidade ("
                    + u.getHoraAbertura().format(Textos.HORA) + " às " + u.getHoraFechamento().format(Textos.HORA) + ").");
        }
        for (BloqueioAgenda bl : bloqueios.sobrepostos(u.getId(), inicio, fim)) {
            if (bl.getBarbeiro() == null || bl.getBarbeiro().getId().equals(b.getId())) {
                throw new BusinessException("Agenda bloqueada nesse horário: " + bl.getMotivo() + ".");
            }
        }
    }

    private void validarConflito(Barbeiro b, LocalDateTime inicio, LocalDateTime fim, Long ignorarId) {
        List<Agendamento> conflitos = repo.conflitos(b.getId(), inicio, fim, OCUPAM, ignorarId);
        if (!conflitos.isEmpty()) {
            Agendamento x = conflitos.get(0);
            throw new BusinessException("Horário indisponível: " + b.getNome() + " já tem atendimento das "
                    + x.getInicio().format(Textos.HORA) + " às " + x.getFim().format(Textos.HORA) + ".");
        }
    }

    private String gerarCodigo() {
        for (int tentativa = 0; tentativa < 20; tentativa++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
            if (!repo.existsByCodigo(sb.toString())) return sb.toString();
        }
        throw new IllegalStateException("Não consegui gerar código único");
    }

    // ------------------------------------------------------------------ alteracao

    /** Reagendar / trocar barbeiro, servico ou cliente (so enquanto nao finalizado). */
    public AgendamentoResponseDTO atualizar(Long id, AgendamentoRequestDTO dto) {
        Agendamento a = obter(id);
        exigirAcesso(a);
        if (a.getStatus().finalizado()) throw new BusinessException("Atendimento já finalizado não pode ser alterado.");
        Barbeiro b = barbeiros.findById(dto.barbeiroId()).orElseThrow(() -> ResourceNotFoundException.de("Barbeiro", dto.barbeiroId()));
        Sessao.exigirUnidade(b.getUnidade().getId());
        Sessao.exigirBarbeiro(b.getId());
        Servico s = servicoAtivo(dto.servicoId());
        LocalDateTime inicio = normalizar(dto.inicio());
        LocalDateTime fim = inicio.plusMinutes(s.getDuracaoMinutos());
        validarJanela(b, inicio, fim, true);
        barbeiros.travar(b.getId());
        validarConflito(b, inicio, fim, a.getId());

        boolean mudouParaCliente = !inicio.equals(a.getInicio()) || !b.getId().equals(a.getBarbeiro().getId())
                || !s.getId().equals(a.getServico().getId());
        String antes = a.getBarbeiro().getNome() + " " + a.getInicio().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
        if (dto.clienteId() != null && !dto.clienteId().equals(a.getCliente().getId())) {
            a.setCliente(clientes.findById(dto.clienteId()).orElseThrow(() -> ResourceNotFoundException.de("Cliente", dto.clienteId())));
        }
        a.setBarbeiro(b);
        a.setUnidade(b.getUnidade());
        a.setServico(s);
        a.setInicio(inicio);
        a.setFim(fim);
        a.setValor(s.getPreco());
        if (a.getCupomCodigo() != null) {
            cupons.findByCodigoIgnoreCase(a.getCupomCodigo()).ifPresent(cp -> a.setDesconto(cp.descontoPara(s.getPreco())));
        }
        a.setDesconto(Textos.zeroSeNulo(a.getDesconto()).min(s.getPreco()));
        a.setObservacao(dto.observacao());
        a.setAtualizadoEm(LocalDateTime.now());
        if (mudouParaCliente) eventos.publishEvent(new AgendamentoEvento(a.getId(), TipoNotificacao.REAGENDAMENTO));
        auditoria.registrar("REAGENDAR", "Agendamento", id, antes + " -> " + b.getNome() + " "
                + inicio.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")));
        return AgendamentoMapper.toResponse(a, null);
    }

    public AgendamentoResponseDTO alterarStatus(Long id, StatusRequestDTO dto) {
        Agendamento a = obter(id);
        exigirAcesso(a);
        StatusAgendamento novo = dto.status();
        if (novo == StatusAgendamento.CONCLUIDO) {
            throw new ValidacaoException("Use \"Finalizar atendimento\" pra concluir e registrar o pagamento.");
        }
        if (novo == StatusAgendamento.AGENDADO) throw new ValidacaoException("Status inválido pra essa ação.");
        if (a.getStatus().finalizado()) throw new BusinessException("Esse atendimento já está " + a.getStatus() + ".");
        if (novo == StatusAgendamento.CANCELADO && Sessao.eh(Papel.BARBEIRO)) {
            throw new AccessDeniedException("Cancelamento é feito pela recepção.");
        }
        if (novo == StatusAgendamento.NAO_COMPARECEU && a.getInicio().isAfter(LocalDateTime.now())) {
            throw new ValidacaoException("Só dá pra marcar falta depois do horário marcado.");
        }
        if (novo == StatusAgendamento.CANCELADO) {
            cancelar(a, Textos.vazio(dto.motivo()) ? "Cancelado pela equipe" : dto.motivo());
        } else {
            a.setStatus(novo);
            a.setAtualizadoEm(LocalDateTime.now());
            auditoria.registrar("STATUS", "Agendamento", id, novo.name());
        }
        return AgendamentoMapper.toResponse(a, null);
    }

    private void cancelar(Agendamento a, String motivo) {
        cancelar(a, motivo, true);
    }

    /** avisarCliente=false quando o proprio cliente cancelou por uma conversa que ja responde na hora (WhatsApp). */
    private void cancelar(Agendamento a, String motivo, boolean avisarCliente) {
        a.setStatus(StatusAgendamento.CANCELADO);
        a.setMotivoCancelamento(motivo);
        a.setAtualizadoEm(LocalDateTime.now());
        if (a.getCupomCodigo() != null) {
            cupons.findByCodigoIgnoreCase(a.getCupomCodigo()).ifPresent(cp -> cp.setUsos(Math.max(0, cp.getUsos() - 1)));
        }
        if (avisarCliente) eventos.publishEvent(new AgendamentoEvento(a.getId(), TipoNotificacao.CANCELAMENTO));
        auditoria.registrar("CANCELAR", "Agendamento", a.getId(), motivo);
    }

    /**
     * Cliente confirmou presenca (botao do lembrete no WhatsApp ou pagina "meu horario").
     * Devolve false quando nao havia o que confirmar (ja confirmado).
     */
    public boolean confirmarPeloCliente(Agendamento a, String canal) {
        if (a.getStatus().finalizado()) throw new BusinessException("Esse agendamento já está " + a.getStatus() + ".");
        if (!a.getInicio().isAfter(LocalDateTime.now())) throw new BusinessException("Esse horário já passou.");
        if (a.getStatus() == StatusAgendamento.CONFIRMADO) return false;
        a.setStatus(StatusAgendamento.CONFIRMADO);
        a.setAtualizadoEm(LocalDateTime.now());
        auditoria.registrar("CONFIRMAR", "Agendamento", a.getId(), "Cliente confirmou presença (" + canal + ")");
        return true;
    }

    /**
     * Cliente avisou pelo WhatsApp que nao vem. Diferente do cancelamento pelo site, vale mesmo
     * em cima da hora: e melhor liberar a cadeira do que esperar alguem que ja avisou que falta.
     * Nao dispara o aviso de cancelamento — a resposta da conversa ja confirma pra ele.
     */
    public void cancelarPeloWhatsApp(Agendamento a) {
        if (a.getStatus().finalizado()) throw new BusinessException("Esse agendamento já está " + a.getStatus() + ".");
        boolean tardio = !podeCancelar(a);
        cancelar(a, "Cliente pelo WhatsApp" + (tardio ? " (menos de " + antecedenciaCancelamentoHoras + "h antes)" : ""), false);
    }

    /** Fecha o atendimento: cobra (ou usa clube/fidelidade), calcula comissao e pontua o cliente. */
    public AgendamentoResponseDTO finalizar(Long id, FinalizarRequestDTO dto) {
        Agendamento a = obter(id);
        exigirAcesso(a);
        if (a.getStatus().finalizado()) throw new BusinessException("Esse atendimento já está " + a.getStatus() + ".");
        if (a.getInicio().toLocalDate().isAfter(LocalDate.now())) {
            throw new BusinessException("Não dá pra finalizar um atendimento marcado pra outro dia.");
        }
        Cliente c = a.getCliente();
        Servico s = a.getServico();
        BigDecimal extra = Textos.zeroSeNulo(dto.descontoExtra());
        FormaPagamento forma;
        BigDecimal cobrado;
        boolean ganhaPonto = true;

        if (dto.usarAssinatura()) {
            Assinatura as = assinaturaQueCobre(c, s)
                    .orElseThrow(() -> new BusinessException("O cliente não tem assinatura ativa, em dia e com saldo que cubra \"" + s.getNome() + "\"."));
            as.setUsosNoCiclo(as.getUsosNoCiclo() + 1);
            forma = FormaPagamento.ASSINATURA;
            cobrado = BigDecimal.ZERO;
        } else if (dto.usarFidelidade()) {
            if (c.getPontos() < pontosResgate) {
                throw new BusinessException("O cliente tem " + c.getPontos() + " ponto(s); precisa de " + pontosResgate + " pra resgatar.");
            }
            c.setPontos(c.getPontos() - pontosResgate);
            forma = FormaPagamento.CORTESIA;
            cobrado = BigDecimal.ZERO;
            ganhaPonto = false;
        } else {
            if (dto.formaPagamento() == null || dto.formaPagamento() == FormaPagamento.ASSINATURA || dto.formaPagamento() == FormaPagamento.CORTESIA) {
                throw new ValidacaoException("Informe como o cliente pagou (dinheiro, Pix, crédito ou débito).");
            }
            forma = dto.formaPagamento();
            BigDecimal aPagar = a.valorAPagar();
            BigDecimal descontoExtra = extra.min(aPagar);
            a.setDesconto(Textos.zeroSeNulo(a.getDesconto()).add(descontoExtra));
            cobrado = aPagar.subtract(descontoExtra);
        }

        // comissao sobre o valor cobrado; atendimento do clube/cortesia paga comissao sobre o preco de tabela
        BigDecimal base = cobrado.signum() > 0 ? cobrado : a.getValor();
        a.setComissaoValor(Textos.percentual(base, a.getBarbeiro().getComissaoServico()));
        a.setValorFinal(Textos.dinheiro(cobrado));
        a.setFormaPagamento(forma);
        a.setPago(true);
        a.setPagoEm(LocalDateTime.now());
        a.setStatus(StatusAgendamento.CONCLUIDO);
        a.setAtualizadoEm(LocalDateTime.now());
        if (ganhaPonto) c.setPontos(c.getPontos() + 1);
        c.setUltimaVisita(LocalDateTime.now());
        auditoria.registrar("FINALIZAR", "Agendamento", id, forma + " R$ " + a.getValorFinal()
                + (extra.signum() > 0 ? " (desconto extra R$ " + extra + ")" : ""));
        return AgendamentoMapper.toResponse(a, null);
    }

    public Optional<Assinatura> assinaturaQueCobre(Cliente c, Servico s) {
        LocalDate hoje = LocalDate.now();
        return assinaturas.findByClienteIdAndStatus(c.getId(), StatusAssinatura.ATIVA).stream()
                .filter(as -> as.utilizavelEm(hoje))
                .filter(as -> as.getUsosNoCiclo() < as.getPlano().getUsosPorMes())
                .filter(as -> as.getPlano().getServicos().stream().anyMatch(x -> x.getId().equals(s.getId())))
                .findFirst();
    }

    public AgendamentoResponseDTO marcarLembrete(Long id) {
        Agendamento a = obter(id);
        exigirAcesso(a);
        a.setLembreteEnviado(true);
        return AgendamentoMapper.toResponse(a, null);
    }

    public void excluir(Long id) {
        Agendamento a = obter(id);
        if (a.isPago()) throw new BusinessException("Atendimento pago não pode ser excluído (afeta o caixa). Use o cancelamento.");
        avaliacoes.findByAgendamentoId(id).ifPresent(avaliacoes::delete);
        notificacoes.deleteByAgendamentoId(id);
        repo.delete(a);
        auditoria.registrar("EXCLUIR", "Agendamento", id, a.getCliente().getNome() + " " + a.getInicio());
    }

    private void exigirAcesso(Agendamento a) {
        Sessao.exigirUnidade(a.getUnidade().getId());
        Sessao.exigirBarbeiro(a.getBarbeiro().getId());
    }

    // ------------------------------------------------------------------ cliente (sem login ou logado)

    public boolean podeCancelar(Agendamento a) {
        return !a.getStatus().finalizado()
                && a.getInicio().minusHours(antecedenciaCancelamentoHoras).isAfter(LocalDateTime.now());
    }

    public boolean podeAvaliar(Agendamento a) {
        return a.getStatus() == StatusAgendamento.CONCLUIDO && !avaliacoes.existsByAgendamentoId(a.getId());
    }

    @Transactional(readOnly = true)
    public AgendamentoPublicoResponseDTO publico(Agendamento a) {
        Integer nota = avaliacoes.findByAgendamentoId(a.getId()).map(Avaliacao::getNota).orElse(null);
        return AgendamentoMapper.toPublico(a, podeCancelar(a), nota == null && a.getStatus() == StatusAgendamento.CONCLUIDO, nota);
    }

    public Agendamento porCodigo(String codigo) {
        return repo.findByCodigo(codigo == null ? "" : codigo.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado. Confira o código."));
    }

    public void cancelarPeloCliente(Agendamento a, String motivo) {
        if (a.getStatus().finalizado()) throw new BusinessException("Esse agendamento já está " + a.getStatus() + ".");
        if (!podeCancelar(a)) {
            throw new BusinessException("Cancelamento online só até " + antecedenciaCancelamentoHoras
                    + "h antes. Fale com a unidade pelo WhatsApp.");
        }
        cancelar(a, "Cliente: " + (Textos.vazio(motivo) ? "sem motivo informado" : motivo));
    }

    public Avaliacao avaliar(Agendamento a, AvaliacaoRequestDTO dto) {
        if (a.getStatus() != StatusAgendamento.CONCLUIDO) throw new BusinessException("Só dá pra avaliar depois do atendimento.");
        if (avaliacoes.existsByAgendamentoId(a.getId())) throw new BusinessException("Esse atendimento já foi avaliado. Obrigado!");
        Avaliacao av = new Avaliacao();
        av.setAgendamento(a);
        av.setNota(dto.nota());
        av.setComentario(Textos.vazio(dto.comentario()) ? null : dto.comentario().trim());
        return avaliacoes.save(av);
    }

    public List<Agendamento> doCliente(Long clienteId) {
        return repo.findByClienteIdOrderByInicioDesc(clienteId);
    }
}
