package com.redebarbeariasapi.dto;

import java.math.BigDecimal;

public record ProdutoResponseDTO(
        Long id, Long unidadeId, String unidadeNome, String nome, String marca, String categoria,
        String codigoBarras, String fotoUrl, BigDecimal precoCusto, BigDecimal precoVenda,
        BigDecimal margemPercentual, Integer estoque, Integer estoqueMinimo, boolean estoqueBaixo, boolean ativo) {
}
