package com.redebarbeariasapi.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.redebarbeariasapi.dto.PixRequestDTO;
import com.redebarbeariasapi.dto.PixResponseDTO;
import com.redebarbeariasapi.model.Unidade;
import com.redebarbeariasapi.repository.UnidadeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Map;

/**
 * Pix "copia e cola" (BR Code estatico com valor) seguindo o padrao EMV do Banco Central,
 * com CRC16-CCITT e QR Code gerado aqui mesmo. Funciona em qualquer app de banco sem gateway.
 */
@Service
public class PixService {

    private final UnidadeRepository unidades;
    private final String chavePadrao;
    private final String nomeRecebedor;
    private final String cidade;

    public PixService(UnidadeRepository unidades,
                      @Value("${app.pix.chave}") String chavePadrao,
                      @Value("${app.pix.nome-recebedor}") String nomeRecebedor,
                      @Value("${app.pix.cidade}") String cidade) {
        this.unidades = unidades;
        this.chavePadrao = chavePadrao;
        this.nomeRecebedor = nomeRecebedor;
        this.cidade = cidade;
    }

    public PixResponseDTO gerar(PixRequestDTO dto) {
        String chave = chavePadrao;
        if (dto.unidadeId() != null) {
            chave = unidades.findById(dto.unidadeId()).map(Unidade::getChavePix)
                    .filter(c -> c != null && !c.isBlank()).orElse(chavePadrao);
        }
        String txid = dto.referencia() == null ? "***"
                : dto.referencia().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (txid.isEmpty()) txid = "***";
        if (txid.length() > 25) txid = txid.substring(0, 25);
        BigDecimal valor = dto.valor().setScale(2, RoundingMode.HALF_UP);
        String payload = payload(chave, dto.descricao(), valor, nomeRecebedor, cidade, txid);
        return new PixResponseDTO(payload, qrCode(payload), valor, chave, limpar(nomeRecebedor, 25), txid);
    }

    public static String payload(String chave, String descricao, BigDecimal valor, String nome, String cidade, String txid) {
        String info = campo("00", "br.gov.bcb.pix") + campo("01", chave.trim());
        if (descricao != null && !descricao.isBlank()) info += campo("02", limpar(descricao, 40));
        String semCrc = campo("00", "01")
                + campo("26", info)
                + campo("52", "0000")
                + campo("53", "986")
                + campo("54", valor.toPlainString())
                + campo("58", "BR")
                + campo("59", limpar(nome, 25))
                + campo("60", limpar(cidade, 15))
                + campo("62", campo("05", txid))
                + "6304";
        return semCrc + crc16(semCrc);
    }

    private static String campo(String id, String valor) {
        return id + String.format("%02d", valor.length()) + valor;
    }

    /** Remove acentos e caracteres fora do padrao EMV, em maiusculas, cortado no limite. */
    static String limpar(String texto, int max) {
        String s = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 .,-]", "")
                .toUpperCase().trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** CRC16-CCITT-FALSE (polinomio 0x1021, inicial 0xFFFF), exigido pelo BR Code. */
    public static String crc16(String dados) {
        int crc = 0xFFFF;
        for (byte b : dados.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }

    public static String qrCode(String conteudo) {
        try {
            BitMatrix m = new QRCodeWriter().encode(conteudo, BarcodeFormat.QR_CODE, 360, 360,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(m, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar QR Code", e);
        }
    }
}
