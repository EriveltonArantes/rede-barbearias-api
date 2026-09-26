package com.redebarbeariasapi.mapper;

import com.redebarbeariasapi.dto.BarbeiroPublicoDTO;
import com.redebarbeariasapi.dto.BarbeiroRequestDTO;
import com.redebarbeariasapi.dto.BarbeiroResponseDTO;
import com.redebarbeariasapi.model.Barbeiro;

public final class BarbeiroMapper {
    private BarbeiroMapper() {}

    public static BarbeiroResponseDTO toResponse(Barbeiro b, Double nota, long total) {
        return new BarbeiroResponseDTO(b.getId(), b.getUnidade().getId(), b.getUnidade().getNome(), b.getNome(),
                b.getApelido(), b.getTelefone(), b.getEmail(), b.getEspecialidades(), b.getBio(), b.getFotoUrl(),
                b.getComissaoServico(), b.getComissaoProduto(), b.getDiasTrabalho(), b.isAtivo(), nota, total);
    }

    public static BarbeiroPublicoDTO toPublico(Barbeiro b, Double nota, long total) {
        return new BarbeiroPublicoDTO(b.getId(), b.getUnidade().getId(), b.getUnidade().getNome(), b.getNome(),
                b.getApelido(), b.getEspecialidades(), b.getBio(), b.getFotoUrl(), nota, total);
    }

    public static void aplicar(BarbeiroRequestDTO d, Barbeiro b) {
        b.setNome(d.nome().trim());
        b.setApelido(d.apelido());
        b.setTelefone(d.telefone());
        b.setEmail(d.email());
        b.setEspecialidades(d.especialidades());
        b.setBio(d.bio());
        b.setFotoUrl(d.fotoUrl());
        if (d.comissaoServico() != null) b.setComissaoServico(d.comissaoServico());
        if (d.comissaoProduto() != null) b.setComissaoProduto(d.comissaoProduto());
        b.setDiasTrabalho(d.diasTrabalho() == null || d.diasTrabalho().isBlank() ? null : d.diasTrabalho());
        if (d.ativo() != null) b.setAtivo(d.ativo());
    }
}
