package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.NotificacaoResponseDTO;
import com.redebarbeariasapi.model.Agendamento;
import com.redebarbeariasapi.model.ConfiguracaoAtendimento;
import com.redebarbeariasapi.model.TipoNotificacao;
import com.redebarbeariasapi.notificacao.AtendimentoWhatsAppService;
import com.redebarbeariasapi.notificacao.MensagemFactory;
import com.redebarbeariasapi.notificacao.WhatsAppCanal;
import com.redebarbeariasapi.notificacao.NotificacaoService;
import com.redebarbeariasapi.security.Sessao;
import com.redebarbeariasapi.service.AgendamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Notificações automáticas")
@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoController {

    private final NotificacaoService service;
    private final AgendamentoService agenda;
    private final AtendimentoWhatsAppService atendimento;
    private final WhatsAppCanal whatsapp;
    private final String apiUrl;
    private final boolean verifyTokenConfigurado;
    private final boolean appSecretConfigurado;

    public NotificacaoController(NotificacaoService service, AgendamentoService agenda, AtendimentoWhatsAppService atendimento,
                                 WhatsAppCanal whatsapp, @Value("${app.api-url:}") String apiUrl,
                                 @Value("${app.whatsapp.verify-token:}") String verifyToken,
                                 @Value("${app.whatsapp.app-secret:}") String appSecret) {
        this.service = service;
        this.agenda = agenda;
        this.atendimento = atendimento;
        this.whatsapp = whatsapp;
        this.apiUrl = apiUrl.replaceAll("/+$", "");
        this.verifyTokenConfigurado = !verifyToken.isBlank();
        this.appSecretConfigurado = !appSecret.isBlank();
    }

    @Operation(summary = "Canais configurados + modelos de mensagem pra cadastrar no WhatsApp oficial")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @GetMapping("/configuracao")
    public Map<String, Object> configuracao() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("canais", service.statusCanais());
        r.put("modelosWhatsApp", MensagemFactory.MODELOS);
        r.put("botoesWhatsApp", MensagemFactory.BOTOES);
        r.put("categoriasWhatsApp", MensagemFactory.CATEGORIAS);
        r.put("rodapeWhatsApp", MensagemFactory.RODAPE);
        Map<String, Object> horarios = new LinkedHashMap<>();
        horarios.put("horaLembrete", service.horaLembrete());
        horarios.put("lembreteAntesMinutos", service.lembreteAntesMinutos());
        horarios.put("avaliacaoAposMinutos", service.avaliacaoAposMinutos());
        r.put("horarios", horarios);
        Map<String, Object> webhook = new LinkedHashMap<>();
        webhook.put("url", apiUrl.isBlank() ? null : apiUrl + "/api/whatsapp/webhook");
        webhook.put("verifyTokenConfigurado", verifyTokenConfigurado);
        webhook.put("appSecretConfigurado", appSecretConfigurado);
        webhook.put("whatsappConfigurado", whatsapp.configurado());
        webhook.put("pronto", verifyTokenConfigurado && appSecretConfigurado && whatsapp.configurado());
        r.put("webhook", webhook);
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

    // ---------------- Resposta automatica do WhatsApp ----------------

    @Operation(summary = "Configuração da resposta automática do WhatsApp")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @GetMapping("/atendimento")
    public ConfiguracaoAtendimento atendimento() {
        return atendimento.configuracao();
    }

    @Operation(summary = "Altera a mensagem de boas-vindas / liga e desliga a resposta automática")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/atendimento")
    public ConfiguracaoAtendimento salvarAtendimento(@RequestBody Map<String, Object> corpo) {
        return atendimento.salvarConfiguracao(corpo);
    }

    @Operation(summary = "Mostra a resposta que um cliente receberia (não envia nada)")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @PostMapping("/atendimento/simular")
    public Map<String, Object> simular(@RequestBody Map<String, Object> corpo) {
        var r = atendimento.simular(corpo.get("nome") instanceof String n ? n : null,
                corpo.get("telefone") instanceof String t ? t : null,
                corpo.get("texto") instanceof String x ? x : null,
                corpo.get("foraDoHorario") instanceof Boolean b ? b : null, LocalDateTime.now());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resposta", r.resposta());
        m.put("acao", r.acao());
        m.put("motivo", r.motivo());
        return m;
    }

    @Operation(summary = "Quem mandou mensagem no WhatsApp da barbearia")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @GetMapping("/atendimento/conversas")
    public List<Map<String, Object>> conversas() {
        return atendimento.conversasRecentes();
    }
}
