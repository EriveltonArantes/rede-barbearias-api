package com.redebarbeariasapi.dto;

import java.math.BigDecimal;

public record CupomValidacaoDTO(String codigo, boolean valido, String mensagem, BigDecimal desconto, BigDecimal valorFinal) {
}
