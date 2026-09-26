package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.ServicoRequestDTO;
import com.redebarbeariasapi.dto.ServicoResponseDTO;
import com.redebarbeariasapi.service.ServicoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Serviços")
@RestController
@RequestMapping("/api/servicos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
public class ServicoController {

    private final ServicoService service;

    @GetMapping
    public List<ServicoResponseDTO> listar(@RequestParam(defaultValue = "false") boolean ativos) {
        return service.listar(ativos);
    }

    @GetMapping("/{id}")
    public ServicoResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ServicoResponseDTO> criar(@Valid @RequestBody ServicoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ServicoResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody ServicoRequestDTO dto) {
        return service.atualizar(id, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
