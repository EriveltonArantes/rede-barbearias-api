package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Arquivo enviado (foto de barbeiro/produto/unidade, comprovante). Guardado no banco, sem S3. */
@Entity
@Table(name = "arquivos")
@Getter @Setter
public class Arquivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)
    private String contentType;
    @Column(nullable = false)
    private Long tamanho;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] dados;

    private String enviadoPor;
    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
