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

    // ---------------- sinal por Pix ----------------
    @Column(precision = 10, scale = 2)
    private BigDecimal sinalValor;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SituacaoSinal sinalSituacao;
    private LocalDateTime sinalExpiraEm;
    private LocalDateTime sinalPagoEm;
    /** Id do pagamento no gateway (Mercado Pago) quando a confirmacao e automatica. */
    @Column(length = 60)
    private String sinalGatewayId;
    /** Pix copia-e-cola gerado pelo gateway (o estatico e calculado na hora). */
    @Column(length = 800)
    private String sinalPixCopiaECola;

    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime atualizadoEm;

    /** Valor que vale pra cobranca: preco de tabela menos desconto do cupom. */
    public BigDecimal valorAPagar() {
        return valor.subtract(desconto == null ? BigDecimal.ZERO : desconto).subtract(sinalAbativel()).max(BigDecimal.ZERO);
    }

    /** Sinal ja pago que entra como parte do pagamento no dia. */
    public BigDecimal sinalAbativel() {
        return sinalValor != null && (sinalSituacao == SituacaoSinal.PAGO || sinalSituacao == SituacaoSinal.ABATIDO)
                ? sinalValor : BigDecimal.ZERO;
    }

    public boolean aguardandoSinal() {
        return sinalSituacao == SituacaoSinal.PENDENTE;
    }
}
