package com.redebarbeariasapi.service;

import com.redebarbeariasapi.mapper.OperacaoMapper;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.*;
import com.redebarbeariasapi.security.Sessao;
import com.redebarbeariasapi.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Painel inicial: o que importa hoje, no mes, e alertas que pedem acao. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    private static final int DIAS_CLIENTE_SUMIDO = 45;

    private final AgendamentoRepository agendamentos;
    private final ClienteRepository clientes;
    private final ProdutoRepository produtos;
    private final DespesaRepository despesas;
    private final AssinaturaRepository assinaturas;
    private final AvaliacaoRepository avaliacoes;
    private final BarbeiroService barbeiroService;
    private final FinanceiroService financeiro;
    private final AgendamentoService agenda;

    public Map<String, Object> resumo(Long unidadeId) {
        UsuarioLogado eu = Sessao.atual();
        if (eu.papel() == Papel.BARBEIRO) return resumoBarbeiro(eu.barbeiroId());
        Long un = Sessao.unidadeEscopo(unidadeId);

        LocalDate hoje = LocalDate.now();
        LocalDate iniMes = hoje.withDayOfMonth(1);
        LocalDate iniMesAnt = iniMes.minusMonths(1);
        LocalDateTime agora = LocalDateTime.now();

        List<Agendamento> doDia = agendamentos.filtrar(hoje.atStartOfDay(), hoje.plusDays(1).atStartOfDay(), un, null, null, null);
        List<Agendamento> doMes = agendamentos.filtrar(iniMes.atStartOfDay(), iniMes.plusMonths(1).atStartOfDay(), un, null, null, null);
        FinanceiroService.Movimentos movHoje = financeiro.movimentos(hoje, hoje, un);
        FinanceiroService.Movimentos movMes = financeiro.movimentos(iniMes, hoje, un);
        FinanceiroService.Movimentos movAnt = financeiro.movimentos(iniMesAnt, hoje.minusMonths(1), un);
        FinanceiroService.Movimentos mov14 = financeiro.movimentos(hoje.minusDays(13), hoje, un);

        BigDecimal fatHoje = receita(movHoje), fatMes = receita(movMes), fatAnt = receita(movAnt);
        long atendidosMes = movMes.atendimentos().size();
        BigDecimal servMes = FinanceiroService.soma(movMes.atendimentos().stream().map(Agendamento::getValorFinal));
        long pagantesMes = movMes.atendimentos().stream().filter(a -> Textos.zeroSeNulo(a.getValorFinal()).signum() > 0).count();
        long passadosMes = doMes.stream().filter(a -> a.getInicio().isBefore(agora)).count();
        long cancelMes = doMes.stream().filter(a -> a.getStatus() == StatusAgendamento.CANCELADO).count();
        long faltasMes = doMes.stream().filter(a -> a.getStatus() == StatusAgendamento.NAO_COMPARECEU).count();

        List<Assinatura> ativas = assinaturas.findByStatus(StatusAssinatura.ATIVA);
        List<Produto> baixo = (un == null ? produtos.findAllByOrderByNome() : produtos.findByUnidadeIdOrderByNome(un))
                .stream().filter(p -> p.isAtivo() && p.estoqueBaixo()).toList();
        List<Avaliacao> avs = avaliacoes.filtrar(un, null);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("agendamentosHoje", doDia.stream().filter(a -> a.getStatus() != StatusAgendamento.CANCELADO).count());
        kpis.put("atendidosHoje", doDia.stream().filter(a -> a.getStatus() == StatusAgendamento.CONCLUIDO).count());
        kpis.put("aguardandoHoje", doDia.stream().filter(a -> !a.getStatus().finalizado()).count());
        kpis.put("faturamentoHoje", Textos.dinheiro(fatHoje));
        kpis.put("faturamentoMes", Textos.dinheiro(fatMes));
        kpis.put("faturamentoMesAnteriorMesmoPeriodo", Textos.dinheiro(fatAnt));
        kpis.put("variacaoMes", fatAnt.signum() == 0 ? null
                : fatMes.subtract(fatAnt).multiply(BigDecimal.valueOf(100)).divide(fatAnt, 1, RoundingMode.HALF_UP));
        kpis.put("atendimentosMes", atendidosMes);
        kpis.put("ticketMedio", pagantesMes == 0 ? BigDecimal.ZERO : servMes.divide(BigDecimal.valueOf(pagantesMes), 2, RoundingMode.HALF_UP));
        kpis.put("novosClientesMes", clientes.countByCriadoEmGreaterThanEqual(iniMes.atStartOfDay()));
        kpis.put("taxaCancelamento", pct(cancelMes, doMes.size()));
        kpis.put("taxaFalta", pct(faltasMes, passadosMes));
        kpis.put("notaMedia", avs.isEmpty() ? null : arred(avs.stream().mapToInt(Avaliacao::getNota).average().orElse(0)));
        kpis.put("totalAvaliacoes", avs.size());
        kpis.put("assinaturasAtivas", ativas.size());
        kpis.put("receitaRecorrenteMensal", Textos.dinheiro(FinanceiroService.soma(ativas.stream().map(a -> a.getPlano().getPrecoMensal()))));
        kpis.put("produtosEstoqueBaixo", baixo.size());
        kpis.put("despesasVencidas", despesas.vencidas(hoje, un));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tipo", "GESTAO");
        r.put("kpis", kpis);
        r.put("proximos", agenda.responder(doDia.stream()
                .filter(a -> !a.getStatus().finalizado() && a.getFim().isAfter(agora)).limit(10).toList()));
        r.put("faturamento14Dias", FinanceiroService.serieDiaria(hoje.minusDays(13), hoje, mov14));
        r.put("statusMes", contarStatus(doMes));
        r.put("formasPagamentoMes", FinanceiroService.porForma(movMes));
        r.put("topServicosMes", topServicos(movMes.atendimentos()));
        r.put("rankingBarbeirosMes", ranking(movMes.atendimentos()));
        r.put("horariosPico", horariosPico(un));
        r.put("aniversariantesMes", aniversariantes(hoje));
        r.put("clientesSumidos", sumidos(agora));
        r.put("estoqueBaixo", baixo.stream().limit(10).map(OperacaoMapper::produto).toList());
        return r;
    }

    private Map<String, Object> resumoBarbeiro(Long barbeiroId) {
        LocalDate hoje = LocalDate.now();
        LocalDate iniMes = hoje.withDayOfMonth(1);
        LocalDateTime agora = LocalDateTime.now();
        List<Agendamento> doDia = agendamentos.filtrar(hoje.atStartOfDay(), hoje.plusDays(1).atStartOfDay(), null, barbeiroId, null, null);
        List<Map<String, Object>> com = financeiro.comissoes(iniMes, hoje, null, barbeiroId);
        Map<String, Object> minha = com.isEmpty() ? Map.of() : com.get(0);
        List<Avaliacao> avs = avaliacoes.filtrar(null, barbeiroId);
        FinanceiroService.Movimentos mov14 = financeiro.movimentos(hoje.minusDays(13), hoje, null);
        FinanceiroService.Movimentos meu14 = new FinanceiroService.Movimentos(
                mov14.atendimentos().stream().filter(a -> a.getBarbeiro().getId().equals(barbeiroId)).toList(),
                List.of(), List.of(), List.of());

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("agendamentosHoje", doDia.stream().filter(a -> a.getStatus() != StatusAgendamento.CANCELADO).count());
        kpis.put("atendidosHoje", doDia.stream().filter(a -> a.getStatus() == StatusAgendamento.CONCLUIDO).count());
        kpis.put("atendimentosMes", minha.getOrDefault("atendimentos", 0));
        kpis.put("faturamentoMes", minha.getOrDefault("faturamentoServicos", BigDecimal.ZERO));
        kpis.put("comissaoMes", minha.getOrDefault("totalComissao", BigDecimal.ZERO));
        kpis.put("notaMedia", avs.isEmpty() ? null : arred(avs.stream().mapToInt(Avaliacao::getNota).average().orElse(0)));
        kpis.put("totalAvaliacoes", avs.size());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tipo", "BARBEIRO");
        r.put("kpis", kpis);
        r.put("agendaHoje", agenda.responder(doDia.stream().filter(a -> a.getStatus() != StatusAgendamento.CANCELADO).toList()));
        r.put("proximos", agenda.responder(doDia.stream().filter(a -> !a.getStatus().finalizado() && a.getFim().isAfter(agora)).toList()));
        r.put("faturamento14Dias", FinanceiroService.serieDiaria(hoje.minusDays(13), hoje, meu14));
        r.put("ultimasAvaliacoes", avs.stream().limit(5).map(OperacaoMapper::avaliacao).toList());
        return r;
    }

    private static BigDecimal receita(FinanceiroService.Movimentos m) {
        return FinanceiroService.soma(m.atendimentos().stream().map(Agendamento::getValorFinal))
                .add(FinanceiroService.soma(m.vendas().stream().map(Venda::getTotal)))
                .add(FinanceiroService.soma(m.mensalidades().stream().map(PagamentoAssinatura::getValor)));
    }

    private static Double pct(long parte, long total) {
        return total == 0 ? 0.0 : arred(parte * 100.0 / total);
    }

    private static Double arred(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static Map<String, Long> contarStatus(List<Agendamento> lista) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (StatusAgendamento s : StatusAgendamento.values()) m.put(s.name(), 0L);
        lista.forEach(a -> m.merge(a.getStatus().name(), 1L, Long::sum));
        return m;
    }

    private static List<Map<String, Object>> topServicos(List<Agendamento> pagos) {
        return pagos.stream().collect(Collectors.groupingBy(a -> a.getServico().getNome())).entrySet().stream()
                .map(e -> {
                    Map<String, Object> l = new LinkedHashMap<>();
                    l.put("nome", e.getKey());
                    l.put("quantidade", e.getValue().size());
                    l.put("valor", Textos.dinheiro(FinanceiroService.soma(e.getValue().stream().map(Agendamento::getValorFinal))));
                    return l;
                })
                .sorted(Comparator.comparing((Map<String, Object> l) -> (Integer) l.get("quantidade")).reversed())
                .limit(6).toList();
    }

    private List<Map<String, Object>> ranking(List<Agendamento> pagos) {
        Map<Long, double[]> notas = barbeiroService.notas();
        return pagos.stream().collect(Collectors.groupingBy(Agendamento::getBarbeiro)).entrySet().stream()
                .map(e -> {
                    Map<String, Object> l = new LinkedHashMap<>();
                    double[] n = notas.get(e.getKey().getId());
                    l.put("barbeiroId", e.getKey().getId());
                    l.put("nome", e.getKey().getNome());
                    l.put("unidade", e.getKey().getUnidade().getNome());
                    l.put("atendimentos", e.getValue().size());
                    l.put("faturamento", Textos.dinheiro(FinanceiroService.soma(e.getValue().stream().map(Agendamento::getValorFinal))));
                    l.put("nota", n == null ? null : n[0]);
                    return l;
                })
                .sorted(Comparator.comparing((Map<String, Object> l) -> (BigDecimal) l.get("faturamento")).reversed())
                .toList();
    }

    /** Movimento por hora do dia nos ultimos 30 dias — ajuda a montar escala de barbeiros. */
    private List<Map<String, Object>> horariosPico(Long un) {
        LocalDate hoje = LocalDate.now();
        List<Agendamento> ult = agendamentos.filtrar(hoje.minusDays(30).atStartOfDay(), hoje.plusDays(1).atStartOfDay(), un, null, null, null);
        Map<Integer, Long> porHora = new TreeMap<>();
        ult.stream().filter(a -> a.getStatus() != StatusAgendamento.CANCELADO)
                .forEach(a -> porHora.merge(a.getInicio().getHour(), 1L, Long::sum));
        List<Map<String, Object>> out = new ArrayList<>();
        porHora.forEach((h, q) -> out.add(Map.of("hora", String.format("%02dh", h), "quantidade", q)));
        return out;
    }

    private List<Map<String, Object>> aniversariantes(LocalDate hoje) {
        return clientes.findAllByOrderByNome().stream()
                .filter(c -> c.getDataNascimento() != null && c.getDataNascimento().getMonth() == hoje.getMonth())
                .sorted(Comparator.comparing(c -> c.getDataNascimento().getDayOfMonth()))
                .map(c -> {
                    Map<String, Object> l = new LinkedHashMap<>();
                    l.put("id", c.getId());
                    l.put("nome", c.getNome());
                    l.put("telefone", c.getTelefone());
                    l.put("dia", c.getDataNascimento().getDayOfMonth());
                    l.put("hoje", c.getDataNascimento().getDayOfMonth() == hoje.getDayOfMonth());
                    return l;
                }).toList();
    }

    /** Clientes que nao aparecem ha mais de 45 dias — lista pronta pra campanha de retorno. */
    private List<Map<String, Object>> sumidos(LocalDateTime agora) {
        return clientes.findAllByOrderByNome().stream()
                .filter(c -> c.getUltimaVisita() != null && c.getUltimaVisita().isBefore(agora.minusDays(DIAS_CLIENTE_SUMIDO)))
                .filter(Cliente::isAceitaMarketing)
                .sorted(Comparator.comparing(Cliente::getUltimaVisita))
                .limit(12)
                .map(c -> {
                    Map<String, Object> l = new LinkedHashMap<>();
                    l.put("id", c.getId());
                    l.put("nome", c.getNome());
                    l.put("telefone", c.getTelefone());
                    l.put("ultimaVisita", c.getUltimaVisita());
                    l.put("diasSemVir", ChronoUnit.DAYS.between(c.getUltimaVisita(), agora));
                    return l;
                }).toList();
    }
}
