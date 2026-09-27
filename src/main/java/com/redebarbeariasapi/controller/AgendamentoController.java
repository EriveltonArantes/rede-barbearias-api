package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.*;
import com.redebarbeariasapi.model.StatusAgendamento;
import com.redebarbeariasapi.service.AgendamentoService;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "Agenda")
@RestController
@RequestMapping("/api/agendamentos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
public class AgendamentoController {

    private final AgendamentoService service;

    @Operation(summary = "Lista por período (padrão: hoje) com filtros; barbeiro vê só a própria agenda")
    @GetMapping
    public List<AgendamentoResponseDTO> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Long unidadeId,
            @RequestParam(required = false) Long barbeiroId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) StatusAgendamento status) {
        LocalDate inicio = de == null ? LocalDate.now() : de;
        return service.listar(inicio, ate == null ? inicio : ate, unidadeId, barbeiroId, clienteId, status);
    }

    @Operation(summary = "Horários livres de um dia para um serviço")
    @GetMapping("/disponibilidade")
    public List<SlotDTO> disponibilidade(@RequestParam Long unidadeId, @RequestParam Long servicoId,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                         @RequestParam(required = false) Long barbeiroId) {
        return service.disponibilidade(unidadeId, servicoId, data, barbeiroId);
    }

    @GetMapping("/{id}")
    public AgendamentoResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @Operation(summary = "Novo agendamento pela equipe (checa conflito, horário e folgas)")
    @PostMapping
    public ResponseEntity<AgendamentoResponseDTO> criar(@Valid @RequestBody AgendamentoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @Operation(summary = "Reagendar / trocar barbeiro, serviço ou cliente")
    @PutMapping("/{id}")
    public AgendamentoResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody AgendamentoRequestDTO dto) {
        return service.atualizar(id, dto);
    }

    @Operation(summary = "Confirmar, iniciar atendimento, cancelar ou marcar falta")
    @PatchMapping("/{id}/status")
    public AgendamentoResponseDTO status(@PathVariable Long id, @Valid @RequestBody StatusRequestDTO dto) {
        return service.alterarStatus(id, dto);
    }

    @Operation(summary = "Finalizar atendimento: pagamento, comissão, pontos de fidelidade e uso do clube")
    @PostMapping("/{id}/finalizar")
    public AgendamentoResponseDTO finalizar(@PathVariable Long id, @Valid @RequestBody FinalizarRequestDTO dto) {
        return service.finalizar(id, dto);
    }

    @Operation(summary = "Marca que o lembrete de WhatsApp foi enviado")
    @PatchMapping("/{id}/lembrete")
    public AgendamentoResponseDTO lembrete(@PathVariable Long id) {
        return service.marcarLembrete(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Sinal por Pix: RECEBIDO (conferiu no banco), DEVOLVIDO ou RETER")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @PostMapping("/{id}/sinal")
    public AgendamentoResponseDTO sinal(@PathVariable Long id, @RequestParam String acao) {
        return service.sinal(id, acao);
    }

    @Operation(summary = "Horários com sinal numa situação (PENDENTE, A_DEVOLVER...)")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @GetMapping("/sinais")
    public List<AgendamentoResponseDTO> sinais(@RequestParam com.redebarbeariasapi.model.SituacaoSinal situacao,
                                               @RequestParam(required = false) Long unidadeId) {
        return service.sinais(situacao, unidadeId);
    }
}
