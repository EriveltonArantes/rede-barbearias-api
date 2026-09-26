package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.Papel;

import java.time.LocalDateTime;

public record UsuarioResponseDTO(
        Long id, String username, String nome, Papel papel,
        Long unidadeId, String unidadeNome, Long barbeiroId, String barbeiroNome,
        Long clienteId, String clienteNome, boolean ativo,
        LocalDateTime ultimoLogin, LocalDateTime criadoEm) {
}
