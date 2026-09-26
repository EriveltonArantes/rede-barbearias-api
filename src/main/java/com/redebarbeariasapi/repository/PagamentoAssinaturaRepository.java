package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.PagamentoAssinatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PagamentoAssinaturaRepository extends JpaRepository<PagamentoAssinatura, Long> {

    @Query("select p from PagamentoAssinatura p where p.dataHora >= :de and p.dataHora < :ate " +
           "and (:unidadeId is null or p.unidade.id = :unidadeId) order by p.dataHora")
    List<PagamentoAssinatura> periodo(@Param("de") LocalDateTime de,
                                      @Param("ate") LocalDateTime ate,
                                      @Param("unidadeId") Long unidadeId);

    List<PagamentoAssinatura> findByAssinaturaIdOrderByDataHoraDesc(Long assinaturaId);
}
