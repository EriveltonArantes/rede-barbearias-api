package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Unidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnidadeRepository extends JpaRepository<Unidade, Long> {
    List<Unidade> findByAtivaTrueOrderByNome();
    List<Unidade> findAllByOrderByNome();
}
