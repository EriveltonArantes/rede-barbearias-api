package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(
        @NotNull(message = "é obrigatório") Long unidadeId,
        Long clienteId,
        Long barbeiroId,
        @NotEmpty(message = "adicione pelo menos 1 produto") @Valid List<Item> itens,
        @DecimalMin(value = "0", message = "não pode ser negativo") BigDecimal desconto,
        @NotNull(message = "é obrigatório") FormaPagamento formaPagamento) {

    public record Item(
            @NotNull(message = "é obrigatório") Long produtoId,
            @NotNull(message = "é obrigatório") @Min(value = 1, message = "mínimo 1") Integer quantidade) {}
}
