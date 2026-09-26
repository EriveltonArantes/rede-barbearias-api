package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Agendamento;
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
    long countByBarbeiroId(Long barbeiroId);
    long countByServicoId(Long servicoId);
    long countByUnidadeId(Long unidadeId);
}
