package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.service.FinanceiroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Financeiro")
@RestController
@RequestMapping("/api/financeiro")
@RequiredArgsConstructor
public class FinanceiroController {

    private final FinanceiroService service;

    @Operation(summary = "DRE simplificado do período: receitas, custos, comissões, despesas e lucro")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    @GetMapping("/resumo")
    public Map<String, Object> resumo(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                      @RequestParam(required = false) Long unidadeId) {
        LocalDate hoje = LocalDate.now();
        return service.resumo(de == null ? hoje.withDayOfMonth(1) : de, ate == null ? hoje : ate, unidadeId);
    }

    @Operation(summary = "Comissão por barbeiro no período (barbeiro vê só a dele)")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','BARBEIRO')")
    @GetMapping("/comissoes")
    public List<Map<String, Object>> comissoes(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                               @RequestParam(required = false) Long unidadeId,
                                               @RequestParam(required = false) Long barbeiroId) {
        LocalDate hoje = LocalDate.now();
        return service.comissoes(de == null ? hoje.withDayOfMonth(1) : de, ate == null ? hoje : ate, unidadeId, barbeiroId);
    }

    @Operation(summary = "Fechamento de caixa do dia")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO')")
    @GetMapping("/caixa")
    public Map<String, Object> caixa(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                     @RequestParam(required = false) Long unidadeId) {
        return service.caixa(data == null ? LocalDate.now() : data, unidadeId);
    }
}
