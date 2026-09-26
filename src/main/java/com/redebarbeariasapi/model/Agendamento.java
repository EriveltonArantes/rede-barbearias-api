package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "agendamentos", indexes = {
        @Index(name = "idx_ag_barbeiro_inicio", columnList = "barbeiro_id,inicio"),
        @Index(name = "idx_ag_unidade_inicio", columnList = "unidade_id,inicio")
})
@Getter @Setter
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Codigo curto que o cliente usa pra consultar/cancelar/avaliar sem login. */
    @Column(nullable = false, unique = true, length = 12)
    private String codigo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_id")
    private Unidade unidade;
    @ManyToOne(optional = false)
    @JoinColumn(name = "barbeiro_id")
    private Barbeiro barbeiro;
    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;
    @ManyToOne(optional = false)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @Column(nullable = false)
    private LocalDateTime inicio;
    @Column(nullable = false)
    private LocalDateTime fim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAgendamento status = StatusAgendamento.AGENDADO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigemAgendamento origem = OrigemAgendamento.BALCAO;

    /** Preco de tabela no momento do agendamento. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;
    /** Valor efetivamente cobrado (preenchido ao finalizar). */
    @Column(precision = 10, scale = 2)
    private BigDecimal valorFinal;
    private String cupomCodigo;

    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;
    private boolean pago;
    private LocalDateTime pagoEm;
    @Column(precision = 10, scale = 2)
    private BigDecimal comissaoValor;

    @Column(length = 1000)
    private String observacao;
    private String motivoCancelamento;
    private boolean lembreteEnviado;

    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime atualizadoEm;

    /** Valor que vale pra cobranca: preco de tabela menos desconto do cupom. */
    public BigDecimal valorAPagar() {
        return valor.subtract(desconto == null ? BigDecimal.ZERO : desconto).max(BigDecimal.ZERO);
    }
}
