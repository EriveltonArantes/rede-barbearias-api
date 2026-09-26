package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProdutoRequestDTO(
        @NotNull(message = "é obrigatório") Long unidadeId,
        @NotBlank(message = "é obrigatório") @Size(max = 120) String nome,
        String marca,
        String categoria,
        String codigoBarras,
        String fotoUrl,
        @NotNull(message = "é obrigatório") @DecimalMin(value = "0", message = "não pode ser negativo") BigDecimal precoCusto,
        @NotNull(message = "é obrigatório") @DecimalMin(value = "0.01", message = "precisa ser maior que zero") BigDecimal precoVenda,
        @Min(value = 0, message = "não pode ser negativo") Integer estoqueInicial,
        @Min(value = 0, message = "não pode ser negativo") Integer estoqueMinimo,
        Boolean ativo) {
}
