package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CupomRequestDTO(
        @NotBlank(message = "é obrigatório") @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$", message = "3 a 30 letras/números, sem espaço") String codigo,
        String descricao,
        @DecimalMin(value = "0", message = "mínimo 0") @DecimalMax(value = "100", message = "máximo 100") BigDecimal percentual,
        @DecimalMin(value = "0", message = "não pode ser negativo") BigDecimal valorFixo,
        LocalDate validoAte,
        @Min(value = 1, message = "mínimo 1") Integer limiteUsos,
        Boolean ativo) {
}
