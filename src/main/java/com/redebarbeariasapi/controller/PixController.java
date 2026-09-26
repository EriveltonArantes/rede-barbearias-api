package com.redebarbeariasapi.controller;

import com.redebarbeariasapi.dto.PixRequestDTO;
import com.redebarbeariasapi.dto.PixResponseDTO;
import com.redebarbeariasapi.service.PixService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Pix")
@RestController
@RequestMapping("/api/pix")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE','RECEPCAO','BARBEIRO')")
public class PixController {

    private final PixService service;

    @Operation(summary = "Gera Pix copia-e-cola + QR Code (BR Code do Banco Central) com o valor da cobrança")
    @PostMapping("/gerar")
    public PixResponseDTO gerar(@Valid @RequestBody PixRequestDTO dto) {
        return service.gerar(dto);
    }
}
