package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.UnidadeRequestDTO;
import com.redebarbeariasapi.dto.UnidadeResponseDTO;
import com.redebarbeariasapi.service.UnidadeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Unidades")
@RestController
@RequestMapping("/api/unidades")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
public class UnidadeController {

    private final UnidadeService service;

    @GetMapping
    public List<UnidadeResponseDTO> listar(@RequestParam(defaultValue = "false") boolean ativas) {
        return service.listar(ativas);
    }

    @GetMapping("/{id}")
    public UnidadeResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<UnidadeResponseDTO> criar(@Valid @RequestBody UnidadeRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public UnidadeResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody UnidadeRequestDTO dto) {
        return service.atualizar(id, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
