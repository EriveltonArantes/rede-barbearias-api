package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.CanalNotificacaoTipo;
import com.redebarbeariasapi.model.StatusNotificacao;
import com.redebarbeariasapi.model.TipoNotificacao;

import java.time.LocalDateTime;

public record NotificacaoResponseDTO(
        Long id, Long agendamentoId, String codigo, String clienteNome, TipoNotificacao tipo,
        CanalNotificacaoTipo canal, StatusNotificacao status, String destino, String erro, LocalDateTime dataHora) {
}
