package com.redebarbeariasapi.espera;

/** Um horario futuro foi cancelado: quem estava na lista de espera daquele dia pode ser avisado. */
public record HorarioLiberadoEvento(Long agendamentoId) {
}
