package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cliente que queria um horario num dia lotado: e avisado quando alguem cancela. */
@Entity
@Table(name = "lista_espera", indexes = @Index(name = "idx_espera_dia", columnList = "unidade_id,data,status"))
@Getter @Setter
public class ListaEspera {

    public enum Periodo { QUALQUER, MANHA, TARDE, NOITE }
    public enum Status { AGUARDANDO, AVISADO, AGENDOU, EXPIROU, DESISTIU }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;
    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_id")
    private Unidade unidade;
    @ManyToOne(optional = false)
    @JoinColumn(name = "servico_id")
    private Servico servico;
    /** null = qualquer barbeiro. */
    @ManyToOne
    @JoinColumn(name = "barbeiro_id")
    private Barbeiro barbeiro;

    @Column(nullable = false)
    private LocalDate data;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Periodo periodo = Periodo.QUALQUER;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.AGUARDANDO;

    private int avisos;
    private LocalDateTime avisadoEm;
    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public boolean cabeNoPeriodo(int hora) {
        return switch (periodo) {
            case QUALQUER -> true;
            case MANHA -> hora < 12;
            case TARDE -> hora >= 12 && hora < 18;
            case NOITE -> hora >= 18;
        };
    }
}
