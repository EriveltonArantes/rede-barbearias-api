package com.redebarbeariasapi.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClienteResponseDTO(
        Long id, String nome, String telefone, String email, LocalDate dataNascimento,
        String observacoes, Long unidadePreferidaId, String unidadePreferidaNome,
        Long barbeiroPreferidoId, String barbeiroPreferidoNome,
        int pontos, boolean aceitaMarketing, LocalDateTime criadoEm, LocalDateTime ultimaVisita) {
}
