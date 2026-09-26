package com.redebarbeariasapi.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Horario livre e os barbeiros disponiveis nele. */
public record SlotDTO(String hora, LocalDateTime inicio, List<Opcao> barbeiros) {
    public record Opcao(Long id, String nome) {}
}
