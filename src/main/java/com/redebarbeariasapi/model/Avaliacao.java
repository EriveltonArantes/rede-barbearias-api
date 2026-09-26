package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "avaliacoes")
@Getter @Setter
public class Avaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "agendamento_id", unique = true)
    private Agendamento agendamento;

    @Column(nullable = false)
    private Integer nota;
    @Column(length = 1000)
    private String comentario;
    /** Aparece no site institucional (o admin pode ocultar). */
    private boolean publica = true;
    @Column(length = 1000)
    private String resposta;
    @Column(nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();
}
