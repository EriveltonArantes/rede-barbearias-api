package com.redebarbeariasapi.mapper;

import com.redebarbeariasapi.dto.ClienteResponseDTO;
import com.redebarbeariasapi.model.Cliente;

public final class ClienteMapper {
    private ClienteMapper() {}

    public static ClienteResponseDTO toResponse(Cliente c) {
        return new ClienteResponseDTO(c.getId(), c.getNome(), c.getTelefone(), c.getEmail(), c.getDataNascimento(),
                c.getObservacoes(),
                c.getUnidadePreferida() == null ? null : c.getUnidadePreferida().getId(),
                c.getUnidadePreferida() == null ? null : c.getUnidadePreferida().getNome(),
                c.getBarbeiroPreferido() == null ? null : c.getBarbeiroPreferido().getId(),
                c.getBarbeiroPreferido() == null ? null : c.getBarbeiroPreferido().getNome(),
                c.getPontos(), c.isAceitaMarketing(), c.getCriadoEm(), c.getUltimaVisita());
    }

    public static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) return "";
        return nome.trim().split("\\s+")[0];
    }
}
