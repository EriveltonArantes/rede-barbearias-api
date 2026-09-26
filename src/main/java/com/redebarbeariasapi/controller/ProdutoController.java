package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.MovimentoEstoqueRequestDTO;
import com.redebarbeariasapi.dto.MovimentoEstoqueResponseDTO;
import com.redebarbeariasapi.dto.ProdutoRequestDTO;
import com.redebarbeariasapi.dto.ProdutoResponseDTO;
import com.redebarbeariasapi.service.ProdutoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Produtos e estoque")
@RestController
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
public class ProdutoController {

    private final ProdutoService service;

    @GetMapping
    public List<ProdutoResponseDTO> listar(@RequestParam(required = false) Long unidadeId,
                                           @RequestParam(defaultValue = "false") boolean estoqueBaixo) {
        return service.listar(unidadeId, estoqueBaixo);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> criar(@Valid @RequestBody ProdutoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @PutMapping("/{id}")
    public ProdutoResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody ProdutoRequestDTO dto) {
        return service.atualizar(id, dto);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @PostMapping("/{id}/movimentos")
    public ProdutoResponseDTO movimentar(@PathVariable Long id, @Valid @RequestBody MovimentoEstoqueRequestDTO dto) {
        return service.movimentar(id, dto);
    }

    @GetMapping("/{id}/movimentos")
    public List<MovimentoEstoqueResponseDTO> historico(@PathVariable Long id) {
        return service.historico(id);
    }
}
