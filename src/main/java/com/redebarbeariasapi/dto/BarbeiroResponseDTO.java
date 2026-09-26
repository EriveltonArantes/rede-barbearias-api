package com.redebarbeariasapi.dto;

import java.math.BigDecimal;

public record BarbeiroResponseDTO(
        Long id, Long unidadeId, String unidadeNome, String nome, String apelido,
        String telefone, String email, String especialidades, String bio, String fotoUrl,
        BigDecimal comissaoServico, BigDecimal comissaoProduto, String diasTrabalho, boolean ativo,
        Double notaMedia, long totalAvaliacoes) {
}
