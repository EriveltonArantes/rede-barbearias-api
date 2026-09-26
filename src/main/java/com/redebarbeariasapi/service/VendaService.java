package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.VendaRequestDTO;
import com.redebarbeariasapi.dto.VendaResponseDTO;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.mapper.OperacaoMapper;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.BarbeiroRepository;
import com.redebarbeariasapi.repository.ClienteRepository;
import com.redebarbeariasapi.repository.ProdutoRepository;
import com.redebarbeariasapi.repository.VendaRepository;
import com.redebarbeariasapi.security.Sessao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** PDV de produtos (pomada, oleo de barba, shampoo...) com baixa de estoque e comissao. */
@Service
@Transactional
@RequiredArgsConstructor
public class VendaService {

    private final VendaRepository repo;
    private final ProdutoRepository produtos;
    private final ClienteRepository clientes;
    private final BarbeiroRepository barbeiros;
    private final ProdutoService produtoService;
    private final UnidadeService unidades;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<VendaResponseDTO> listar(LocalDate de, LocalDate ate, Long unidadeId) {
        return repo.periodo(de.atStartOfDay(), ate.plusDays(1).atStartOfDay(), Sessao.unidadeEscopo(unidadeId))
                .stream().map(OperacaoMapper::venda).toList();
    }

    public VendaResponseDTO criar(VendaRequestDTO dto) {
        Sessao.exigirUnidade(dto.unidadeId());
        Venda v = new Venda();
        v.setUnidade(unidades.obter(dto.unidadeId()));
        v.setFormaPagamento(dto.formaPagamento());
        if (dto.formaPagamento() == FormaPagamento.ASSINATURA || dto.formaPagamento() == FormaPagamento.CORTESIA) {
            throw new ValidacaoException("Venda de produto precisa de pagamento real (dinheiro, Pix ou cartão).");
        }
        if (dto.clienteId() != null) {
            v.setCliente(clientes.findById(dto.clienteId()).orElseThrow(() -> ResourceNotFoundException.de("Cliente", dto.clienteId())));
        }
        if (dto.barbeiroId() != null) {
            Barbeiro b = barbeiros.findById(dto.barbeiroId()).orElseThrow(() -> ResourceNotFoundException.de("Barbeiro", dto.barbeiroId()));
            if (!b.getUnidade().getId().equals(dto.unidadeId())) throw new ValidacaoException("O barbeiro não é dessa unidade.");
            v.setBarbeiro(b);
        } else if (Sessao.eh(Papel.BARBEIRO)) {
            v.setBarbeiro(barbeiros.findById(Sessao.atual().barbeiroId()).orElseThrow());
        }

        // agrupa itens repetidos do mesmo produto antes de checar estoque
        Map<Long, Integer> quantidades = new HashMap<>();
        dto.itens().forEach(i -> quantidades.merge(i.produtoId(), i.quantidade(), Integer::sum));

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : quantidades.entrySet()) {
            Produto p = produtos.findById(e.getKey()).orElseThrow(() -> ResourceNotFoundException.de("Produto", e.getKey()));
            if (!p.getUnidade().getId().equals(dto.unidadeId())) throw new ValidacaoException(p.getNome() + " é de outra unidade.");
            if (!p.isAtivo()) throw new BusinessException(p.getNome() + " está desativado.");
            if (p.getEstoque() < e.getValue()) {
                throw new BusinessException("Estoque insuficiente de " + p.getNome() + ": há " + p.getEstoque() + ", pedido " + e.getValue() + ".");
            }
            VendaItem item = new VendaItem();
            item.setVenda(v);
            item.setProduto(p);
            item.setQuantidade(e.getValue());
            item.setPrecoUnitario(p.getPrecoVenda());
            item.setTotal(p.getPrecoVenda().multiply(BigDecimal.valueOf(e.getValue())));
            v.getItens().add(item);
            subtotal = subtotal.add(item.getTotal());
        }
        BigDecimal desconto = Textos.zeroSeNulo(dto.desconto()).min(subtotal);
        v.setSubtotal(Textos.dinheiro(subtotal));
        v.setDesconto(Textos.dinheiro(desconto));
        v.setTotal(Textos.dinheiro(subtotal.subtract(desconto)));
        v.setComissaoValor(v.getBarbeiro() == null ? BigDecimal.ZERO : Textos.percentual(v.getTotal(), v.getBarbeiro().getComissaoProduto()));
        v.setUsuario(Sessao.username());
        repo.save(v);
        for (VendaItem i : v.getItens()) {
            produtoService.registrarMovimento(i.getProduto(), TipoMovimento.SAIDA_VENDA, -i.getQuantidade(), null, "Venda #" + v.getId());
        }
        auditoria.registrar("VENDER", "Venda", v.getId(), "R$ " + v.getTotal() + " " + v.getFormaPagamento());
        return OperacaoMapper.venda(v);
    }

    public VendaResponseDTO cancelar(Long id, String motivo) {
        Venda v = repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Venda", id));
        Sessao.exigirUnidade(v.getUnidade().getId());
        if (v.isCancelada()) throw new BusinessException("Essa venda já foi cancelada.");
        v.setCancelada(true);
        v.setMotivoCancelamento(motivo);
        v.setComissaoValor(BigDecimal.ZERO);
        for (VendaItem i : v.getItens()) {
            produtoService.registrarMovimento(i.getProduto(), TipoMovimento.ESTORNO_VENDA, i.getQuantidade(), null, "Estorno venda #" + id);
        }
        auditoria.registrar("ESTORNAR", "Venda", id, "R$ " + v.getTotal() + " — " + motivo);
        return OperacaoMapper.venda(v);
    }
}
