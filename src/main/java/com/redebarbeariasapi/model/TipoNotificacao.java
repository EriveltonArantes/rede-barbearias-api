package com.redebarbeariasapi.model;

public enum TipoNotificacao {
    /** Logo que o horario e marcado (site, app ou balcao). */
    CONFIRMACAO,
    /** No dia do atendimento, a partir da hora configurada. */
    LEMBRETE,
    /** Horario, barbeiro ou servico mudou. */
    REAGENDAMENTO,
    CANCELAMENTO,
    /** Depois do atendimento pago: pede a avaliacao pelo link. */
    AVALIACAO
}
