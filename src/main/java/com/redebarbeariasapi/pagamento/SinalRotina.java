package com.redebarbeariasapi.pagamento;

import com.redebarbeariasapi.model.Agendamento;
import com.redebarbeariasapi.model.SituacaoSinal;
import com.redebarbeariasapi.repository.AgendamentoRepository;
import com.redebarbeariasapi.service.AgendamentoService;
import com.redebarbeariasapi.service.ConfiguracaoRedeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A cada 5 min: pergunta ao gateway pelos sinais pendentes (se o webhook se perdeu) e, quando a
 * rede ligou essa opcao, cancela o horario de quem nao pagou no prazo — libera a cadeira e
 * avisa a lista de espera.
 */
@Component
public class SinalRotina {

    private static final Logger log = LoggerFactory.getLogger(SinalRotina.class);

    private final SinalService sinal;
    private final AgendamentoService agenda;
    private final AgendamentoRepository agendamentos;
    private final ConfiguracaoRedeService configuracao;

    public SinalRotina(SinalService sinal, AgendamentoService agenda, AgendamentoRepository agendamentos,
                       ConfiguracaoRedeService configuracao) {
        this.sinal = sinal;
        this.agenda = agenda;
        this.agendamentos = agendamentos;
        this.configuracao = configuracao;
    }

    @Scheduled(initialDelay = 150_000, fixedDelay = 300_000)
    @Transactional
    public void rodada() {
        try {
            Map<String, Integer> r = processar(LocalDateTime.now());
            if (r.values().stream().anyMatch(v -> v > 0)) log.info("Sinais: {}", r);
        } catch (Exception e) {
            log.warn("Rodada dos sinais falhou: {}", e.getMessage());
        }
    }

    @Transactional
    public Map<String, Integer> processar(LocalDateTime agora) {
        int confirmados = sinal.conferirPendentesNoGateway();
        int cancelados = 0;
        if (configuracao.atual().isSinalCancelarSemPagamento()) {
            for (Agendamento a : agendamentos.findBySinalSituacaoOrderByInicio(SituacaoSinal.PENDENTE)) {
                if (a.getSinalExpiraEm() != null && a.getSinalExpiraEm().isBefore(agora) && !a.getStatus().finalizado()) {
                    agenda.cancelarSemSinal(a);
                    cancelados++;
                }
            }
        }
        Map<String, Integer> r = new LinkedHashMap<>();
        r.put("confirmadosNoGateway", confirmados);
        r.put("canceladosSemSinal", cancelados);
        return r;
    }
}
