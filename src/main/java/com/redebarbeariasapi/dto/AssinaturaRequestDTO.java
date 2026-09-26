package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.FormaPagamento;
import jakarta.validation.constraints.NotNull;

/** Nova assinatura ja registra o pagamento do 1o mes na unidade informada. */
public record AssinaturaRequestDTO(
        @NotNull(message = "é obrigatório") Long clienteId,
        @NotNull(message = "é obrigatório") Long planoId,
        @NotNull(message = "é obrigatório") Long unidadeId,
        @NotNull(message = "é obrigatório") FormaPagamento formaPagamento) {
}
