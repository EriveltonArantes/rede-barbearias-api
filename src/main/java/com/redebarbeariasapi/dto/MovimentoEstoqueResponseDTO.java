package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.TipoMovimento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimentoEstoqueResponseDTO(
        Long id, TipoMovimento tipo, Integer quantidade, BigDecimal custoUnitario,
        String motivo, String usuario, LocalDateTime dataHora) {
}
