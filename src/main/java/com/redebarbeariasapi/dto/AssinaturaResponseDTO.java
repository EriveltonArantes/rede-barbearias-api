package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.StatusAssinatura;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssinaturaResponseDTO(
        Long id, Long clienteId, String clienteNome, String clienteTelefone,
        Long planoId, String planoNome, BigDecimal precoMensal, Integer usosPorMes,
        int usosNoCiclo, int usosRestantes, StatusAssinatura status,
        LocalDate inicio, LocalDate cicloInicio, LocalDate validaAte, boolean vencida, LocalDate canceladaEm) {
}
