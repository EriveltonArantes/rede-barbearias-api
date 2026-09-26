package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.StatusAgendamento;
import jakarta.validation.constraints.NotNull;

public record StatusRequestDTO(@NotNull(message = "é obrigatório") StatusAgendamento status, String motivo) {
}
