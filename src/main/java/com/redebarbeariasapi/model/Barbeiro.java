package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;

@Entity
@Table(name = "barbeiros")
@Getter @Setter
public class Barbeiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_id")
    private Unidade unidade;

    @Column(nullable = false)
    private String nome;
    private String apelido;
    private String telefone;
    private String email;
    private String especialidades;
    @Column(length = 1000)
    private String bio;
    private String fotoUrl;

    /** Percentual de comissao sobre servicos (0-100). */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal comissaoServico = new BigDecimal("40");
    /** Percentual de comissao sobre venda de produtos (0-100). */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal comissaoProduto = new BigDecimal("10");

    /** Dias que o barbeiro atende (ISO 1-7). Vazio = todos os dias da unidade. */
    private String diasTrabalho;

    private boolean ativo = true;

    public boolean trabalhaEm(DayOfWeek dia) {
        if (!unidade.dias().contains(dia)) return false;
        return diasTrabalho == null || diasTrabalho.isBlank() || Dias.parse(diasTrabalho).contains(dia);
    }
}
