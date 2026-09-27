package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.AgendamentoRepository;
import com.redebarbeariasapi.repository.ClienteRepository;
import com.redebarbeariasapi.repository.CupomRepository;
import com.redebarbeariasapi.service.ConfiguracaoRedeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traz o cliente de volta, sem ninguem precisar lembrar:
 * - aniversario: parabens + cupom pessoal (1x por ano);
 * - retorno: X dias sem vir e sem horario marcado -> "bora voltar?" (com cupom opcional), 1x por visita.
 * So pra quem aceitou receber novidades e nao pediu PARAR. O cupom so e criado se a mensagem saiu.
 */
@Service
public class RelacionamentoService {

    private static final Logger log = LoggerFactory.getLogger(RelacionamentoService.class);
    /** Se o servidor ficou desligado no dia exato, ainda manda nos dias seguintes (sem repetir). */
    private static final int TOLERANCIA_DIAS = 6;

    private final ClienteRepository clientes;
    private final AgendamentoRepository agendamentos;
    private final CupomRepository cupons;
    private final ConfiguracaoRedeService configuracao;
    private final NotificacaoService notificacoes;
    private final MensagemFactory mensagens;

    public RelacionamentoService(ClienteRepository clientes, AgendamentoRepository agendamentos, CupomRepository cupons,
                                 ConfiguracaoRedeService configuracao, NotificacaoService notificacoes, MensagemFactory mensagens) {
        this.clientes = clientes;
        this.agendamentos = agendamentos;
        this.cupons = cupons;
        this.configuracao = configuracao;
        this.notificacoes = notificacoes;
        this.mensagens = mensagens;
    }

    @Scheduled(initialDelay = 240_000, fixedDelay = 1_800_000)
    @Transactional // o agendador chama pelo proxy; processar() aqui dentro e chamada interna
    public void rodada() {
        try {
            Map<String, Integer> r = processar(LocalDateTime.now());
            if (r.values().stream().anyMatch(v -> v > 0)) log.info("Relacionamento: {}", r);
        } catch (Exception e) {
            log.warn("Rodada de aniversário/retorno falhou: {}", e.getMessage());
        }
    }

    @Transactional
    public Map<String, Integer> processar(LocalDateTime agora) {
        ConfiguracaoRede cfg = configuracao.atual();
        int niver = 0, retorno = 0;
        if (agora.getHour() >= cfg.getHoraMensagens() && notificacoes.algumCanalConfigurado()) {
            LocalDate hoje = agora.toLocalDate();
            for (Cliente c : clientes.findAll()) {
                if (!c.isAceitaMarketing() || c.getAnonimizadoEm() != null) continue;
                if (cfg.isAniversarioAtivo() && fazAniversario(c, hoje)) niver += aniversario(c, cfg, hoje);
                if (cfg.isRetornoAtivo()) retorno += retorno(c, cfg, agora);
            }
        }
        Map<String, Integer> r = new LinkedHashMap<>();
        r.put("aniversarios", niver);
        r.put("retornos", retorno);
        return r;
    }

    static boolean fazAniversario(Cliente c, LocalDate hoje) {
        if (c.getDataNascimento() == null) return false;
        MonthDay niver = MonthDay.from(c.getDataNascimento());
        // quem nasceu em 29/02 comemora em 28/02 nos anos que nao sao bissextos
        if (niver.getMonthValue() == 2 && niver.getDayOfMonth() == 29 && !hoje.isLeapYear()) niver = MonthDay.of(2, 28);
        return niver.equals(MonthDay.from(hoje));
    }

    private int aniversario(Cliente c, ConfiguracaoRede cfg, LocalDate hoje) {
        String ref = "aniversario:" + hoje.getYear();
        if (notificacoes.jaEnviado(c, TipoNotificacao.ANIVERSARIO, ref)) return 0;
        Cupom cp = cupom("NIVER" + (hoje.getYear() % 100) + base36(c.getId()), cfg.getAniversarioDesconto(),
                hoje.plusDays(cfg.getAniversarioValidadeDias()), "Aniversário de " + c.getNome());
        Mensagem m = mensagens.aniversario(c, cp);
        if (!notificacoes.alcanca(m)) return 0;
        int ok = notificacoes.enviarParaCliente(c, null, TipoNotificacao.ANIVERSARIO, m, ref);
        if (ok > 0) salvarSeNovo(cp);
        return ok > 0 ? 1 : 0;
    }

    private int retorno(Cliente c, ConfiguracaoRede cfg, LocalDateTime agora) {
        if (c.getUltimaVisita() == null) return 0;
        long dias = ChronoUnit.DAYS.between(c.getUltimaVisita().toLocalDate(), agora.toLocalDate());
        if (dias < cfg.getRetornoDias() || dias > cfg.getRetornoDias() + TOLERANCIA_DIAS) return 0;
        String ref = "retorno:" + c.getUltimaVisita().toLocalDate();
        if (notificacoes.jaEnviado(c, TipoNotificacao.RETORNO, ref)) return 0;
        if (agendamentos.temHorarioFuturo(c.getId(), agora)) return 0;
        Cupom cp = cfg.getRetornoDesconto().signum() <= 0 ? null
                : cupom("VOLTA" + base36(c.getId()) + base36(c.getUltimaVisita().toLocalDate().toEpochDay() % 1296),
                cfg.getRetornoDesconto(), agora.toLocalDate().plusDays(cfg.getRetornoValidadeDias()), "Retorno de " + c.getNome());
        Mensagem m = mensagens.retorno(c, dias, cp);
        if (!notificacoes.alcanca(m)) return 0;
        int ok = notificacoes.enviarParaCliente(c, null, TipoNotificacao.RETORNO, m, ref);
        if (ok > 0 && cp != null) salvarSeNovo(cp);
        return ok > 0 ? 1 : 0;
    }

    /** Cupom pessoal de uso unico. Se ja existe (reprocessamento), reaproveita. */
    private Cupom cupom(String codigo, java.math.BigDecimal percentual, LocalDate validoAte, String descricao) {
        return cupons.findByCodigoIgnoreCase(codigo).orElseGet(() -> {
            Cupom cp = new Cupom();
            cp.setCodigo(codigo);
            cp.setDescricao(descricao.length() > 120 ? descricao.substring(0, 120) : descricao);
            cp.setPercentual(percentual);
            cp.setValidoAte(validoAte);
            cp.setLimiteUsos(1);
            return cp;
        });
    }

    private void salvarSeNovo(Cupom cp) {
        if (cp.getId() == null) cupons.save(cp);
    }

    private static String base36(long n) {
        return Long.toString(n, 36).toUpperCase();
    }
}
