package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.NotificacaoResponseDTO;
import com.redebarbeariasapi.model.Agendamento;
import com.redebarbeariasapi.model.TipoNotificacao;
import com.redebarbeariasapi.notificacao.MensagemFactory;
import com.redebarbeariasapi.notificacao.NotificacaoService;
import com.redebarbeariasapi.security.Sessao;
import com.redebarbeariasapi.service.AgendamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Notificações automáticas")
@RestController
@RequestMapping("/api/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoService service;
    private final AgendamentoService agenda;

    @Operation(summary = "Canais configurados + modelos de mensagem pra cadastrar no WhatsApp oficial")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @GetMapping("/configuracao")
    public Map<String, Object> configuracao() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("canais", service.statusCanais());
        r.put("modelosWhatsApp", MensagemFactory.MODELOS);
        return r;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @GetMapping
    public List<NotificacaoResponseDTO> recentes() {
        return service.recentes();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
    @GetMapping("/agendamento/{id}")
    public List<NotificacaoResponseDTO> doAgendamento(@PathVariable Long id) {
        agenda.buscar(id); // valida acesso (unidade/barbeiro)
        return service.doAgendamento(id);
    }

    @Operation(summary = "Reenviar confirmação/lembrete de um agendamento agora")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @PostMapping("/agendamento/{id}/reenviar")
    public Map<String, Object> reenviar(@PathVariable Long id, @RequestParam(defaultValue = "CONFIRMACAO") TipoNotificacao tipo) {
        Agendamento a = agenda.obter(id);
        Sessao.exigirUnidade(a.getUnidade().getId());
        int enviados = service.enviar(id, tipo, true);
        return Map.of("enviados", enviados, "historico", service.doAgendamento(id));
    }

    @Operation(summary = "Dispara uma mensagem de teste pro e-mail/telefone informado")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/teste")
    public Map<String, Object> teste(@RequestBody Map<String, String> corpo) {
        return Map.of("resultado", service.teste(corpo.get("email"), corpo.get("telefone")));
    }
}
