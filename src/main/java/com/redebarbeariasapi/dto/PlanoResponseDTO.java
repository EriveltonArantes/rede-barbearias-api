package com.redebarbeariasapi.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlanoResponseDTO(
        Long id, String nome, String descricao, BigDecimal precoMensal, Integer usosPorMes,
        List<ServicoResumo> servicos, boolean ativo, long assinantesAtivos) {

    public record ServicoResumo(Long id, String nome, BigDecimal preco) {}
}
