package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Barbeiro;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BarbeiroRepository extends JpaRepository<Barbeiro, Long> {
    List<Barbeiro> findByUnidadeIdAndAtivoTrueOrderByNome(Long unidadeId);
    List<Barbeiro> findByAtivoTrueOrderByNome();
    List<Barbeiro> findByUnidadeIdOrderByNome(Long unidadeId);
    List<Barbeiro> findAllByOrderByNome();
    long countByUnidadeId(Long unidadeId);

    /** Trava a linha do barbeiro durante a checagem de conflito — dois agendamentos
     *  simultaneos no mesmo horario nao passam juntos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Barbeiro b where b.id = :id")
    Optional<Barbeiro> travar(@Param("id") Long id);
}
