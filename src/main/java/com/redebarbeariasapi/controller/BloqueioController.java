package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.BloqueioRequestDTO;
import com.redebarbeariasapi.dto.BloqueioResponseDTO;
import com.redebarbeariasapi.service.BloqueioService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Folgas e bloqueios de agenda")
@RestController
@RequestMapping("/api/bloqueios")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
public class BloqueioController {

    private final BloqueioService service;

    @GetMapping
    public List<BloqueioResponseDTO> listar(@RequestParam(required = false) Long unidadeId,
                                            @RequestParam(required = false) Long barbeiroId) {
        return service.listar(unidadeId, barbeiroId);
    }

    @PostMapping
    public ResponseEntity<BloqueioResponseDTO> criar(@Valid @RequestBody BloqueioRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
