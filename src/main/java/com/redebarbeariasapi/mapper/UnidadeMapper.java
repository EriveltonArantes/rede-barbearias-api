package com.redebarbeariasapi.mapper;

import com.redebarbeariasapi.dto.UnidadeRequestDTO;
import com.redebarbeariasapi.dto.UnidadeResponseDTO;
import com.redebarbeariasapi.model.Unidade;

public final class UnidadeMapper {
    private UnidadeMapper() {}

    public static UnidadeResponseDTO toResponse(Unidade u, long totalBarbeiros) {
        return new UnidadeResponseDTO(u.getId(), u.getNome(), u.getEndereco(), u.getBairro(), u.getCidade(),
                u.getTelefone(), u.getWhatsapp(), u.getEmail(), u.getFotoUrl(),
                u.getHoraAbertura(), u.getHoraFechamento(), u.getDiasFuncionamento(),
                u.getChavePix(), u.isAtiva(), totalBarbeiros);
    }

    public static void aplicar(UnidadeRequestDTO d, Unidade u) {
        u.setNome(d.nome().trim());
        u.setEndereco(d.endereco().trim());
        u.setBairro(d.bairro());
        u.setCidade(d.cidade());
        u.setTelefone(d.telefone());
        u.setWhatsapp(d.whatsapp());
        u.setEmail(d.email());
        u.setFotoUrl(d.fotoUrl());
        u.setHoraAbertura(d.horaAbertura());
        u.setHoraFechamento(d.horaFechamento());
        u.setDiasFuncionamento(d.diasFuncionamento());
        u.setChavePix(d.chavePix());
        if (d.ativa() != null) u.setAtiva(d.ativa());
    }
}
