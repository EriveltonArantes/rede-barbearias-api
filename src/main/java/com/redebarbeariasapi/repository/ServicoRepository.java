package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Servico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicoRepository extends JpaRepository<Servico, Long> {
    List<Servico> findByAtivoTrueOrderByCategoriaAscPrecoAsc();
    List<Servico> findAllByOrderByCategoriaAscPrecoAsc();
}
