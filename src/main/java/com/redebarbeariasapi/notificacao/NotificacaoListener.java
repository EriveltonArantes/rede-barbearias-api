package com.redebarbeariasapi.notificacao;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Liga a agenda as mensagens: eventos viram envio em segundo plano (o cliente nao espera
 * o SMTP pra ver a confirmacao na tela) e uma rodada a cada 5 min cuida dos lembretes e da avaliacao.
 */
@Component
@RequiredArgsConstructor
public class NotificacaoListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoListener.class);
    private final NotificacaoService service;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoMudarAgendamento(AgendamentoEvento e) {
        try {
            service.enviar(e.agendamentoId(), e.tipo());
        } catch (Exception ex) {
            log.warn("Notificacao {} do agendamento {} nao enviada: {}", e.tipo(), e.agendamentoId(), ex.getMessage());
        }
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 300_000)
    public void rodadaAutomatica() {
        try {
            Map<String, Integer> r = service.processarAutomaticos(LocalDateTime.now());
            if (r.values().stream().anyMatch(v -> v > 0)) log.info("Notificacoes automaticas: {}", r);
        } catch (Exception ex) {
            log.warn("Rodada de notificacoes falhou: {}", ex.getMessage());
        }
    }
}
