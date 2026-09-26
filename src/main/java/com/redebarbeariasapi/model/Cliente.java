package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "clientes")
@Getter @Setter
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;
    /** So digitos (DDD + numero) — identifica o cliente no agendamento online. */
    @Column(nullable = false, unique = true)
    private String telefone;
    private String email;
    private LocalDate dataNascimento;
    @Column(length = 1000)
    private String observacoes;

    @ManyToOne
    @JoinColumn(name = "unidade_preferida_id")
    private Unidade unidadePreferida;
    @ManyToOne
    @JoinColumn(name = "barbeiro_preferido_id")
    private Barbeiro barbeiroPreferido;

    /** Pontos do programa de fidelidade (1 por atendimento pago). */
    private int pontos;
    private boolean aceitaMarketing = true;

    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime ultimaVisita;
}
