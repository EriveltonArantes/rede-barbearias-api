package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Agendamento;
import com.redebarbeariasapi.model.SituacaoSinal;
import com.redebarbeariasapi.model.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    Optional<Agendamento> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);

    @Query("select a from Agendamento a where a.barbeiro.id = :barbeiroId and a.status in :status " +
           "and a.inicio < :fim and a.fim > :inicio and (:ignorarId is null or a.id <> :ignorarId)")
    List<Agendamento> conflitos(@Param("barbeiroId") Long barbeiroId,
                                @Param("inicio") LocalDateTime inicio,
                                @Param("fim") LocalDateTime fim,
                                @Param("status") Collection<StatusAgendamento> status,
                                @Param("ignorarId") Long ignorarId);

    @Query("select a from Agendamento a where a.barbeiro.id in :barbeiros and a.status in :status " +
           "and a.inicio < :ate and a.fim > :de")
    List<Agendamento> ocupados(@Param("barbeiros") Collection<Long> barbeiros,
                               @Param("de") LocalDateTime de,
                               @Param("ate") LocalDateTime ate,
                               @Param("status") Collection<StatusAgendamento> status);

    @Query("select a from Agendamento a where a.inicio >= :de and a.inicio < :ate " +
           "and (:unidadeId is null or a.unidade.id = :unidadeId) " +
           "and (:barbeiroId is null or a.barbeiro.id = :barbeiroId) " +
           "and (:clienteId is null or a.cliente.id = :clienteId) " +
           "and (:status is null or a.status = :status) order by a.inicio")
    List<Agendamento> filtrar(@Param("de") LocalDateTime de,
                              @Param("ate") LocalDateTime ate,
                              @Param("unidadeId") Long unidadeId,
                              @Param("barbeiroId") Long barbeiroId,
                              @Param("clienteId") Long clienteId,
                              @Param("status") StatusAgendamento status);

    /** Atendimentos pagos no periodo (base do financeiro — conta pela data do pagamento). */
    @Query("select a from Agendamento a where a.pago = true and a.pagoEm >= :de and a.pagoEm < :ate " +
           "and (:unidadeId is null or a.unidade.id = :unidadeId)")
    List<Agendamento> pagosNoPeriodo(@Param("de") LocalDateTime de,
                                     @Param("ate") LocalDateTime ate,
                                     @Param("unidadeId") Long unidadeId);

    List<Agendamento> findByClienteIdOrderByInicioDesc(Long clienteId);

    /** Faltas sem aviso do cliente desde uma data (regra do sinal). */
    long countByClienteIdAndStatusAndInicioAfter(Long clienteId, StatusAgendamento status, LocalDateTime desde);

    List<Agendamento> findBySinalSituacaoOrderByInicio(SituacaoSinal situacao);

    /** Sinais que ficaram com a barbearia (falta / cancelamento em cima da hora): receita do periodo. */
    @Query("select a from Agendamento a where a.sinalSituacao = com.redebarbeariasapi.model.SituacaoSinal.RETIDO " +
           "and a.sinalPagoEm >= :de and a.sinalPagoEm < :ate and (:unidadeId is null or a.unidade.id = :unidadeId)")
    List<Agendamento> sinaisRetidos(@Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate, @Param("unidadeId") Long unidadeId);

    /** Algum horario em aberto daqui pra frente (nao manda "bora voltar?" pra quem ja marcou). */
    @Query("select count(a) > 0 from Agendamento a where a.cliente.id = :clienteId and a.inicio > :agora " +
           "and a.status in (com.redebarbeariasapi.model.StatusAgendamento.AGENDADO, com.redebarbeariasapi.model.StatusAgendamento.CONFIRMADO)")
    boolean temHorarioFuturo(@Param("clienteId") Long clienteId, @Param("agora") LocalDateTime agora);
    long countByBarbeiroId(Long barbeiroId);
    long countByServicoId(Long servicoId);
    long countByUnidadeId(Long unidadeId);
}
