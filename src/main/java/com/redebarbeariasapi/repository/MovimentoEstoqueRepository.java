package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.MovimentoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {
    List<MovimentoEstoque> findTop100ByProdutoIdOrderByDataHoraDesc(Long produtoId);
    void deleteByProdutoId(Long produtoId);
}
