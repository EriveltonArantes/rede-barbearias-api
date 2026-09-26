package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.CategoriaDespesa;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DespesaRequestDTO(
        @NotNull(message = "é obrigatório") Long unidadeId,
        @NotBlank(message = "é obrigatório") @Size(max = 200) String descricao,
        @NotNull(message = "é obrigatório") CategoriaDespesa categoria,
        @NotNull(message = "é obrigatório") @DecimalMin(value = "0.01", message = "precisa ser maior que zero") BigDecimal valor,
        @NotNull(message = "é obrigatório") LocalDate vencimento,
        Boolean paga,
        String fornecedor,
        String comprovanteUrl) {
}
