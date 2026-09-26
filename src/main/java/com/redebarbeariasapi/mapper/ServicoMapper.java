package com.redebarbeariasapi.mapper;

import com.redebarbeariasapi.dto.ServicoRequestDTO;
import com.redebarbeariasapi.dto.ServicoResponseDTO;
import com.redebarbeariasapi.model.Servico;

public final class ServicoMapper {
    private ServicoMapper() {}

    public static ServicoResponseDTO toResponse(Servico s) {
        return new ServicoResponseDTO(s.getId(), s.getNome(), s.getDescricao(), s.getCategoria(),
                s.getPreco(), s.getDuracaoMinutos(), s.isAtivo());
    }

    public static void aplicar(ServicoRequestDTO d, Servico s) {
        s.setNome(d.nome().trim());
        s.setDescricao(d.descricao());
        s.setCategoria(d.categoria());
        s.setPreco(d.preco());
        s.setDuracaoMinutos(d.duracaoMinutos());
        if (d.ativo() != null) s.setAtivo(d.ativo());
    }
}
