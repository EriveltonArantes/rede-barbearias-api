package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.Papel;

public record LoginResponseDTO(
        String token, String username, String nome, Papel papel, String role,
        Long unidadeId, String unidadeNome, Long barbeiroId, Long clienteId) {
}
