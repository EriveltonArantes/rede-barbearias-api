package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.*;
import com.redebarbeariasapi.service.ClubeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Clube de assinatura")
@RestController
@RequestMapping("/api/clube")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
public class ClubeController {

    private final ClubeService service;

    @GetMapping("/planos")
    public List<PlanoResponseDTO> planos(@RequestParam(defaultValue = "false") boolean ativos) {
        return service.listarPlanos(ativos);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/planos")
    public ResponseEntity<PlanoResponseDTO> criarPlano(@Valid @RequestBody PlanoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarPlano(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/planos/{id}")
    public PlanoResponseDTO atualizarPlano(@PathVariable Long id, @Valid @RequestBody PlanoRequestDTO dto) {
        return service.atualizarPlano(id, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/planos/{id}")
    public ResponseEntity<Void> excluirPlano(@PathVariable Long id) {
        service.excluirPlano(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assinaturas")
    public List<AssinaturaResponseDTO> assinaturas() {
        return service.listarAssinaturas();
    }

    @PostMapping("/assinaturas")
    public ResponseEntity<AssinaturaResponseDTO> assinar(@Valid @RequestBody AssinaturaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.assinar(dto));
    }

    @PostMapping("/assinaturas/{id}/renovar")
    public AssinaturaResponseDTO renovar(@PathVariable Long id, @Valid @RequestBody RenovacaoRequestDTO dto) {
        return service.renovar(id, dto);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @PostMapping("/assinaturas/{id}/cancelar")
    public AssinaturaResponseDTO cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }
}
