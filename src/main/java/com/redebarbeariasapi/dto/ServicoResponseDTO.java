package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.CategoriaServico;

import java.math.BigDecimal;

public record ServicoResponseDTO(
        Long id, String nome, String descricao, CategoriaServico categoria,
        BigDecimal preco, Integer duracaoMinutos, boolean ativo) {
}
