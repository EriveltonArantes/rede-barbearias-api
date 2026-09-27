package com.redebarbeariasapi.service;

import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.*;
import com.redebarbeariasapi.security.Sessao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Financeiro da rede: receitas (servicos + produtos + mensalidades do clube),
 * despesas, comissoes, lucro estimado e fechamento de caixa diario.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FinanceiroService {

    private final AgendamentoRepository agendamentos;
    private final VendaRepository vendas;
    private final PagamentoAssinaturaRepository mensalidades;
    private final DespesaRepository despesas;
    private final BarbeiroRepository barbeiros;
    private final UnidadeRepository unidades;

    public record Movimentos(List<Agendamento> atendimentos, List<Venda> vendas,
                             List<PagamentoAssinatura> mensalidades, List<Despesa> despesas) {}

    public Movimentos movimentos(LocalDate de, LocalDate ate, Long unidadeId) {
        if (ate.isBefore(de)) throw new ValidacaoException("A data final precisa ser depois da inicial.");
        LocalDateTime d0 = de.atStartOfDay(), d1 = ate.plusDays(1).atStartOfDay();
        return new Movimentos(
                agendamentos.pagosNoPeriodo(d0, d1, unidadeId),
                vendas.periodo(d0, d1, unidadeId).stream().filter(v -> !v.isCancelada()).toList(),
                mensalidades.periodo(d0, d1, unidadeId),
                despesas.periodo(de, ate, unidadeId));
    }

    public Map<String, Object> resumo(LocalDate de, LocalDate ate, Long unidadeId) {
        Long un = Sessao.unidadeEscopo(unidadeId);
        Movimentos m = movimentos(de, ate, un);

        BigDecimal servicos = soma(m.atendimentos().stream().map(Agendamento::getValorFinal));
        BigDecimal produtos = soma(m.vendas().stream().map(Venda::getTotal));
        BigDecimal clube = soma(m.mensalidades().stream().map(PagamentoAssinatura::getValor));
        // sinal de quem faltou / cancelou em cima da hora fica com a barbearia: entra como receita no dia do Pix
        BigDecimal sinais = soma(agendamentos.sinaisRetidos(de.atStartOfDay(), ate.plusDays(1).atStartOfDay(), un).stream()
                .map(Agendamento::getSinalValor));
        BigDecimal receita = servicos.add(produtos).add(clube).add(sinais);
        BigDecimal custoProdutos = soma(m.vendas().stream().flatMap(v -> v.getItens().stream())
                .map(i -> Textos.zeroSeNulo(i.getProduto().getPrecoCusto()).multiply(BigDecimal.valueOf(i.getQuantidade()))));
        BigDecimal comissoes = soma(m.atendimentos().stream().map(Agendamento::getComissaoValor))
                .add(soma(m.vendas().stream().map(Venda::getComissaoValor)));
        BigDecimal descontos = soma(m.atendimentos().stream().map(Agendamento::getDesconto))
                .add(soma(m.vendas().stream().map(Venda::getDesconto)));
        BigDecimal despesaTotal = soma(m.despesas().stream().map(Despesa::getValor));
        BigDecimal despesaPaga = soma(m.despesas().stream().filter(Despesa::isPaga).map(Despesa::getValor));
        BigDecimal lucro = receita.subtract(custoProdutos).subtract(comissoes).subtract(despesaTotal);
        long pagantes = m.atendimentos().stream().filter(a -> Textos.zeroSeNulo(a.getValorFinal()).signum() > 0).count();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("de", de);
        r.put("ate", ate);
        r.put("unidadeId", un);
        r.put("receitaTotal", Textos.dinheiro(receita));
        r.put("receitaServicos", Textos.dinheiro(servicos));
        r.put("receitaProdutos", Textos.dinheiro(produtos));
        r.put("receitaClube", Textos.dinheiro(clube));
        r.put("receitaSinaisRetidos", Textos.dinheiro(sinais));
        r.put("custoProdutos", Textos.dinheiro(custoProdutos));
        r.put("comissoes", Textos.dinheiro(comissoes));
        r.put("descontosConcedidos", Textos.dinheiro(descontos));
        r.put("despesas", Textos.dinheiro(despesaTotal));
        r.put("despesasPagas", Textos.dinheiro(despesaPaga));
        r.put("despesasAPagar", Textos.dinheiro(despesaTotal.subtract(despesaPaga)));
        r.put("lucroEstimado", Textos.dinheiro(lucro));
        r.put("margemPercentual", receita.signum() == 0 ? BigDecimal.ZERO
                : lucro.multiply(BigDecimal.valueOf(100)).divide(receita, 1, RoundingMode.HALF_UP));
        r.put("atendimentos", m.atendimentos().size());
        r.put("ticketMedio", pagantes == 0 ? BigDecimal.ZERO : servicos.divide(BigDecimal.valueOf(pagantes), 2, RoundingMode.HALF_UP));
        r.put("vendasProdutos", m.vendas().size());
        r.put("porFormaPagamento", porForma(m));
        r.put("despesasPorCategoria", despesasPorCategoria(m.despesas()));
        r.put("serieDiaria", serieDiaria(de, ate, m));
        if (un == null) r.put("porUnidade", porUnidade(m));
        return r;
    }

    /** Comissao de cada barbeiro no periodo (servicos + produtos). */
    public List<Map<String, Object>> comissoes(LocalDate de, LocalDate ate, Long unidadeId, Long barbeiroId) {
        Long un = Sessao.unidadeEscopo(unidadeId);
        if (Sessao.eh(Papel.BARBEIRO)) barbeiroId = Sessao.atual().barbeiroId();
        Movimentos m = movimentos(de, ate, un);
        List<Barbeiro> lista = barbeiroId != null ? barbeiros.findById(barbeiroId).stream().toList()
                : un != null ? barbeiros.findByUnidadeIdOrderByNome(un) : barbeiros.findAllByOrderByNome();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Barbeiro b : lista) {
            List<Agendamento> ags = m.atendimentos().stream().filter(a -> a.getBarbeiro().getId().equals(b.getId())).toList();
            List<Venda> vs = m.vendas().stream().filter(v -> v.getBarbeiro() != null && v.getBarbeiro().getId().equals(b.getId())).toList();
            BigDecimal fatServ = soma(ags.stream().map(Agendamento::getValorFinal));
            BigDecimal comServ = soma(ags.stream().map(Agendamento::getComissaoValor));
            BigDecimal fatProd = soma(vs.stream().map(Venda::getTotal));
            BigDecimal comProd = soma(vs.stream().map(Venda::getComissaoValor));
            if (ags.isEmpty() && vs.isEmpty() && !b.isAtivo()) continue;
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("barbeiroId", b.getId());
            l.put("barbeiroNome", b.getNome());
            l.put("unidadeNome", b.getUnidade().getNome());
            l.put("percentualServico", b.getComissaoServico());
            l.put("percentualProduto", b.getComissaoProduto());
            l.put("atendimentos", ags.size());
            l.put("atendimentosClube", ags.stream().filter(a -> a.getFormaPagamento() == FormaPagamento.ASSINATURA).count());
            l.put("faturamentoServicos", Textos.dinheiro(fatServ));
            l.put("comissaoServicos", Textos.dinheiro(comServ));
            l.put("vendasProdutos", vs.size());
            l.put("faturamentoProdutos", Textos.dinheiro(fatProd));
            l.put("comissaoProdutos", Textos.dinheiro(comProd));
            l.put("totalComissao", Textos.dinheiro(comServ.add(comProd)));
            out.add(l);
        }
        out.sort(Comparator.comparing((Map<String, Object> x) -> (BigDecimal) x.get("totalComissao")).reversed());
        return out;
    }

    /** Fechamento de caixa do dia: cada entrada, total por forma de pagamento. */
    public Map<String, Object> caixa(LocalDate dia, Long unidadeId) {
        Long un = Sessao.unidadeEscopo(unidadeId);
        Movimentos m = movimentos(dia, dia, un);
        List<Map<String, Object>> linhas = new ArrayList<>();
        for (Agendamento a : m.atendimentos()) {
            linhas.add(linha(a.getPagoEm(), "Atendimento", a.getServico().getNome() + " — " + a.getCliente().getNome()
                    + " (" + a.getBarbeiro().getNome() + ")", a.getFormaPagamento(), a.getValorFinal(), a.getUnidade().getNome()));
        }
        for (Venda v : m.vendas()) {
            linhas.add(linha(v.getDataHora(), "Produto", "Venda #" + v.getId() + " — " + v.getItens().size() + " item(ns)"
                    + (v.getCliente() != null ? " — " + v.getCliente().getNome() : ""), v.getFormaPagamento(), v.getTotal(), v.getUnidade().getNome()));
        }
        for (PagamentoAssinatura p : m.mensalidades()) {
            linhas.add(linha(p.getDataHora(), "Clube", "Mensalidade " + p.getAssinatura().getPlano().getNome() + " — "
                    + p.getAssinatura().getCliente().getNome(), p.getFormaPagamento(), p.getValor(), p.getUnidade().getNome()));
        }
        linhas.sort(Comparator.comparing(x -> (LocalDateTime) x.get("dataHora")));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("data", dia);
        r.put("unidadeId", un);
        r.put("totalEntradas", Textos.dinheiro(soma(linhas.stream().map(x -> (BigDecimal) x.get("valor")))));
        r.put("porFormaPagamento", porForma(m));
        r.put("lancamentos", linhas);
        return r;
    }

    // ----------------------------------------------------------------- auxiliares

    private static Map<String, Object> linha(LocalDateTime quando, String tipo, String descricao,
                                             FormaPagamento forma, BigDecimal valor, String unidade) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("dataHora", quando);
        l.put("tipo", tipo);
        l.put("descricao", descricao);
        l.put("formaPagamento", forma);
        l.put("valor", Textos.dinheiro(valor));
        l.put("unidade", unidade);
        return l;
    }

    static BigDecimal soma(java.util.stream.Stream<BigDecimal> valores) {
        return valores.map(Textos::zeroSeNulo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static Map<String, BigDecimal> porForma(Movimentos m) {
        Map<String, BigDecimal> f = new LinkedHashMap<>();
        for (FormaPagamento fp : FormaPagamento.values()) f.put(fp.name(), BigDecimal.ZERO);
        m.atendimentos().forEach(a -> f.merge(a.getFormaPagamento().name(), Textos.zeroSeNulo(a.getValorFinal()), BigDecimal::add));
        m.vendas().forEach(v -> f.merge(v.getFormaPagamento().name(), v.getTotal(), BigDecimal::add));
        m.mensalidades().forEach(p -> f.merge(p.getFormaPagamento().name(), p.getValor(), BigDecimal::add));
        f.replaceAll((k, v) -> Textos.dinheiro(v));
        return f;
    }

    private static Map<String, BigDecimal> despesasPorCategoria(List<Despesa> ds) {
        Map<String, BigDecimal> c = new TreeMap<>();
        ds.forEach(d -> c.merge(d.getCategoria().name(), d.getValor(), BigDecimal::add));
        return c;
    }

    static List<Map<String, Object>> serieDiaria(LocalDate de, LocalDate ate, Movimentos m) {
        Map<LocalDate, BigDecimal[]> dias = new TreeMap<>();
        for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
            dias.put(d, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            if (dias.size() > 400) break;
        }
        m.atendimentos().forEach(a -> acumula(dias, a.getPagoEm().toLocalDate(), 0, a.getValorFinal()));
        m.vendas().forEach(v -> acumula(dias, v.getDataHora().toLocalDate(), 1, v.getTotal()));
        m.mensalidades().forEach(p -> acumula(dias, p.getDataHora().toLocalDate(), 2, p.getValor()));
        List<Map<String, Object>> serie = new ArrayList<>();
        dias.forEach((d, v) -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("data", d);
            p.put("servicos", Textos.dinheiro(v[0]));
            p.put("produtos", Textos.dinheiro(v[1]));
            p.put("clube", Textos.dinheiro(v[2]));
            p.put("total", Textos.dinheiro(v[0].add(v[1]).add(v[2])));
            serie.add(p);
        });
        return serie;
    }

    private static void acumula(Map<LocalDate, BigDecimal[]> dias, LocalDate d, int i, BigDecimal v) {
        BigDecimal[] x = dias.get(d);
        if (x != null) x[i] = x[i].add(Textos.zeroSeNulo(v));
    }

    private List<Map<String, Object>> porUnidade(Movimentos m) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Unidade u : unidades.findAllByOrderByNome()) {
            Long id = u.getId();
            BigDecimal serv = soma(m.atendimentos().stream().filter(a -> a.getUnidade().getId().equals(id)).map(Agendamento::getValorFinal));
            BigDecimal prod = soma(m.vendas().stream().filter(v -> v.getUnidade().getId().equals(id)).map(Venda::getTotal));
            BigDecimal clube = soma(m.mensalidades().stream().filter(p -> p.getUnidade().getId().equals(id)).map(PagamentoAssinatura::getValor));
            BigDecimal desp = soma(m.despesas().stream().filter(d -> d.getUnidade().getId().equals(id)).map(Despesa::getValor));
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("unidadeId", id);
            l.put("unidadeNome", u.getNome());
            l.put("receita", Textos.dinheiro(serv.add(prod).add(clube)));
            l.put("despesas", Textos.dinheiro(desp));
            l.put("atendimentos", m.atendimentos().stream().filter(a -> a.getUnidade().getId().equals(id)).count());
            out.add(l);
        }
        return out;
    }
}
