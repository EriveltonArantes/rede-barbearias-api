package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/** Agendamento online feito pelo proprio cliente, sem login. barbeiroId vazio = "sem preferencia". */
public record AgendamentoPublicoRequestDTO(
        @NotNull(message = "é obrigatório") Long unidadeId,
        @NotNull(message = "é obrigatório") Long servicoId,
        Long barbeiroId,
        @NotNull(message = "é obrigatório") @Future(message = "precisa ser no futuro") LocalDateTime inicio,
        @NotBlank(message = "é obrigatório") @Size(min = 3, max = 120, message = "entre 3 e 120 letras") String nome,
        @NotBlank(message = "é obrigatório") String telefone,
        @Email(message = "inválido") String email,
        String cupom,
        @Size(max = 500) String observacao,
        /** Caixinha "quero receber promocoes" (LGPD): null quando o formulario nao perguntou. */
        Boolean aceitaMarketing) {
}
