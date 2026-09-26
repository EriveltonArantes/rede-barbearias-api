package com.redebarbeariasapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CupomResponseDTO(
        Long id, String codigo, String descricao, BigDecimal percentual, BigDecimal valorFixo,
        LocalDate validoAte, Integer limiteUsos, int usos, boolean ativo, boolean valido) {
}
