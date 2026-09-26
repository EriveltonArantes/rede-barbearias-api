package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.FormaPagamento;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Fecha o atendimento e registra o pagamento.
 * usarAssinatura: consome 1 uso do clube. usarFidelidade: troca pontos por atendimento gratis.
 */
public record FinalizarRequestDTO(
        FormaPagamento formaPagamento,
        @DecimalMin(value = "0", message = "não pode ser negativo") BigDecimal descontoExtra,
        boolean usarFidelidade,
        boolean usarAssinatura) {
}
