package com.redebarbeariasapi.model;

import java.util.Set;

public enum StatusAgendamento {
    AGENDADO, CONFIRMADO, EM_ATENDIMENTO, CONCLUIDO, CANCELADO, NAO_COMPARECEU;

    /** Status que ocupam a cadeira do barbeiro (entram na checagem de conflito). */
    public static final Set<StatusAgendamento> OCUPAM_HORARIO = Set.of(AGENDADO, CONFIRMADO, EM_ATENDIMENTO, CONCLUIDO);

    public boolean finalizado() {
        return this == CONCLUIDO || this == CANCELADO || this == NAO_COMPARECEU;
    }
}
