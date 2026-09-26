package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {
    boolean existsByAgendamentoIdAndTipoAndCanalAndStatusAndReferencia(
            Long agendamentoId, TipoNotificacao tipo, CanalNotificacaoTipo canal, StatusNotificacao status, String referencia);
    long countByAgendamentoIdAndTipoAndCanalAndStatus(Long agendamentoId, TipoNotificacao tipo, CanalNotificacaoTipo canal, StatusNotificacao status);
    List<Notificacao> findByAgendamentoIdOrderByDataHoraDesc(Long agendamentoId);
    List<Notificacao> findTop200ByOrderByDataHoraDesc();
    void deleteByAgendamentoId(Long agendamentoId);
}
