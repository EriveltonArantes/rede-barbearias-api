package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.NotBlank;

/** Pra cancelar sem login o cliente confirma o telefone usado no agendamento. */
public record CancelarPublicoDTO(@NotBlank(message = "é obrigatório") String telefone, String motivo) {
}
