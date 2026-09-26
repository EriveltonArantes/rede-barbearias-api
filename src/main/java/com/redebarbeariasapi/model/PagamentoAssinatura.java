package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagamentos_assinatura")
@Getter @Setter
public class PagamentoAssinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "assinatura_id")
    private Assinatura assinatura;
    /** Unidade que recebeu o pagamento (pra fechar o caixa certo). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_id")
    private Unidade unidade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormaPagamento formaPagamento;
    @Column(nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();
}
