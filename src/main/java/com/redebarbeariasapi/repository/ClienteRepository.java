package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByTelefone(String telefone);

    @Query("select c from Cliente c where lower(c.nome) like lower(concat('%', :q, '%')) " +
           "or c.telefone like concat('%', :q, '%') or lower(coalesce(c.email, '')) like lower(concat('%', :q, '%')) order by c.nome")
    List<Cliente> buscar(@Param("q") String q);

    List<Cliente> findAllByOrderByNome();
    long countByCriadoEmGreaterThanEqual(LocalDateTime desde);
}
