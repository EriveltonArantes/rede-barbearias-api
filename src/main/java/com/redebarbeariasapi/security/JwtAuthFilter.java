package com.redebarbeariasapi.security;

import com.redebarbeariasapi.model.Usuario;
import com.redebarbeariasapi.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Valida o JWT e recarrega o usuario do banco a cada requisicao: se o admin
 * desativar a conta ou trocar o papel, vale na hora (nao espera o token expirar).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UsuarioRepository usuarios;

    public JwtAuthFilter(JwtUtil jwtUtil, UsuarioRepository usuarios) {
        this.jwtUtil = jwtUtil;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwtUtil.usernameValido(header.substring(7))
                    .flatMap(usuarios::findByUsernameIgnoreCase)
                    .filter(Usuario::isAtivo)
                    .ifPresent(u -> {
                        UsuarioLogado logado = new UsuarioLogado(
                                u.getId(), u.getUsername(), u.getNome(), u.getPapel(),
                                unidadeDe(u),
                                u.getBarbeiro() == null ? null : u.getBarbeiro().getId(),
                                u.getCliente() == null ? null : u.getCliente().getId());
                        var auth = new UsernamePasswordAuthenticationToken(logado, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + u.getPapel().name())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }

    static Long unidadeDe(Usuario u) {
        if (u.getUnidade() != null) return u.getUnidade().getId();
        if (u.getBarbeiro() != null) return u.getBarbeiro().getUnidade().getId();
        return null;
    }
}
