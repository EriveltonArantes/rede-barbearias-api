package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.BarbeiroRequestDTO;
import com.redebarbeariasapi.dto.BarbeiroResponseDTO;
import com.redebarbeariasapi.service.BarbeiroService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Barbeiros")
@RestController
@RequestMapping("/api/barbeiros")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
public class BarbeiroController {

    private final BarbeiroService service;

    @GetMapping
    public List<BarbeiroResponseDTO> listar(@RequestParam(required = false) Long unidadeId,
                                            @RequestParam(defaultValue = "false") boolean ativos) {
        return service.listar(unidadeId, ativos);
    }

    @GetMapping("/{id}")
    public BarbeiroResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @PostMapping
    public ResponseEntity<BarbeiroResponseDTO> criar(@Valid @RequestBody BarbeiroRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @PutMapping("/{id}")
    public BarbeiroResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody BarbeiroRequestDTO dto) {
        return service.atualizar(id, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
