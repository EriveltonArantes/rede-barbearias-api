package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VendaRepository extends JpaRepository<Venda, Long> {

    @Query("select distinct v from Venda v left join fetch v.itens where v.dataHora >= :de and v.dataHora < :ate " +
           "and (:unidadeId is null or v.unidade.id = :unidadeId) order by v.dataHora desc")
    List<Venda> periodo(@Param("de") LocalDateTime de,
                        @Param("ate") LocalDateTime ate,
                        @Param("unidadeId") Long unidadeId);

    @Query("select distinct v from Venda v left join fetch v.itens where v.cliente.id = :clienteId order by v.dataHora desc")
    List<Venda> doCliente(@Param("clienteId") Long clienteId);

    @Query("select count(i) from VendaItem i where i.produto.id = :produtoId")
    long itensDoProduto(@Param("produtoId") Long produtoId);
}
