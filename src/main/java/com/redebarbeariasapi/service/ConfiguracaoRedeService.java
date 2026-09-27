package com.redebarbeariasapi.service;

import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.model.ConfiguracaoRede;
import com.redebarbeariasapi.repository.ConfiguracaoRedeRepository;
import com.redebarbeariasapi.security.Sessao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Le e grava as regras da rede (sinal, lista de espera, aniversario/retorno, privacidade). */
@Service
public class ConfiguracaoRedeService {

    private final ConfiguracaoRedeRepository repo;
    private final AuditoriaService auditoria;

    public ConfiguracaoRedeService(ConfiguracaoRedeRepository repo, AuditoriaService auditoria) {
        this.repo = repo;
        this.auditoria = auditoria;
    }

    @Transactional
    public ConfiguracaoRede atual() {
        return repo.findById(1L).orElseGet(() -> repo.save(new ConfiguracaoRede()));
    }

    /** Aplica so os campos enviados (PUT parcial). */
    @Transactional
    public ConfiguracaoRede salvar(Map<String, Object> c) {
        ConfiguracaoRede r = atual();
        if (c.get("sinalAtivo") instanceof Boolean b) r.setSinalAtivo(b);
        if (c.containsKey("sinalValor")) r.setSinalValor(valor(c.get("sinalValor"), "Valor do sinal", new BigDecimal("1"), new BigDecimal("500")));
        if (c.get("sinalFimDeSemana") instanceof Boolean b) r.setSinalFimDeSemana(b);
        if (c.get("sinalQuemFaltou") instanceof Boolean b) r.setSinalQuemFaltou(b);
        if (c.get("sinalSempre") instanceof Boolean b) r.setSinalSempre(b);
        if (c.containsKey("sinalPrazoMinutos")) r.setSinalPrazoMinutos(inteiro(c.get("sinalPrazoMinutos"), "Prazo pra pagar o sinal", 10, 1440));
        if (c.get("sinalCancelarSemPagamento") instanceof Boolean b) r.setSinalCancelarSemPagamento(b);
        if (c.containsKey("sinalDevolucaoHoras")) r.setSinalDevolucaoHoras(inteiro(c.get("sinalDevolucaoHoras"), "Antecedência pra devolver o sinal", 0, 168));

        if (c.get("esperaAtiva") instanceof Boolean b) r.setEsperaAtiva(b);
        if (c.containsKey("esperaAvisarQuantos")) r.setEsperaAvisarQuantos(inteiro(c.get("esperaAvisarQuantos"), "Quantos avisar da lista de espera", 1, 20));

        if (c.get("aniversarioAtivo") instanceof Boolean b) r.setAniversarioAtivo(b);
        if (c.containsKey("aniversarioDesconto")) r.setAniversarioDesconto(valor(c.get("aniversarioDesconto"), "Desconto de aniversário (%)", BigDecimal.ONE, new BigDecimal("100")));
        if (c.containsKey("aniversarioValidadeDias")) r.setAniversarioValidadeDias(inteiro(c.get("aniversarioValidadeDias"), "Validade do cupom de aniversário", 1, 90));
        if (c.get("retornoAtivo") instanceof Boolean b) r.setRetornoAtivo(b);
        if (c.containsKey("retornoDias")) r.setRetornoDias(inteiro(c.get("retornoDias"), "Dias sem vir", 7, 365));
        if (c.containsKey("retornoDesconto")) r.setRetornoDesconto(valor(c.get("retornoDesconto"), "Desconto de retorno (%)", BigDecimal.ZERO, new BigDecimal("100")));
        if (c.containsKey("retornoValidadeDias")) r.setRetornoValidadeDias(inteiro(c.get("retornoValidadeDias"), "Validade do cupom de retorno", 1, 90));
        if (c.containsKey("horaMensagens")) r.setHoraMensagens(inteiro(c.get("horaMensagens"), "Hora das mensagens", 7, 21));

        if (c.containsKey("razaoSocial")) r.setRazaoSocial(texto(c.get("razaoSocial"), 150));
        if (c.containsKey("cnpj")) r.setCnpj(texto(c.get("cnpj"), 20));
        if (c.containsKey("emailPrivacidade")) {
            String e = texto(c.get("emailPrivacidade"), 150);
            if (e != null && !e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ValidacaoException("E-mail de privacidade inválido.");
            r.setEmailPrivacidade(e);
        }
        r.setAtualizadoEm(LocalDateTime.now());
        r.setAtualizadoPor(Sessao.username());
        auditoria.registrar("CONFIGURAR", "ConfiguracaoRede", 1L, "campos: " + String.join(", ", c.keySet()));
        return r;
    }

    /** O que o site mostra sem login (regras do sinal, se tem lista de espera, contato de privacidade). */
    @Transactional
    public Map<String, Object> publico() {
        ConfiguracaoRede r = atual();
        Map<String, Object> sinal = new LinkedHashMap<>();
        sinal.put("ativo", r.isSinalAtivo());
        sinal.put("valor", r.getSinalValor());
        sinal.put("fimDeSemana", r.isSinalFimDeSemana());
        sinal.put("quemFaltou", r.isSinalQuemFaltou());
        sinal.put("sempre", r.isSinalSempre());
        sinal.put("prazoMinutos", r.getSinalPrazoMinutos());
        sinal.put("devolucaoHoras", r.getSinalDevolucaoHoras());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sinal", sinal);
        m.put("esperaAtiva", r.isEsperaAtiva());
        m.put("razaoSocial", r.getRazaoSocial());
        m.put("cnpj", r.getCnpj());
        m.put("emailPrivacidade", r.getEmailPrivacidade());
        return m;
    }

    private static BigDecimal valor(Object o, String campo, BigDecimal min, BigDecimal max) {
        BigDecimal v;
        try {
            v = new BigDecimal(String.valueOf(o).replace(",", "."));
        } catch (NumberFormatException e) {
            throw new ValidacaoException(campo + ": informe um número.");
        }
        if (v.compareTo(min) < 0 || v.compareTo(max) > 0) throw new ValidacaoException(campo + " deve ficar entre " + min + " e " + max + ".");
        return Textos.dinheiro(v);
    }

    private static int inteiro(Object o, String campo, int min, int max) {
        int v;
        try {
            v = o instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            throw new ValidacaoException(campo + ": informe um número inteiro.");
        }
        if (v < min || v > max) throw new ValidacaoException(campo + " deve ficar entre " + min + " e " + max + ".");
        return v;
    }

    private static String texto(Object o, int max) {
        if (o == null) return null;
        String s = String.valueOf(o).strip();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
