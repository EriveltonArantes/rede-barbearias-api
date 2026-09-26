package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.model.Arquivo;
import com.redebarbeariasapi.service.ArquivoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Tag(name = "Arquivos (fotos e comprovantes)")
@RestController
@RequiredArgsConstructor
public class ArquivoController {

    private final ArquivoService service;

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
    @PostMapping(value = "/api/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("arquivo") MultipartFile arquivo) {
        Arquivo a = service.salvar(arquivo);
        return Map.of("id", a.getId(), "url", "/api/arquivos/" + a.getId(), "nome", a.getNome());
    }

    @GetMapping("/api/arquivos/{id}")
    public ResponseEntity<byte[]> baixar(@PathVariable Long id) {
        Arquivo a = service.obter(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + a.getNome() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(a.getDados());
    }
}
