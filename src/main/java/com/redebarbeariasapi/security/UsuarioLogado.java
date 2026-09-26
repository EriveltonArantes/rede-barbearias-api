package com.redebarbeariasapi.security;

import com.redebarbeariasapi.model.Papel;

/** Principal colocado no SecurityContext a cada requisicao autenticada. */
public record UsuarioLogado(Long id, String username, String nome, Papel papel,
                            Long unidadeId, Long barbeiroId, Long clienteId) {

    /** GERENTE, RECEPCAO e BARBEIRO so enxergam a propria unidade. */
    public boolean presoAUnidade() {
        return papel == Papel.GERENTE || papel == Papel.RECEPCAO || papel == Papel.BARBEIRO;
    }
}
