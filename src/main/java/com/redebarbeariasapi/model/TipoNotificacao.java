package com.redebarbeariasapi.model;

public enum TipoNotificacao {
    /** Logo que o horario e marcado (site, app ou balcao). */
    CONFIRMACAO,
    /** No dia do atendimento, a partir da hora configurada. */
    LEMBRETE,
    /** Pouco antes do horario (padrao: 1 hora antes). */
    LEMBRETE_PROXIMO,
    /** Horario, barbeiro ou servico mudou. */
    REAGENDAMENTO,
    CANCELAMENTO,
    /** Depois do atendimento pago: pede a avaliacao pelo link e mostra a cartela de fidelidade. */
    AVALIACAO
}
