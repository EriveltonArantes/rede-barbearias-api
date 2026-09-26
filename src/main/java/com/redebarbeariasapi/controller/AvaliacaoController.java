package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.AvaliacaoResponseDTO;
import com.redebarbeariasapi.dto.RespostaAvaliacaoDTO;
import com.redebarbeariasapi.service.AvaliacaoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Avaliações")
@RestController
@RequestMapping("/api/avaliacoes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
public class AvaliacaoController {

    private final AvaliacaoService service;

    @GetMapping
    public List<AvaliacaoResponseDTO> listar(@RequestParam(required = false) Long unidadeId,
                                             @RequestParam(required = false) Long barbeiroId) {
        return service.listar(unidadeId, barbeiroId);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @PatchMapping("/{id}")
    public AvaliacaoResponseDTO responder(@PathVariable Long id, @Valid @RequestBody RespostaAvaliacaoDTO dto) {
        return service.responder(id, dto);
    }
}
