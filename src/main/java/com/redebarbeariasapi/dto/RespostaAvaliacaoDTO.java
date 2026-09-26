package com.redebarbeariasapi.dto;

import jakarta.validation.constraints.Size;

public record RespostaAvaliacaoDTO(@Size(max = 1000) String resposta, Boolean publica) {
}
