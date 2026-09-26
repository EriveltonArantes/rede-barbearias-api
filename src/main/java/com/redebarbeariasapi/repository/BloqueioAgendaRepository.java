package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.BloqueioAgenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BloqueioAgendaRepository extends JpaRepository<BloqueioAgenda, Long> {

    @Query("select b from BloqueioAgenda b where b.unidade.id = :unidadeId and b.inicio < :fim and b.fim > :inicio order by b.inicio")
    List<BloqueioAgenda> sobrepostos(@Param("unidadeId") Long unidadeId,
                                     @Param("inicio") LocalDateTime inicio,
                                     @Param("fim") LocalDateTime fim);

    @Query("select b from BloqueioAgenda b where b.fim >= :desde " +
           "and (:unidadeId is null or b.unidade.id = :unidadeId) " +
           "and (:barbeiroId is null or b.barbeiro.id = :barbeiroId or b.barbeiro is null) order by b.inicio")
    List<BloqueioAgenda> futuros(@Param("desde") LocalDateTime desde,
                                 @Param("unidadeId") Long unidadeId,
                                 @Param("barbeiroId") Long barbeiroId);
}
