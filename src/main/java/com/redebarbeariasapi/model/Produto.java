package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "produtos")
@Getter @Setter
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_id")
    private Unidade unidade;

    @Column(nullable = false)
    private String nome;
    private String marca;
    private String categoria;
    private String codigoBarras;
    private String fotoUrl;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoCusto = BigDecimal.ZERO;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoVenda;
    @Column(nullable = false)
    private Integer estoque = 0;
    @Column(nullable = false)
    private Integer estoqueMinimo = 3;
    private boolean ativo = true;

    public boolean estoqueBaixo() {
        return estoque != null && estoqueMinimo != null && estoque <= estoqueMinimo;
    }
}
