package com.redebarbeariasapi.dto;

import java.time.LocalTime;

public record UnidadeResponseDTO(
        Long id, String nome, String endereco, String bairro, String cidade,
        String telefone, String whatsapp, String email, String fotoUrl,
        LocalTime horaAbertura, LocalTime horaFechamento, String diasFuncionamento,
        String chavePix, boolean ativa, long totalBarbeiros) {
}
