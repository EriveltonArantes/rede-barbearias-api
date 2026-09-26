package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/** Plano do clube de assinatura (ex.: "Corte ilimitado" R$ 99/mes). */
@Entity
@Table(name = "planos")
@Getter @Setter
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;
    @Column(length = 800)
    private String descricao;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoMensal;
    /** Atendimentos incluidos por ciclo mensal. */
    @Column(nullable = false)
    private Integer usosPorMes = 4;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "plano_servicos",
            joinColumns = @JoinColumn(name = "plano_id"),
            inverseJoinColumns = @JoinColumn(name = "servico_id"))
    private Set<Servico> servicos = new HashSet<>();

    private boolean ativo = true;
}
