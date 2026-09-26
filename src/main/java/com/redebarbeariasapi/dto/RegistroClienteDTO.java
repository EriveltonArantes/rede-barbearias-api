package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** Auto-cadastro no app: sempre cria conta de CLIENTE (nunca admin/equipe). */
public record RegistroClienteDTO(
        @NotBlank(message = "é obrigatório") @Pattern(regexp = "^[A-Za-z0-9._@-]{3,60}$", message = "3 a 60 caracteres, sem espaço") String username,
        @NotBlank(message = "é obrigatório") @Size(min = 6, max = 100, message = "mínimo 6 caracteres") String password,
        @NotBlank(message = "é obrigatório") @Size(min = 2, max = 120) String nome,
        @NotBlank(message = "é obrigatório") String telefone,
        @Email(message = "inválido") String email,
        @Past(message = "precisa ser no passado") LocalDate dataNascimento) {
}
