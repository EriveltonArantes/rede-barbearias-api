package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "venda_itens")
@Getter @Setter
public class VendaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "venda_id")
    private Venda venda;
    @ManyToOne(optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Column(nullable = false)
    private Integer quantidade;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;
}
