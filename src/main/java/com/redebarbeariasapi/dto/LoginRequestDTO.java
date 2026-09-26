package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(@NotBlank(message = "é obrigatório") String username,
                              @NotBlank(message = "é obrigatório") String password) {
}
