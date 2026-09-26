package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.CupomRequestDTO;
import com.redebarbeariasapi.dto.CupomResponseDTO;
import com.redebarbeariasapi.service.CupomService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Cupons de desconto")
@RestController
@RequestMapping("/api/cupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CupomController {

    private final CupomService service;

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @GetMapping
    public List<CupomResponseDTO> listar() {
        return service.listar();
    }

    @PostMapping
    public ResponseEntity<CupomResponseDTO> criar(@Valid @RequestBody CupomRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @PutMapping("/{id}")
    public CupomResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody CupomRequestDTO dto) {
        return service.atualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
