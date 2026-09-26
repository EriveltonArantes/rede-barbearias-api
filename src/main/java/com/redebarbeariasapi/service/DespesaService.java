package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.DespesaRequestDTO;
import com.redebarbeariasapi.dto.DespesaResponseDTO;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.mapper.OperacaoMapper;
import com.redebarbeariasapi.model.Despesa;
import com.redebarbeariasapi.repository.DespesaRepository;
import com.redebarbeariasapi.security.Sessao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Contas a pagar da unidade (aluguel, luz, produtos, salarios...). */
@Service
@Transactional
@RequiredArgsConstructor
public class DespesaService {

    private final DespesaRepository repo;
    private final UnidadeService unidades;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<DespesaResponseDTO> listar(LocalDate de, LocalDate ate, Long unidadeId) {
        return repo.periodo(de, ate, Sessao.unidadeEscopo(unidadeId)).stream().map(OperacaoMapper::despesa).toList();
    }

    private Despesa obter(Long id) {
        Despesa d = repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Despesa", id));
        Sessao.exigirUnidade(d.getUnidade().getId());
        return d;
    }

    public DespesaResponseDTO criar(DespesaRequestDTO dto) {
        Sessao.exigirUnidade(dto.unidadeId());
        Despesa d = new Despesa();
        aplicar(dto, d);
        repo.save(d);
        auditoria.registrar("CRIAR", "Despesa", d.getId(), d.getDescricao() + " R$ " + d.getValor());
        return OperacaoMapper.despesa(d);
    }

    public DespesaResponseDTO atualizar(Long id, DespesaRequestDTO dto) {
        Despesa d = obter(id);
        Sessao.exigirUnidade(dto.unidadeId());
        aplicar(dto, d);
        auditoria.registrar("EDITAR", "Despesa", id, d.getDescricao() + " R$ " + d.getValor());
        return OperacaoMapper.despesa(d);
    }

    public DespesaResponseDTO pagar(Long id) {
        Despesa d = obter(id);
        d.setPaga(true);
        d.setPagaEm(LocalDate.now());
        auditoria.registrar("PAGAR", "Despesa", id, d.getDescricao() + " R$ " + d.getValor());
        return OperacaoMapper.despesa(d);
    }

    public void excluir(Long id) {
        Despesa d = obter(id);
        repo.delete(d);
        auditoria.registrar("EXCLUIR", "Despesa", id, d.getDescricao() + " R$ " + d.getValor());
    }

    private void aplicar(DespesaRequestDTO dto, Despesa d) {
        d.setUnidade(unidades.obter(dto.unidadeId()));
        d.setDescricao(dto.descricao().trim());
        d.setCategoria(dto.categoria());
        d.setValor(dto.valor());
        d.setVencimento(dto.vencimento());
        d.setFornecedor(dto.fornecedor());
        d.setComprovanteUrl(dto.comprovanteUrl());
        boolean paga = Boolean.TRUE.equals(dto.paga());
        if (paga && !d.isPaga()) d.setPagaEm(LocalDate.now());
        if (!paga) d.setPagaEm(null);
        d.setPaga(paga);
    }
}
