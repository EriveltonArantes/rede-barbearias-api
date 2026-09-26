package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record ClienteRequestDTO(
        @NotBlank(message = "é obrigatório") @Size(min = 2, max = 120) String nome,
        @NotBlank(message = "é obrigatório") String telefone,
        @Email(message = "inválido") String email,
        @Past(message = "precisa ser no passado") LocalDate dataNascimento,
        @Size(max = 1000) String observacoes,
        Long unidadePreferidaId,
        Long barbeiroPreferidoId,
        Boolean aceitaMarketing) {
}
