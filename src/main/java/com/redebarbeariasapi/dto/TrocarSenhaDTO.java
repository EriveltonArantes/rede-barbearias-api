package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocarSenhaDTO(@NotBlank(message = "é obrigatório") String senhaAtual,
                             @NotBlank(message = "é obrigatório") @Size(min = 6, message = "mínimo 6 caracteres") String novaSenha) {
}
