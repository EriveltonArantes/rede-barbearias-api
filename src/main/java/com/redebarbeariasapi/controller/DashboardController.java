package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
public class DashboardController {

    private final DashboardService service;

    @Operation(summary = "KPIs do dia/mês, gráficos e alertas (conteúdo muda conforme o papel)")
    @GetMapping("/resumo")
    public Map<String, Object> resumo(@RequestParam(required = false) Long unidadeId) {
        return service.resumo(unidadeId);
    }
}
