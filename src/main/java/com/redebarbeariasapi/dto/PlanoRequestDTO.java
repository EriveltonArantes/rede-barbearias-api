package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Set;

public record PlanoRequestDTO(
        @NotBlank(message = "é obrigatório") String nome,
        @Size(max = 800) String descricao,
        @NotNull(message = "é obrigatório") @DecimalMin(value = "0.01", message = "precisa ser maior que zero") BigDecimal precoMensal,
        @NotNull(message = "é obrigatório") @Min(value = 1, message = "mínimo 1") @Max(value = 60, message = "máximo 60") Integer usosPorMes,
        @NotEmpty(message = "escolha pelo menos 1 serviço") Set<Long> servicoIds,
        Boolean ativo) {
}
