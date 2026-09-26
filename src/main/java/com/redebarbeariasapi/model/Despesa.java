package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "despesas")
@Getter @Setter
public class Despesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_id")
    private Unidade unidade;

    @Column(nullable = false)
    private String descricao;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaDespesa categoria = CategoriaDespesa.OUTROS;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;
    @Column(nullable = false)
    private LocalDate vencimento;
    private boolean paga;
    private LocalDate pagaEm;
    private String fornecedor;
    private String comprovanteUrl;
}
