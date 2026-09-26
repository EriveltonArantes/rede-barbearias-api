package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.FormaPagamento;
import com.redebarbeariasapi.model.OrigemAgendamento;
import com.redebarbeariasapi.model.StatusAgendamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgendamentoResponseDTO(
        Long id, String codigo,
        Long unidadeId, String unidadeNome,
        Long barbeiroId, String barbeiroNome,
        Long clienteId, String clienteNome, String clienteTelefone,
        Long servicoId, String servicoNome, Integer duracaoMinutos,
        LocalDateTime inicio, LocalDateTime fim,
        StatusAgendamento status, OrigemAgendamento origem,
        BigDecimal valor, BigDecimal desconto, BigDecimal valorAPagar, BigDecimal valorFinal,
        String cupomCodigo, FormaPagamento formaPagamento, boolean pago, LocalDateTime pagoEm,
        BigDecimal comissaoValor, String observacao, String motivoCancelamento,
        boolean lembreteEnviado, Integer nota, LocalDateTime criadoEm) {
}
