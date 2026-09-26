package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.CategoriaDespesa;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DespesaResponseDTO(
        Long id, Long unidadeId, String unidadeNome, String descricao, CategoriaDespesa categoria,
        BigDecimal valor, LocalDate vencimento, boolean paga, LocalDate pagaEm,
        String fornecedor, String comprovanteUrl, boolean vencida) {
}
