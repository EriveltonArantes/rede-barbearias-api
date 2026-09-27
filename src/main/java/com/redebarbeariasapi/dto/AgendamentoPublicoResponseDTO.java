package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.SituacaoSinal;
import com.redebarbeariasapi.model.StatusAgendamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** O que o cliente ve do proprio agendamento pelo codigo (sem dados de outras pessoas). */
public record AgendamentoPublicoResponseDTO(
        String codigo, StatusAgendamento status, String clientePrimeiroNome,
        String unidadeNome, String unidadeEndereco, String unidadeWhatsapp,
        String barbeiroNome, String servicoNome, Integer duracaoMinutos,
        LocalDateTime inicio, LocalDateTime fim,
        BigDecimal valor, BigDecimal desconto, BigDecimal valorAPagar,
        boolean podeCancelar, boolean podeAvaliar, Integer nota,
        BigDecimal sinalValor, SituacaoSinal sinalSituacao, LocalDateTime sinalExpiraEm) {
}
