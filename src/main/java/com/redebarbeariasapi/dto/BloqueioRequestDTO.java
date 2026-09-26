package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record BloqueioRequestDTO(
        @NotNull(message = "é obrigatório") Long unidadeId,
        Long barbeiroId,
        @NotNull(message = "é obrigatório") LocalDateTime inicio,
        @NotNull(message = "é obrigatório") LocalDateTime fim,
        @NotBlank(message = "é obrigatório") String motivo) {
}
