package com.redebarbeariasapi.service;

import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.model.Arquivo;
import com.redebarbeariasapi.repository.ArquivoRepository;
import com.redebarbeariasapi.security.Sessao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class ArquivoService {

    private static final Set<String> TIPOS = Set.of("image/png", "image/jpeg", "image/webp", "image/gif", "application/pdf");
    private static final long MAXIMO = 5L * 1024 * 1024;

    private final ArquivoRepository repo;

    public Arquivo salvar(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ValidacaoException("Envie um arquivo.");
        if (file.getSize() > MAXIMO) throw new ValidacaoException("Arquivo grande demais (máximo 5 MB).");
        String tipo = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!TIPOS.contains(tipo)) throw new ValidacaoException("Formato não aceito. Use PNG, JPG, WEBP, GIF ou PDF.");
        try {
            byte[] dados = file.getBytes();
            if (!assinaturaConfere(tipo, dados)) throw new ValidacaoException("O conteúdo do arquivo não bate com o formato informado.");
            Arquivo a = new Arquivo();
            String nome = file.getOriginalFilename() == null ? "arquivo" : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_");
            a.setNome(nome.length() > 120 ? nome.substring(nome.length() - 120) : nome);
            a.setContentType(tipo);
            a.setTamanho(file.getSize());
            a.setDados(dados);
            a.setEnviadoPor(Sessao.username());
            return repo.save(a);
        } catch (IOException e) {
            throw new ValidacaoException("Não consegui ler o arquivo enviado.");
        }
    }

    @Transactional(readOnly = true)
    public Arquivo obter(Long id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Arquivo", id));
    }

    /** Confere os "magic bytes" pra nao aceitar um executavel renomeado pra .png. */
    private static boolean assinaturaConfere(String tipo, byte[] d) {
        if (d.length < 4) return false;
        return switch (tipo) {
            case "image/png" -> (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
            case "image/jpeg" -> (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8;
            case "image/gif" -> d[0] == 'G' && d[1] == 'I' && d[2] == 'F';
            case "image/webp" -> d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[8] == 'W' && d[9] == 'E';
            case "application/pdf" -> d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F';
            default -> false;
        };
    }
}
