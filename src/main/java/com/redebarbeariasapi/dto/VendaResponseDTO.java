package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VendaResponseDTO(
        Long id, Long unidadeId, String unidadeNome, Long clienteId, String clienteNome,
        Long barbeiroId, String barbeiroNome, List<Item> itens,
        BigDecimal subtotal, BigDecimal desconto, BigDecimal total, BigDecimal comissaoValor,
        FormaPagamento formaPagamento, boolean cancelada, String motivoCancelamento,
        String usuario, LocalDateTime dataHora) {

    public record Item(Long produtoId, String produtoNome, Integer quantidade, BigDecimal precoUnitario, BigDecimal total) {}
}
