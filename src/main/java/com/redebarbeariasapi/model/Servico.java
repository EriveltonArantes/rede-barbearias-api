package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "servicos")
@Getter @Setter
public class Servico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;
    @Column(length = 600)
    private String descricao;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaServico categoria = CategoriaServico.CORTE;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;
    @Column(nullable = false)
    private Integer duracaoMinutos = 30;
    private boolean ativo = true;
}
