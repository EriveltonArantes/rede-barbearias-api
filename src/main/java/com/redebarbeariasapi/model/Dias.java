package com.redebarbeariasapi.model;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/** Converte "1,2,3" (ISO: 1=segunda) em dias da semana. */
public final class Dias {
    private Dias() {}

    public static Set<DayOfWeek> parse(String texto) {
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        if (texto == null || texto.isBlank()) return dias;
        Arrays.stream(texto.split(","))
                .map(String::trim)
                .filter(s -> s.matches("[1-7]"))
                .forEach(s -> dias.add(DayOfWeek.of(Integer.parseInt(s))));
        return dias;
    }
}
