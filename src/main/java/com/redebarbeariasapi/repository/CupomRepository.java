package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Cupom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CupomRepository extends JpaRepository<Cupom, Long> {
    Optional<Cupom> findByCodigoIgnoreCase(String codigo);
    List<Cupom> findAllByOrderByCodigo();
}
