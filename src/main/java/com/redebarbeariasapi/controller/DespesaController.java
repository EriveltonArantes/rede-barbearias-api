package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.DespesaRequestDTO;
import com.redebarbeariasapi.dto.DespesaResponseDTO;
import com.redebarbeariasapi.service.DespesaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Despesas (contas a pagar)")
@RestController
@RequestMapping("/api/despesas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DespesaController {

    private final DespesaService service;

    @GetMapping
    public List<DespesaResponseDTO> listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                           @RequestParam(required = false) Long unidadeId) {
        LocalDate hoje = LocalDate.now();
        return service.listar(de == null ? hoje.withDayOfMonth(1) : de, ate == null ? hoje.withDayOfMonth(hoje.lengthOfMonth()) : ate, unidadeId);
    }

    @PostMapping
    public ResponseEntity<DespesaResponseDTO> criar(@Valid @RequestBody DespesaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @PutMapping("/{id}")
    public DespesaResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody DespesaRequestDTO dto) {
        return service.atualizar(id, dto);
    }

    @PostMapping("/{id}/pagar")
    public DespesaResponseDTO pagar(@PathVariable Long id) {
        return service.pagar(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
