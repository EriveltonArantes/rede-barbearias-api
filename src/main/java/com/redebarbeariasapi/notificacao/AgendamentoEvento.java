package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.model.TipoNotificacao;

/** Publicado pela agenda; so vira mensagem depois que a transacao confirma (nada de avisar horario que deu rollback). */
public record AgendamentoEvento(Long agendamentoId, TipoNotificacao tipo) {
}
