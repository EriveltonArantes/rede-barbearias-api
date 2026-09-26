package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimentos_estoque")
@Getter @Setter
public class MovimentoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimento tipo;
    /** Positivo entra, negativo sai. */
    @Column(nullable = false)
    private Integer quantidade;
    @Column(precision = 10, scale = 2)
    private BigDecimal custoUnitario;
    private String motivo;
    private String usuario;
    @Column(nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();
}
