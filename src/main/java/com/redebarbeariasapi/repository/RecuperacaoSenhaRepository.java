package com.redebarbeariasapi.repository;

import com.redebarbeariasapi.model.RecuperacaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, Long> {

    Optional<RecuperacaoSenha> findFirstByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);

    long countByUsuarioIdAndCriadoEmAfter(Long usuarioId, LocalDateTime depois);

    /** Pedidos feitos pelo site que ninguem concluiu ainda: aparecem pra equipe ajudar. */
    @Query("select r from RecuperacaoSenha r where r.origem = 'CLIENTE' and r.usadoEm is null and r.substituido = false "
            + "and r.criadoEm > :depois order by r.criadoEm desc")
    List<RecuperacaoSenha> pendentes(@Param("depois") LocalDateTime depois);

    @Modifying
    @Query("update RecuperacaoSenha r set r.substituido = true where r.usuario.id = :usuarioId and r.usadoEm is null and r.substituido = false")
    int substituirAbertos(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("delete from RecuperacaoSenha r where r.usuario.id = :usuarioId")
    int apagarDoUsuario(@Param("usuarioId") Long usuarioId);
}
