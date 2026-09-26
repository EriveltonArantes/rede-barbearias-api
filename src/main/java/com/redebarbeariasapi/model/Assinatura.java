package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "assinaturas")
@Getter @Setter
public class Assinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;
    @ManyToOne(optional = false)
    @JoinColumn(name = "plano_id")
    private Plano plano;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAssinatura status = StatusAssinatura.ATIVA;
    @Column(nullable = false)
    private LocalDate inicio;
    /** Inicio do ciclo atual (os usos zeram a cada renovacao paga). */
    @Column(nullable = false)
    private LocalDate cicloInicio;
    /** Ate quando o ciclo pago cobre. Depois disso a assinatura nao pode ser usada. */
    @Column(nullable = false)
    private LocalDate validaAte;
    private int usosNoCiclo;
    private LocalDate canceladaEm;

    public boolean utilizavelEm(LocalDate dia) {
        return status == StatusAssinatura.ATIVA && !dia.isAfter(validaAte);
    }
}
