package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.ListaEspera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ListaEsperaRepository extends JpaRepository<ListaEspera, Long> {

    /** Fila do dia, na ordem de chegada. */
    List<ListaEspera> findByUnidadeIdAndDataAndStatusInOrderByCriadoEm(Long unidadeId, LocalDate data, Collection<ListaEspera.Status> status);

    List<ListaEspera> findByClienteIdAndStatusIn(Long clienteId, Collection<ListaEspera.Status> status);

    @Query("select e from ListaEspera e where e.data >= :de and e.data <= :ate and (:unidadeId is null or e.unidade.id = :unidadeId) order by e.data, e.criadoEm")
    List<ListaEspera> periodo(@Param("de") LocalDate de, @Param("ate") LocalDate ate, @Param("unidadeId") Long unidadeId);

    @Modifying
    @Query("update ListaEspera e set e.status = com.redebarbeariasapi.model.ListaEspera.Status.EXPIROU " +
           "where e.data < :hoje and e.status in (com.redebarbeariasapi.model.ListaEspera.Status.AGUARDANDO, com.redebarbeariasapi.model.ListaEspera.Status.AVISADO)")
    int expirarAntesDe(@Param("hoje") LocalDate hoje);
}
