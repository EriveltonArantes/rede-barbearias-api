package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    List<Produto> findByUnidadeIdOrderByNome(Long unidadeId);
    List<Produto> findAllByOrderByNome();
}
