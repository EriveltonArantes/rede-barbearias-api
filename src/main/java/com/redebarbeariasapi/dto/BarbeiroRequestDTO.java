package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BarbeiroRequestDTO(
        @NotNull(message = "é obrigatório") Long unidadeId,
        @NotBlank(message = "é obrigatório") @Size(max = 120) String nome,
        String apelido,
        String telefone,
        @Email(message = "inválido") String email,
        String especialidades,
        @Size(max = 1000) String bio,
        String fotoUrl,
        @DecimalMin(value = "0", message = "mínimo 0") @DecimalMax(value = "100", message = "máximo 100") BigDecimal comissaoServico,
        @DecimalMin(value = "0", message = "mínimo 0") @DecimalMax(value = "100", message = "máximo 100") BigDecimal comissaoProduto,
        @Pattern(regexp = "^$|^[1-7](,[1-7])*$", message = "use números de 1 (segunda) a 7 (domingo) separados por vírgula")
        String diasTrabalho,
        Boolean ativo) {
}
