package com.redebarbeariasapi.sistema;

import com.redebarbeariasapi.service.AuditoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Saude do sistema, alertas e backup — so o administrador. */
@Tag(name = "Sistema (saúde, alertas e backup)")
@RestController
@RequestMapping("/api/sistema")
@PreAuthorize("hasRole('ADMIN')")
public class SistemaController {

    private final SaudeService saude;
    private final AlertaService alertas;
    private final BackupService backup;
    private final AuditoriaService auditoria;

    public SistemaController(SaudeService saude, AlertaService alertas, BackupService backup, AuditoriaService auditoria) {
        this.saude = saude;
        this.alertas = alertas;
        this.backup = backup;
        this.auditoria = auditoria;
    }

    @Operation(summary = "Painel de saúde: banco, canais, tarefas automáticas, backup, alertas e verificações")
    @GetMapping("/saude")
    public Map<String, Object> painel() {
        return saude.painel();
    }

    @Operation(summary = "Manda um alerta de teste pro Telegram/e-mail do responsável")
    @PostMapping("/alerta-teste")
    public Map<String, Object> testarAlerta() {
        return Map.of("resultado", alertas.testar());
    }

    @Operation(summary = "Baixa o backup agora (.zip com as tabelas em CSV + fotos)")
    @GetMapping("/backup")
    public ResponseEntity<byte[]> baixar() throws Exception {
        BackupService.Resultado b = backup.gerar();
        auditoria.registrar("BAIXAR_BACKUP", "Sistema", null, b.nome() + " (" + b.linhas() + " linhas)");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(b.nome()).build().toString())
                .body(b.zip());
    }

    @Operation(summary = "Gera o backup e manda agora pros destinos configurados (Telegram/e-mail)")
    @PostMapping("/backup/enviar")
    public Map<String, List<String>> enviar() {
        return Map.of("resultado", backup.enviarAgora());
    }
}
