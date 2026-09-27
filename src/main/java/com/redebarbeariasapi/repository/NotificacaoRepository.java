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
    /** Mensagens que nao sao de um horario (aniversario, retorno): o "ja mandei" e por cliente + referencia. */
    boolean existsByClienteIdAndTipoAndCanalAndStatusAndReferencia(
            Long clienteId, TipoNotificacao tipo, CanalNotificacaoTipo canal, StatusNotificacao status, String referencia);
    long countByClienteIdAndTipoAndCanalAndStatusAndReferencia(
            Long clienteId, TipoNotificacao tipo, CanalNotificacaoTipo canal, StatusNotificacao status, String referencia);
    List<Notificacao> findByClienteIdOrderByDataHoraDesc(Long clienteId);
}
