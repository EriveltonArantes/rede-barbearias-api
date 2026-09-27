package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.*;
import com.redebarbeariasapi.model.Agendamento;
import com.redebarbeariasapi.model.Cliente;
import com.redebarbeariasapi.model.OrigemAgendamento;
import com.redebarbeariasapi.security.Sessao;
import com.redebarbeariasapi.service.AgendamentoService;
import com.redebarbeariasapi.service.ClienteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Area do cliente logado: meus horarios, fidelidade, clube e avaliacoes. */
@Tag(name = "Minha conta (cliente)")
@RestController
@RequestMapping("/api/minha-conta")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENTE')")
public class MinhaContaController {

    private final ClienteService clientes;
    private final AgendamentoService agenda;
    private final com.redebarbeariasapi.service.PrivacidadeService privacidade;

    @GetMapping
    public ClienteFichaDTO ficha() {
        return clientes.ficha(Sessao.atual().clienteId());
    }

    @PutMapping
    public ClienteResponseDTO atualizar(@Valid @RequestBody ClienteRequestDTO dto) {
        return clientes.atualizar(Sessao.atual().clienteId(), dto);
    }

    @GetMapping("/agendamentos")
    @Transactional(readOnly = true)
    public List<AgendamentoPublicoResponseDTO> meusAgendamentos() {
        return agenda.doCliente(Sessao.atual().clienteId()).stream().limit(100).map(agenda::publico).toList();
    }

    @PostMapping("/agendamentos")
    @Transactional
    public ResponseEntity<AgendamentoPublicoResponseDTO> agendar(@Valid @RequestBody AgendamentoPublicoRequestDTO dto) {
        Cliente eu = clientes.obter(Sessao.atual().clienteId());
        Agendamento a = agenda.agendarOnline(dto, eu, OrigemAgendamento.APP_CLIENTE);
        return ResponseEntity.status(HttpStatus.CREATED).body(agenda.publico(a));
    }

    @PostMapping("/agendamentos/{codigo}/cancelar")
    @Transactional
    public AgendamentoPublicoResponseDTO cancelar(@PathVariable String codigo, @RequestBody(required = false) MotivoOpcional dto) {
        Agendamento a = meu(codigo);
        agenda.cancelarPeloCliente(a, dto == null ? null : dto.motivo());
        return agenda.publico(a);
    }

    @PostMapping("/agendamentos/{codigo}/avaliar")
    @Transactional
    public AgendamentoPublicoResponseDTO avaliar(@PathVariable String codigo, @Valid @RequestBody AvaliacaoRequestDTO dto) {
        Agendamento a = meu(codigo);
        agenda.avaliar(a, dto);
        return agenda.publico(a);
    }

    private Agendamento meu(String codigo) {
        Agendamento a = agenda.porCodigo(codigo);
        if (!a.getCliente().getId().equals(Sessao.atual().clienteId())) throw new AccessDeniedException("Não é seu agendamento");
        return a;
    }

    public record MotivoOpcional(String motivo) {}

    /** LGPD: tudo que a barbearia guarda sobre mim (baixa como arquivo). */
    @GetMapping("/meus-dados")
    public ResponseEntity<java.util.Map<String, Object>> meusDados() {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=meus-dados.json")
                .body(privacidade.exportar(Sessao.atual().clienteId()));
    }

    /** LGPD: exclusao dos dados. Pede a palavra EXCLUIR pra nao acontecer por engano. */
    @PostMapping("/excluir-conta")
    public java.util.Map<String, String> excluirConta(@RequestBody java.util.Map<String, String> corpo) {
        if (!"EXCLUIR".equalsIgnoreCase(String.valueOf(corpo.get("confirmacao")).trim())) {
            throw new com.redebarbeariasapi.exception.ValidacaoException("Digite EXCLUIR pra confirmar.");
        }
        privacidade.anonimizar(Sessao.atual().clienteId(), "o próprio cliente");
        return java.util.Map.of("mensagem", "Seus dados foram excluídos. Obrigado por ter sido nosso cliente 💈");
    }
}
