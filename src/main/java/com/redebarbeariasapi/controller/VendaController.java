package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.MotivoDTO;
import com.redebarbeariasapi.dto.VendaRequestDTO;
import com.redebarbeariasapi.dto.VendaResponseDTO;
import com.redebarbeariasapi.service.VendaService;
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

@Tag(name = "Vendas (PDV de produtos)")
@RestController
@RequestMapping("/api/vendas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
public class VendaController {

    private final VendaService service;

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @GetMapping
    public List<VendaResponseDTO> listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                         @RequestParam(required = false) Long unidadeId) {
        LocalDate fim = ate == null ? LocalDate.now() : ate;
        return service.listar(de == null ? fim.minusDays(30) : de, fim, unidadeId);
    }

    @PostMapping
    public ResponseEntity<VendaResponseDTO> criar(@Valid @RequestBody VendaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @PostMapping("/{id}/cancelar")
    public VendaResponseDTO cancelar(@PathVariable Long id, @Valid @RequestBody MotivoDTO dto) {
        return service.cancelar(id, dto.motivo());
    }
}
