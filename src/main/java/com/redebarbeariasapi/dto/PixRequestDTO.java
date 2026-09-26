package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PixRequestDTO(
        @NotNull(message = "é obrigatório") @DecimalMin(value = "0.01", message = "precisa ser maior que zero") BigDecimal valor,
        Long unidadeId,
        @Size(max = 40) String descricao,
        String referencia) {
}
