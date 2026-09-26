package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Avaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {
    Optional<Avaliacao> findByAgendamentoId(Long agendamentoId);
    boolean existsByAgendamentoId(Long agendamentoId);

    @Query("select a from Avaliacao a where (:unidadeId is null or a.agendamento.unidade.id = :unidadeId) " +
           "and (:barbeiroId is null or a.agendamento.barbeiro.id = :barbeiroId) order by a.criadaEm desc")
    List<Avaliacao> filtrar(@Param("unidadeId") Long unidadeId, @Param("barbeiroId") Long barbeiroId);

    @Query("select a from Avaliacao a where a.publica = true and a.nota >= 4 and a.comentario is not null order by a.criadaEm desc")
    List<Avaliacao> destaquesPublicos();

    @Query("select a.agendamento.barbeiro.id, avg(a.nota), count(a) from Avaliacao a group by a.agendamento.barbeiro.id")
    List<Object[]> mediasPorBarbeiro();

    @Query("select a.agendamento.id, a.nota from Avaliacao a where a.agendamento.id in :ids")
    List<Object[]> notasDe(@Param("ids") java.util.Collection<Long> ids);
}
