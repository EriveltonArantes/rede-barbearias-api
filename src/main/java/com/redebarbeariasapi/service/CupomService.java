package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.CupomRequestDTO;
import com.redebarbeariasapi.dto.CupomResponseDTO;
import com.redebarbeariasapi.dto.CupomValidacaoDTO;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.mapper.OperacaoMapper;
import com.redebarbeariasapi.model.Cupom;
import com.redebarbeariasapi.repository.CupomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class CupomService {

    private final CupomRepository repo;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<CupomResponseDTO> listar() {
        return repo.findAllByOrderByCodigo().stream().map(OperacaoMapper::cupom).toList();
    }

    public CupomResponseDTO criar(CupomRequestDTO dto) {
        String codigo = dto.codigo().trim().toUpperCase();
        repo.findByCodigoIgnoreCase(codigo).ifPresent(c -> {
            throw new BusinessException("Já existe um cupom com o código " + codigo + ".");
        });
        Cupom c = new Cupom();
        aplicar(dto, c, codigo);
        repo.save(c);
        auditoria.registrar("CRIAR", "Cupom", c.getId(), codigo);
        return OperacaoMapper.cupom(c);
    }

    public CupomResponseDTO atualizar(Long id, CupomRequestDTO dto) {
        Cupom c = repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Cupom", id));
        String codigo = dto.codigo().trim().toUpperCase();
        repo.findByCodigoIgnoreCase(codigo).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Já existe um cupom com o código " + codigo + ".");
        });
        aplicar(dto, c, codigo);
        auditoria.registrar("EDITAR", "Cupom", id, codigo);
        return OperacaoMapper.cupom(c);
    }

    public void excluir(Long id) {
        Cupom c = repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Cupom", id));
        repo.delete(c);
        auditoria.registrar("EXCLUIR", "Cupom", id, c.getCodigo());
    }

    @Transactional(readOnly = true)
    public CupomValidacaoDTO validar(String codigo, BigDecimal valor) {
        BigDecimal base = valor == null ? BigDecimal.ZERO : valor;
        return repo.findByCodigoIgnoreCase(codigo == null ? "" : codigo.trim())
                .map(c -> {
                    if (!c.valido(LocalDate.now())) {
                        return new CupomValidacaoDTO(c.getCodigo(), false, "Cupom expirado ou esgotado.", BigDecimal.ZERO, base);
                    }
                    BigDecimal d = c.descontoPara(base);
                    String msg = c.getPercentual() != null && c.getPercentual().signum() > 0
                            ? c.getPercentual().stripTrailingZeros().toPlainString() + "% de desconto"
                            : "R$ " + d + " de desconto";
                    return new CupomValidacaoDTO(c.getCodigo(), true, msg, d, base.subtract(d));
                })
                .orElse(new CupomValidacaoDTO(codigo, false, "Cupom não encontrado.", BigDecimal.ZERO, base));
    }

    private void aplicar(CupomRequestDTO dto, Cupom c, String codigo) {
        boolean temPct = dto.percentual() != null && dto.percentual().signum() > 0;
        boolean temValor = dto.valorFixo() != null && dto.valorFixo().signum() > 0;
        if (temPct == temValor) throw new ValidacaoException("Informe OU um percentual OU um valor fixo de desconto.");
        c.setCodigo(codigo);
        c.setDescricao(dto.descricao());
        c.setPercentual(temPct ? dto.percentual() : null);
        c.setValorFixo(temValor ? dto.valorFixo() : null);
        c.setValidoAte(dto.validoAte());
        c.setLimiteUsos(dto.limiteUsos());
        if (dto.ativo() != null) c.setAtivo(dto.ativo());
    }
}
