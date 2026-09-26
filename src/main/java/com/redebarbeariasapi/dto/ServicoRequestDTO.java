package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.CategoriaServico;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ServicoRequestDTO(
        @NotBlank(message = "é obrigatório") @Size(max = 120) String nome,
        @Size(max = 600) String descricao,
        @NotNull(message = "é obrigatório") CategoriaServico categoria,
        @NotNull(message = "é obrigatório") @DecimalMin(value = "0", message = "não pode ser negativo") BigDecimal preco,
        @NotNull(message = "é obrigatório") @Min(value = 5, message = "mínimo 5 minutos") @Max(value = 480, message = "máximo 8 horas") Integer duracaoMinutos,
        Boolean ativo) {
}
