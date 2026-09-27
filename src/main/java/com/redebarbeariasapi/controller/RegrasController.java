package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.espera.ListaEsperaService;
import com.redebarbeariasapi.model.ConfiguracaoRede;
import com.redebarbeariasapi.model.ListaEspera;
import com.redebarbeariasapi.notificacao.RelacionamentoService;
import com.redebarbeariasapi.pagamento.SinalService;
import com.redebarbeariasapi.service.ConfiguracaoRedeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Regras da rede (sinal, espera, aniversario/retorno, privacidade) e a fila de espera pra recepcao. */
@Tag(name = "Regras da rede e lista de espera")
@RestController
@RequestMapping("/api")
public class RegrasController {

    private final ConfiguracaoRedeService configuracao;
    private final ListaEsperaService espera;
    private final SinalService sinal;
    private final RelacionamentoService relacionamento;

    public RegrasController(ConfiguracaoRedeService configuracao, ListaEsperaService espera, SinalService sinal,
                            RelacionamentoService relacionamento) {
        this.configuracao = configuracao;
        this.espera = espera;
        this.sinal = sinal;
        this.relacionamento = relacionamento;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @GetMapping("/configuracoes/rede")
    public Map<String, Object> regras() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("regras", configuracao.atual());
        r.put("gatewayPix", sinal.gateway().configurado() ? sinal.gateway().nome() : null);
        return r;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/configuracoes/rede")
    public ConfiguracaoRede salvar(@RequestBody Map<String, Object> corpo) {
        return configuracao.salvar(corpo);
    }

    @Operation(summary = "Roda agora as mensagens de aniversário e retorno (normalmente saem sozinhas no horário configurado)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/configuracoes/rede/rodar-relacionamento")
    public Map<String, Integer> rodarRelacionamento() {
        return relacionamento.processar(LocalDateTime.now());
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @GetMapping("/lista-espera")
    public List<Map<String, Object>> fila(@RequestParam(required = false) LocalDate de, @RequestParam(required = false) LocalDate ate,
                                          @RequestParam(required = false) Long unidadeId) {
        LocalDate d0 = de == null ? LocalDate.now() : de;
        return espera.listar(d0, ate == null ? d0.plusDays(30) : ate, unidadeId);
    }

    @Operation(summary = "Recepção marca como avisado (mandou pelo WhatsApp) ou tira da fila")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @PatchMapping("/lista-espera/{id}")
    public Map<String, Object> alterar(@PathVariable Long id, @RequestParam ListaEspera.Status status) {
        return espera.alterarStatus(id, status);
    }
}
