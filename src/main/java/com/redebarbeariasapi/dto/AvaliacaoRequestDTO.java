package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.*;

public record AvaliacaoRequestDTO(
        @NotNull(message = "é obrigatório") @Min(value = 1, message = "de 1 a 5") @Max(value = 5, message = "de 1 a 5") Integer nota,
        @Size(max = 1000) String comentario) {
}
