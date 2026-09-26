package com.redebarbeariasapi.dto;

import java.math.BigDecimal;

public record PixResponseDTO(String payload, String qrCodeBase64, BigDecimal valor, String chave, String recebedor, String txid) {
}
