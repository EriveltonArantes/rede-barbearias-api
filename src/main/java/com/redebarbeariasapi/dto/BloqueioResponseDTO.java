package com.redebarbeariasapi.dto;

import java.time.LocalDateTime;

public record BloqueioResponseDTO(
        Long id, Long unidadeId, String unidadeNome, Long barbeiroId, String barbeiroNome,
        LocalDateTime inicio, LocalDateTime fim, String motivo) {
}
