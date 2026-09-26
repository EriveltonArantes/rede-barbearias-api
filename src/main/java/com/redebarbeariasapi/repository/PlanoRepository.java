package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Plano;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanoRepository extends JpaRepository<Plano, Long> {
    List<Plano> findByAtivoTrueOrderByPrecoMensal();
    List<Plano> findAllByOrderByPrecoMensal();
}
