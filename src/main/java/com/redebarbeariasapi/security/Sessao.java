package com.redebarbeariasapi.security;

import com.redebarbeariasapi.model.Papel;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

/** Acesso ao usuario da requisicao atual + regras de escopo por unidade. */
public final class Sessao {

    private Sessao() {}

    public static UsuarioLogado atualOuNulo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioLogado u) return u;
        return null;
    }

    public static UsuarioLogado atual() {
        UsuarioLogado u = atualOuNulo();
        if (u == null) throw new AccessDeniedException("Não autenticado");
        return u;
    }

    public static String username() {
        UsuarioLogado u = atualOuNulo();
        return u == null ? "publico" : u.username();
    }

    public static boolean eh(Papel papel) {
        UsuarioLogado u = atualOuNulo();
        return u != null && u.papel() == papel;
    }

    /** Unidade efetiva de um filtro: quem e preso a unidade sempre ve so a sua (ignora o pedido). */
    public static Long unidadeEscopo(Long pedida) {
        UsuarioLogado u = atualOuNulo();
        if (u != null && u.presoAUnidade()) return u.unidadeId();
        return pedida;
    }

    /** Garante que o usuario pode mexer em algo da unidade informada. */
    public static void exigirUnidade(Long unidadeId) {
        UsuarioLogado u = atual();
        if (u.presoAUnidade() && !Objects.equals(u.unidadeId(), unidadeId)) {
            throw new AccessDeniedException("Fora da sua unidade");
        }
    }

    /** Barbeiro so mexe no que e dele. */
    public static void exigirBarbeiro(Long barbeiroId) {
        UsuarioLogado u = atual();
        if (u.papel() == Papel.BARBEIRO && !Objects.equals(u.barbeiroId(), barbeiroId)) {
            throw new AccessDeniedException("Não é seu atendimento");
        }
    }
}
