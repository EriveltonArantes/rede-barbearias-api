package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Getter @Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;
    @Column(nullable = false)
    private String password;
    private String nome;
    /** Contato pra recuperar a senha (equipe). Cliente e barbeiro usam o da propria ficha se este ficar vazio. */
    private String email;
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Papel papel = Papel.CLIENTE;

    /** GERENTE/RECEPCAO: unidade que administram. */
    @ManyToOne
    @JoinColumn(name = "unidade_id")
    private Unidade unidade;
    /** BARBEIRO: ficha do barbeiro. */
    @ManyToOne
    @JoinColumn(name = "barbeiro_id")
    private Barbeiro barbeiro;
    /** CLIENTE: ficha do cliente. */
    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    private boolean ativo = true;
    private LocalDateTime ultimoLogin;
    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
