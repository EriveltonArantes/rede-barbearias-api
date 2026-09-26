package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Despesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DespesaRepository extends JpaRepository<Despesa, Long> {

    @Query("select d from Despesa d where d.vencimento >= :de and d.vencimento <= :ate " +
           "and (:unidadeId is null or d.unidade.id = :unidadeId) order by d.vencimento")
    List<Despesa> periodo(@Param("de") LocalDate de, @Param("ate") LocalDate ate, @Param("unidadeId") Long unidadeId);

    @Query("select count(d) from Despesa d where d.paga = false and d.vencimento < :hoje " +
           "and (:unidadeId is null or d.unidade.id = :unidadeId)")
    long vencidas(@Param("hoje") LocalDate hoje, @Param("unidadeId") Long unidadeId);
}
