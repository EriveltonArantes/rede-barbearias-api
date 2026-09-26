package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.TipoMovimento;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * ENTRADA: quantidade positiva comprada. AJUSTE: quantidade = novo estoque contado.
 * USO_INTERNO: quantidade positiva consumida na barbearia (pomada usada no cliente etc.).
 */
public record MovimentoEstoqueRequestDTO(
        @NotNull(message = "é obrigatório") TipoMovimento tipo,
        @NotNull(message = "é obrigatório") Integer quantidade,
        BigDecimal custoUnitario,
        String motivo) {
}
