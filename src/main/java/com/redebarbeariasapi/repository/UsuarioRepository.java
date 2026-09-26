package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.Papel;
import com.redebarbeariasapi.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByPapel(Papel papel);
    List<Usuario> findAllByOrderByUsername();
    List<Usuario> findByUnidadeIdOrderByUsername(Long unidadeId);
    Optional<Usuario> findByClienteId(Long clienteId);
}
