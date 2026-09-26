package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.FormaPagamento;
import jakarta.validation.constraints.NotNull;

public record RenovacaoRequestDTO(
        @NotNull(message = "é obrigatório") Long unidadeId,
        @NotNull(message = "é obrigatório") FormaPagamento formaPagamento) {
}
