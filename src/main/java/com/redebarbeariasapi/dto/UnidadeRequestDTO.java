package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.*;

import java.time.LocalTime;

public record UnidadeRequestDTO(
        @NotBlank(message = "é obrigatório") @Size(max = 120) String nome,
        @NotBlank(message = "é obrigatório") String endereco,
        String bairro,
        String cidade,
        String telefone,
        String whatsapp,
        @Email(message = "inválido") String email,
        String fotoUrl,
        @NotNull(message = "é obrigatório") LocalTime horaAbertura,
        @NotNull(message = "é obrigatório") LocalTime horaFechamento,
        @NotBlank(message = "é obrigatório")
        @Pattern(regexp = "^[1-7](,[1-7])*$", message = "use números de 1 (segunda) a 7 (domingo) separados por vírgula")
        String diasFuncionamento,
        String chavePix,
        Boolean ativa) {
}
