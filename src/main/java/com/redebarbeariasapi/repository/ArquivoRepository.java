package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Arquivo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArquivoRepository extends JpaRepository<Arquivo, Long> {
}
