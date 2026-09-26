package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.*;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.model.Agendamento;
import com.redebarbeariasapi.model.OrigemAgendamento;
import com.redebarbeariasapi.security.RateLimiter;
import com.redebarbeariasapi.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Tudo que o site e o agendamento online usam sem login. Nunca expoe dados de outros clientes. */
@Tag(name = "Público (site e agendamento online)")
@RestController
@RequestMapping("/api/publico")
@RequiredArgsConstructor
public class PublicoController {

    private final UnidadeService unidades;
    private final ServicoService servicos;
    private final BarbeiroService barbeiros;
    private final AgendamentoService agenda;
    private final ClubeService clube;
    private final CupomService cupons;
    private final AvaliacaoService avaliacoes;
    private final RateLimiter rateLimiter;

    @GetMapping("/unidades")
    public List<UnidadeResponseDTO> unidades() {
        return unidades.listar(true).stream()
                .map(u -> new UnidadeResponseDTO(u.id(), u.nome(), u.endereco(), u.bairro(), u.cidade(), u.telefone(),
                        u.whatsapp(), u.email(), u.fotoUrl(), u.horaAbertura(), u.horaFechamento(),
                        u.diasFuncionamento(), null, u.ativa(), u.totalBarbeiros()))
                .toList();
    }

    @GetMapping("/servicos")
    public List<ServicoResponseDTO> servicos() {
        return servicos.listar(true);
    }

    @GetMapping("/barbeiros")
    public List<BarbeiroPublicoDTO> barbeiros(@RequestParam(required = false) Long unidadeId) {
        return barbeiros.listarPublico(unidadeId);
    }

    @GetMapping("/planos")
    public List<PlanoResponseDTO> planos() {
        return clube.listarPlanos(true);
    }

    @GetMapping("/avaliacoes")
    public List<AvaliacaoResponseDTO> avaliacoes() {
        return avaliacoes.destaques();
    }

    @GetMapping("/cupons/{codigo}")
    public CupomValidacaoDTO validarCupom(@PathVariable String codigo, @RequestParam(required = false) BigDecimal valor,
                                          HttpServletRequest req) {
        rateLimiter.verificar("cupom", req);
        return cupons.validar(codigo, valor);
    }

    @Operation(summary = "Horários livres pro agendamento online")
    @GetMapping("/disponibilidade")
    public List<SlotDTO> disponibilidade(@RequestParam Long unidadeId, @RequestParam Long servicoId,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                         @RequestParam(required = false) Long barbeiroId) {
        return agenda.disponibilidade(unidadeId, servicoId, data, barbeiroId);
    }

    @Operation(summary = "Cliente agenda sozinho; devolve o código pra consultar/cancelar depois")
    @PostMapping("/agendamentos")
    @Transactional
    public ResponseEntity<AgendamentoPublicoResponseDTO> agendar(@Valid @RequestBody AgendamentoPublicoRequestDTO dto,
                                                                HttpServletRequest req) {
        rateLimiter.verificar("agendar", req);
        Agendamento a = agenda.agendarOnline(dto, null, OrigemAgendamento.ONLINE);
        return ResponseEntity.status(HttpStatus.CREATED).body(agenda.publico(a));
    }

    @GetMapping("/agendamentos/{codigo}")
    @Transactional(readOnly = true)
    public AgendamentoPublicoResponseDTO consultar(@PathVariable String codigo, HttpServletRequest req) {
        rateLimiter.verificar("consultar", req);
        return agenda.publico(agenda.porCodigo(codigo));
    }

    @PostMapping("/agendamentos/{codigo}/confirmar")
    @Transactional
    public AgendamentoPublicoResponseDTO confirmar(@PathVariable String codigo, HttpServletRequest req) {
        rateLimiter.verificar("confirmar", req);
        Agendamento a = agenda.porCodigo(codigo);
        agenda.confirmarPeloCliente(a, "site");
        return agenda.publico(a);
    }

    @PostMapping("/agendamentos/{codigo}/cancelar")
    @Transactional
    public AgendamentoPublicoResponseDTO cancelar(@PathVariable String codigo, @Valid @RequestBody CancelarPublicoDTO dto,
                                                  HttpServletRequest req) {
        rateLimiter.verificar("cancelar", req);
        Agendamento a = agenda.porCodigo(codigo);
        String informado = Textos.telefone(dto.telefone());
        if (!informado.equals(a.getCliente().getTelefone())) {
            throw new ValidacaoException("O telefone não confere com o do agendamento.");
        }
        agenda.cancelarPeloCliente(a, dto.motivo());
        return agenda.publico(a);
    }

    @PostMapping("/agendamentos/{codigo}/avaliar")
    @Transactional
    public AgendamentoPublicoResponseDTO avaliar(@PathVariable String codigo, @Valid @RequestBody AvaliacaoRequestDTO dto,
                                                 HttpServletRequest req) {
        rateLimiter.verificar("avaliar", req);
        Agendamento a = agenda.porCodigo(codigo);
        agenda.avaliar(a, dto);
        return agenda.publico(a);
    }
}
