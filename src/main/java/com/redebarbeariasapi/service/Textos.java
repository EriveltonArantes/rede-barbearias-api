package com.redebarbeariasapi.service;

import com.redebarbeariasapi.exception.ValidacaoException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/** Utilitarios pequenos de texto e dinheiro usados pelos servicos. */
public final class Textos {
    private Textos() {}

    public static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Deixa so digitos (DDD + numero, sem +55). Aceita fixo (10) ou celular (11). */
    public static String telefone(String bruto) {
        String d = bruto == null ? "" : bruto.replaceAll("\\D", "");
        if (d.length() > 11 && d.startsWith("55")) d = d.substring(2);
        if (d.length() < 10 || d.length() > 11) {
            throw new ValidacaoException("Telefone inválido: informe DDD + número (ex.: 31 99999-8888).");
        }
        return d;
    }

    public static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    public static BigDecimal zeroSeNulo(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** base * percentual / 100, arredondado em centavos. */
    public static BigDecimal percentual(BigDecimal base, BigDecimal pct) {
        return zeroSeNulo(base).multiply(zeroSeNulo(pct)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal dinheiro(BigDecimal v) {
        return zeroSeNulo(v).setScale(2, RoundingMode.HALF_UP);
    }
}
