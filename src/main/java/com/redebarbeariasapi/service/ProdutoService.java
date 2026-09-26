package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.MovimentoEstoqueRequestDTO;
import com.redebarbeariasapi.dto.MovimentoEstoqueResponseDTO;
import com.redebarbeariasapi.dto.ProdutoRequestDTO;
import com.redebarbeariasapi.dto.ProdutoResponseDTO;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.mapper.OperacaoMapper;
import com.redebarbeariasapi.model.MovimentoEstoque;
import com.redebarbeariasapi.model.Produto;
import com.redebarbeariasapi.model.TipoMovimento;
import com.redebarbeariasapi.repository.MovimentoEstoqueRepository;
import com.redebarbeariasapi.repository.ProdutoRepository;
import com.redebarbeariasapi.repository.VendaRepository;
import com.redebarbeariasapi.security.Sessao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository repo;
    private final MovimentoEstoqueRepository movimentos;
    private final VendaRepository vendas;
    private final UnidadeService unidades;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<ProdutoResponseDTO> listar(Long unidadeId, boolean apenasEstoqueBaixo) {
        Long un = Sessao.unidadeEscopo(unidadeId);
        List<Produto> lista = un == null ? repo.findAllByOrderByNome() : repo.findByUnidadeIdOrderByNome(un);
        return lista.stream()
                .filter(p -> !apenasEstoqueBaixo || (p.isAtivo() && p.estoqueBaixo()))
                .map(OperacaoMapper::produto).toList();
    }

    public Produto obter(Long id) {
        Produto p = repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Produto", id));
        Sessao.exigirUnidade(p.getUnidade().getId());
        return p;
    }

    public ProdutoResponseDTO criar(ProdutoRequestDTO dto) {
        Sessao.exigirUnidade(dto.unidadeId());
        Produto p = new Produto();
        p.setUnidade(unidades.obter(dto.unidadeId()));
        aplicar(dto, p);
        p.setEstoque(0);
        repo.save(p);
        int inicial = dto.estoqueInicial() == null ? 0 : dto.estoqueInicial();
        if (inicial > 0) registrarMovimento(p, TipoMovimento.ENTRADA, inicial, p.getPrecoCusto(), "Estoque inicial");
        auditoria.registrar("CRIAR", "Produto", p.getId(), p.getNome());
        return OperacaoMapper.produto(p);
    }

    public ProdutoResponseDTO atualizar(Long id, ProdutoRequestDTO dto) {
        Produto p = obter(id);
        Sessao.exigirUnidade(dto.unidadeId());
        if (!p.getUnidade().getId().equals(dto.unidadeId())) {
            throw new ValidacaoException("Não dá pra mudar a unidade de um produto (o estoque é físico). Cadastre-o na outra unidade.");
        }
        String antes = "R$ " + p.getPrecoVenda();
        aplicar(dto, p);
        auditoria.registrar("EDITAR", "Produto", id, p.getNome() + " " + antes + " -> R$ " + p.getPrecoVenda());
        return OperacaoMapper.produto(p);
    }

    public void excluir(Long id) {
        Produto p = obter(id);
        if (vendas.itensDoProduto(id) > 0) {
            throw new BusinessException("Produto já vendido não pode ser excluído. Desative-o em vez disso.");
        }
        movimentos.deleteByProdutoId(id);
        repo.delete(p);
        auditoria.registrar("EXCLUIR", "Produto", id, p.getNome());
    }

    /** Entrada de compra, ajuste de inventario ou uso interno. */
    public ProdutoResponseDTO movimentar(Long id, MovimentoEstoqueRequestDTO dto) {
        Produto p = obter(id);
        int q = dto.quantidade();
        String motivo = Textos.vazio(dto.motivo()) ? null : dto.motivo().trim();
        switch (dto.tipo()) {
            case ENTRADA -> {
                if (q <= 0) throw new ValidacaoException("Quantidade de entrada precisa ser positiva.");
                if (dto.custoUnitario() != null && dto.custoUnitario().signum() > 0) p.setPrecoCusto(dto.custoUnitario());
                registrarMovimento(p, TipoMovimento.ENTRADA, q, dto.custoUnitario(), motivo == null ? "Compra" : motivo);
            }
            case AJUSTE -> {
                if (q < 0) throw new ValidacaoException("O estoque contado não pode ser negativo.");
                if (motivo == null) throw new ValidacaoException("Informe o motivo do ajuste (ex.: inventário, quebra, vencido).");
                int dif = q - p.getEstoque();
                if (dif == 0) return OperacaoMapper.produto(p);
                registrarMovimento(p, TipoMovimento.AJUSTE, dif, null, motivo);
            }
            case USO_INTERNO -> {
                if (q <= 0) throw new ValidacaoException("Quantidade precisa ser positiva.");
                if (p.getEstoque() < q) throw new BusinessException("Estoque insuficiente: há " + p.getEstoque() + " unidade(s).");
                registrarMovimento(p, TipoMovimento.USO_INTERNO, -q, null, motivo == null ? "Uso no salão" : motivo);
            }
            default -> throw new ValidacaoException("Saída por venda e estorno são automáticos (feitos pela tela de vendas).");
        }
        return OperacaoMapper.produto(p);
    }

    public void registrarMovimento(Produto p, TipoMovimento tipo, int quantidade, BigDecimal custo, String motivo) {
        p.setEstoque(p.getEstoque() + quantidade);
        MovimentoEstoque m = new MovimentoEstoque();
        m.setProduto(p);
        m.setTipo(tipo);
        m.setQuantidade(quantidade);
        m.setCustoUnitario(custo);
        m.setMotivo(motivo);
        m.setUsuario(Sessao.username());
        movimentos.save(m);
    }

    @Transactional(readOnly = true)
    public List<MovimentoEstoqueResponseDTO> historico(Long id) {
        obter(id);
        return movimentos.findTop100ByProdutoIdOrderByDataHoraDesc(id).stream().map(OperacaoMapper::movimento).toList();
    }

    private void aplicar(ProdutoRequestDTO d, Produto p) {
        p.setNome(d.nome().trim());
        p.setMarca(d.marca());
        p.setCategoria(d.categoria());
        p.setCodigoBarras(Textos.vazio(d.codigoBarras()) ? null : d.codigoBarras().trim());
        p.setFotoUrl(d.fotoUrl());
        p.setPrecoCusto(d.precoCusto());
        p.setPrecoVenda(d.precoVenda());
        if (d.estoqueMinimo() != null) p.setEstoqueMinimo(d.estoqueMinimo());
        if (d.ativo() != null) p.setAtivo(d.ativo());
    }
}
