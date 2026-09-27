package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.ClienteFichaDTO;
import com.redebarbeariasapi.dto.ClienteRequestDTO;
import com.redebarbeariasapi.dto.ClienteResponseDTO;
import com.redebarbeariasapi.service.ClienteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Clientes")
@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
public class ClienteController {

    private final ClienteService service;
    private final com.redebarbeariasapi.service.PrivacidadeService privacidade;

    @GetMapping
    public List<ClienteResponseDTO> listar(@RequestParam(required = false) String q) {
        return service.listar(q);
    }

    @GetMapping("/{id}")
    public ClienteResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @GetMapping("/{id}/ficha")
    public ClienteFichaDTO ficha(@PathVariable Long id) {
        return service.ficha(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @PostMapping
    public ResponseEntity<ClienteResponseDTO> criar(@Valid @RequestBody ClienteRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @PutMapping("/{id}")
    public ClienteResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequestDTO dto) {
        return service.atualizar(id, dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    /** LGPD: cliente pediu os dados pelo balcao/WhatsApp. */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @GetMapping("/{id}/dados-pessoais")
    public java.util.Map<String, Object> dadosPessoais(@PathVariable Long id) {
        return privacidade.exportar(id);
    }

    /** LGPD: cliente pediu a exclusao pelo balcao/WhatsApp. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/anonimizar")
    public java.util.Map<String, String> anonimizar(@PathVariable Long id) {
        privacidade.anonimizar(id, "admin " + com.redebarbeariasapi.security.Sessao.username());
        return java.util.Map.of("mensagem", "Dados do cliente anonimizados.");
    }
}
