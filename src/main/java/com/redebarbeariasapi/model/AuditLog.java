package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria")
@Getter @Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String usuario;
    @Column(nullable = false)
    private String acao;
    @Column(nullable = false)
    private String entidade;
    private Long entidadeId;
    @Column(length = 1000)
    private String detalhe;
    @Column(nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();
}
