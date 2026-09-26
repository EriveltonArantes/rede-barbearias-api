package com.redebarbeariasapi.mapper;

import com.redebarbeariasapi.dto.*;
import com.redebarbeariasapi.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;

/** Mapeamentos das partes operacionais: estoque, vendas, despesas, clube, cupons, avaliacoes, usuarios. */
public final class OperacaoMapper {
    private OperacaoMapper() {}

    public static BloqueioResponseDTO bloqueio(BloqueioAgenda b) {
        return new BloqueioResponseDTO(b.getId(), b.getUnidade().getId(), b.getUnidade().getNome(),
                b.getBarbeiro() == null ? null : b.getBarbeiro().getId(),
                b.getBarbeiro() == null ? null : b.getBarbeiro().getNome(),
                b.getInicio(), b.getFim(), b.getMotivo());
    }

    public static ProdutoResponseDTO produto(Produto p) {
        BigDecimal margem = null;
        if (p.getPrecoCusto() != null && p.getPrecoCusto().signum() > 0 && p.getPrecoVenda() != null) {
            margem = p.getPrecoVenda().subtract(p.getPrecoCusto())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(p.getPrecoVenda(), 1, RoundingMode.HALF_UP);
        }
        return new ProdutoResponseDTO(p.getId(), p.getUnidade().getId(), p.getUnidade().getNome(), p.getNome(),
                p.getMarca(), p.getCategoria(), p.getCodigoBarras(), p.getFotoUrl(), p.getPrecoCusto(),
                p.getPrecoVenda(), margem, p.getEstoque(), p.getEstoqueMinimo(), p.estoqueBaixo(), p.isAtivo());
    }

    public static MovimentoEstoqueResponseDTO movimento(MovimentoEstoque m) {
        return new MovimentoEstoqueResponseDTO(m.getId(), m.getTipo(), m.getQuantidade(), m.getCustoUnitario(),
                m.getMotivo(), m.getUsuario(), m.getDataHora());
    }

    public static VendaResponseDTO venda(Venda v) {
        return new VendaResponseDTO(v.getId(), v.getUnidade().getId(), v.getUnidade().getNome(),
                v.getCliente() == null ? null : v.getCliente().getId(),
                v.getCliente() == null ? null : v.getCliente().getNome(),
                v.getBarbeiro() == null ? null : v.getBarbeiro().getId(),
                v.getBarbeiro() == null ? null : v.getBarbeiro().getNome(),
                v.getItens().stream()
                        .sorted(Comparator.comparing(VendaItem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(i -> new VendaResponseDTO.Item(i.getProduto().getId(), i.getProduto().getNome(),
                                i.getQuantidade(), i.getPrecoUnitario(), i.getTotal()))
                        .toList(),
                v.getSubtotal(), v.getDesconto(), v.getTotal(), v.getComissaoValor(), v.getFormaPagamento(),
                v.isCancelada(), v.getMotivoCancelamento(), v.getUsuario(), v.getDataHora());
    }

    public static DespesaResponseDTO despesa(Despesa d) {
        boolean vencida = !d.isPaga() && d.getVencimento().isBefore(LocalDate.now());
        return new DespesaResponseDTO(d.getId(), d.getUnidade().getId(), d.getUnidade().getNome(), d.getDescricao(),
                d.getCategoria(), d.getValor(), d.getVencimento(), d.isPaga(), d.getPagaEm(), d.getFornecedor(),
                d.getComprovanteUrl(), vencida);
    }

    public static PlanoResponseDTO plano(Plano p, long assinantes) {
        return new PlanoResponseDTO(p.getId(), p.getNome(), p.getDescricao(), p.getPrecoMensal(), p.getUsosPorMes(),
                p.getServicos().stream()
                        .sorted(Comparator.comparing(Servico::getNome))
                        .map(s -> new PlanoResponseDTO.ServicoResumo(s.getId(), s.getNome(), s.getPreco()))
                        .toList(),
                p.isAtivo(), assinantes);
    }

    public static AssinaturaResponseDTO assinatura(Assinatura a) {
        if (a == null) return null;
        int restantes = Math.max(0, a.getPlano().getUsosPorMes() - a.getUsosNoCiclo());
        boolean vencida = a.getStatus() == StatusAssinatura.ATIVA && LocalDate.now().isAfter(a.getValidaAte());
        return new AssinaturaResponseDTO(a.getId(), a.getCliente().getId(), a.getCliente().getNome(),
                a.getCliente().getTelefone(), a.getPlano().getId(), a.getPlano().getNome(),
                a.getPlano().getPrecoMensal(), a.getPlano().getUsosPorMes(), a.getUsosNoCiclo(), restantes,
                a.getStatus(), a.getInicio(), a.getCicloInicio(), a.getValidaAte(), vencida, a.getCanceladaEm());
    }

    public static CupomResponseDTO cupom(Cupom c) {
        return new CupomResponseDTO(c.getId(), c.getCodigo(), c.getDescricao(), c.getPercentual(), c.getValorFixo(),
                c.getValidoAte(), c.getLimiteUsos(), c.getUsos(), c.isAtivo(), c.valido(LocalDate.now()));
    }

    public static AvaliacaoResponseDTO avaliacao(Avaliacao av) {
        Agendamento a = av.getAgendamento();
        return new AvaliacaoResponseDTO(av.getId(), a.getId(), av.getNota(), av.getComentario(), av.isPublica(),
                av.getResposta(), av.getCriadaEm(), ClienteMapper.primeiroNome(a.getCliente().getNome()),
                a.getBarbeiro().getId(), a.getBarbeiro().getNome(), a.getServico().getNome(),
                a.getUnidade().getId(), a.getUnidade().getNome());
    }

    public static UsuarioResponseDTO usuario(Usuario u) {
        Long unidadeId = u.getUnidade() != null ? u.getUnidade().getId()
                : u.getBarbeiro() != null ? u.getBarbeiro().getUnidade().getId() : null;
        String unidadeNome = u.getUnidade() != null ? u.getUnidade().getNome()
                : u.getBarbeiro() != null ? u.getBarbeiro().getUnidade().getNome() : null;
        return new UsuarioResponseDTO(u.getId(), u.getUsername(), u.getNome(), u.getPapel(), unidadeId, unidadeNome,
                u.getBarbeiro() == null ? null : u.getBarbeiro().getId(),
                u.getBarbeiro() == null ? null : u.getBarbeiro().getNome(),
                u.getCliente() == null ? null : u.getCliente().getId(),
                u.getCliente() == null ? null : u.getCliente().getNome(),
                u.isAtivo(), u.getUltimoLogin(), u.getCriadoEm());
    }
}
