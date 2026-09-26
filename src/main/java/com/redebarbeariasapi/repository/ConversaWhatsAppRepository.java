package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.ConversaWhatsApp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversaWhatsAppRepository extends JpaRepository<ConversaWhatsApp, Long> {
    Optional<ConversaWhatsApp> findByTelefone(String telefone);
    List<ConversaWhatsApp> findTop100ByOrderByUltimaRecebidaEmDesc();
}
