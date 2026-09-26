package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.OrigemAgendamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Agendamento feito pela equipe. Informe clienteId OU (clienteNome + clienteTelefone). */
public record AgendamentoRequestDTO(
        @NotNull(message = "é obrigatório") Long barbeiroId,
        @NotNull(message = "é obrigatório") Long servicoId,
        Long clienteId,
        String clienteNome,
        String clienteTelefone,
        String clienteEmail,
        @NotNull(message = "é obrigatório") LocalDateTime inicio,
        OrigemAgendamento origem,
        @Size(max = 1000) String observacao,
        String cupom) {
}
