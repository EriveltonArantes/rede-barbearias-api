package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.UsuarioRequestDTO;
import com.redebarbeariasapi.dto.UsuarioResponseDTO;
import com.redebarbeariasapi.model.AuditLog;
import com.redebarbeariasapi.service.AuditoriaService;
import com.redebarbeariasapi.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Usuários e auditoria")
@RestController
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService service;
    private final AuditoriaService auditoria;
    private final com.redebarbeariasapi.service.RecuperacaoSenhaService recuperacao;
    private final com.redebarbeariasapi.repository.UsuarioRepository usuarios;

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @GetMapping("/api/usuarios")
    public List<UsuarioResponseDTO> listar() {
        return service.listar();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @PostMapping("/api/usuarios")
    public ResponseEntity<UsuarioResponseDTO> criar(@Valid @RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @PutMapping("/api/usuarios/{id}")
    public UsuarioResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequestDTO dto) {
        return service.atualizar(id, dto);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @DeleteMapping("/api/usuarios/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/auditoria")
    public List<AuditLog> auditoria() {
        return auditoria.recentes();
    }

    @Operation(summary = "Pedidos de \"esqueci minha senha\" das últimas 24h que ninguém concluiu")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @GetMapping("/api/usuarios/recuperacoes-pendentes")
    public List<java.util.Map<String, Object>> recuperacoesPendentes() {
        return recuperacao.pendentes();
    }

    @Operation(summary = "Gera um código de nova senha (vale 24h) pra equipe mandar pelo WhatsApp da barbearia")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @PostMapping("/api/usuarios/{id}/codigo-senha")
    public java.util.Map<String, Object> gerarCodigo(@PathVariable Long id) {
        return recuperacao.gerarPelaEquipe(id);
    }

    @Operation(summary = "Mesmo que o anterior, a partir da ficha do cliente (balcão)")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @PostMapping("/api/usuarios/por-cliente/{clienteId}/codigo-senha")
    public java.util.Map<String, Object> gerarCodigoCliente(@PathVariable Long clienteId) {
        Long id = usuarios.findByClienteId(clienteId)
                .orElseThrow(() -> new com.redebarbeariasapi.exception.BusinessException("Esse cliente ainda não criou conta no site — ele agenda só com o celular, sem senha."))
                .getId();
        return recuperacao.gerarPelaEquipe(id);
    }
}
