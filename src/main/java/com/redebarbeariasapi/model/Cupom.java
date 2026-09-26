package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name = "cupons")
@Getter @Setter
public class Cupom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;
    private String descricao;
    /** Desconto percentual (0-100). Se preenchido, tem prioridade sobre o valor fixo. */
    @Column(precision = 5, scale = 2)
    private BigDecimal percentual;
    @Column(precision = 10, scale = 2)
    private BigDecimal valorFixo;
    private LocalDate validoAte;
    private Integer limiteUsos;
    private int usos;
    private boolean ativo = true;

    public boolean valido(LocalDate hoje) {
        return ativo
                && (validoAte == null || !hoje.isAfter(validoAte))
                && (limiteUsos == null || usos < limiteUsos);
    }

    public BigDecimal descontoPara(BigDecimal valor) {
        BigDecimal d;
        if (percentual != null && percentual.signum() > 0) {
            d = valor.multiply(percentual).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (valorFixo != null) {
            d = valorFixo;
        } else {
            d = BigDecimal.ZERO;
        }
        return d.min(valor).setScale(2, RoundingMode.HALF_UP);
    }
}
