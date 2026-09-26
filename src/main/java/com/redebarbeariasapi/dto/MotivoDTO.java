package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.NotBlank;

public record MotivoDTO(@NotBlank(message = "informe o motivo") String motivo) {
}
