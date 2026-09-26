package com.redebarbeariasapi.dto;

import java.time.LocalDateTime;

public record AvaliacaoResponseDTO(
        Long id, Long agendamentoId, Integer nota, String comentario, boolean publica, String resposta,
        LocalDateTime criadaEm, String clienteNome, Long barbeiroId, String barbeiroNome,
        String servicoNome, Long unidadeId, String unidadeNome) {
}
