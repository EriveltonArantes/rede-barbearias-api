package com.redebarbeariasapi.dto;

import java.math.BigDecimal;
import java.util.List;

/** Ficha completa do cliente: historico, gasto, fidelidade e assinatura. */
public record ClienteFichaDTO(
        ClienteResponseDTO cliente,
        BigDecimal totalGasto,
        long atendimentos,
        long faltas,
        long cancelamentos,
        BigDecimal ticketMedio,
        int pontosParaResgate,
        boolean podeResgatar,
        AssinaturaResponseDTO assinatura,
        String servicoFavorito,
        List<AgendamentoResponseDTO> agendamentos,
        List<VendaResponseDTO> compras) {
}
