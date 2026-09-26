package com.redebarbeariasapi.dto;

/** Versao publica do barbeiro (site): sem telefone, e-mail nem comissao. */
public record BarbeiroPublicoDTO(
        Long id, Long unidadeId, String unidadeNome, String nome, String apelido,
        String especialidades, String bio, String fotoUrl, Double notaMedia, long totalAvaliacoes) {
}
