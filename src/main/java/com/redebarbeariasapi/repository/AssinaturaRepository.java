package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Assinatura;
import com.redebarbeariasapi.model.StatusAssinatura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssinaturaRepository extends JpaRepository<Assinatura, Long> {
    List<Assinatura> findByClienteIdAndStatus(Long clienteId, StatusAssinatura status);
    List<Assinatura> findByClienteIdOrderByInicioDesc(Long clienteId);
    List<Assinatura> findByStatus(StatusAssinatura status);
    List<Assinatura> findAllByOrderByStatusAscValidaAteAsc();
    long countByPlanoIdAndStatus(Long planoId, StatusAssinatura status);
    long countByPlanoId(Long planoId);
}
