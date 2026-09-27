package com.redebarbeariasapi.dto;

import com.redebarbeariasapi.model.Papel;
import jakarta.validation.constraints.*;

/** password: obrigatoria na criacao; na edicao, vazia = mantem a atual. */
public record UsuarioRequestDTO(
        @NotBlank(message = "é obrigatório") @Pattern(regexp = "^[A-Za-z0-9._@-]{3,60}$", message = "3 a 60 caracteres, sem espaço") String username,
        @Size(min = 6, max = 100, message = "mínimo 6 caracteres") String password,
        String nome,
        @NotNull(message = "é obrigatório") Papel papel,
        Long unidadeId,
        Long barbeiroId,
        Long clienteId,
        Boolean ativo,
        @Size(max = 150) String email,
        @Size(max = 30) String telefone) {
}
