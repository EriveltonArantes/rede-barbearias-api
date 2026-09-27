package com.redebarbeariasapi.pagamento;

import com.redebarbeariasapi.dto.PixRequestDTO;
import com.redebarbeariasapi.dto.PixResponseDTO;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.notificacao.AgendamentoEvento;
import com.redebarbeariasapi.repository.AgendamentoRepository;
import com.redebarbeariasapi.service.AuditoriaService;
import com.redebarbeariasapi.service.ConfiguracaoRedeService;
import com.redebarbeariasapi.service.PixService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Sinal por Pix pra reduzir falta: vale so pro agendamento online (site/app), nos casos que a rede
 * escolher (fim de semana, quem ja faltou, ou sempre). E descontado no dia; cancelando com
 * antecedencia volta pro cliente; falta ou cancelamento em cima da hora fica com a barbearia.
 */
@Service
@Transactional
public class SinalService {

    private static final Logger log = LoggerFactory.getLogger(SinalService.class);
    private static final Set<OrigemAgendamento> ONLINE = Set.of(OrigemAgendamento.ONLINE, OrigemAgendamento.APP_CLIENTE);
    private static final int JANELA_FALTAS_DIAS = 180;

    private final ConfiguracaoRedeService configuracao;
    private final AgendamentoRepository agendamentos;
    private final PixService pix;
    private final GatewayPix gateway;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;

    public SinalService(ConfiguracaoRedeService configuracao, AgendamentoRepository agendamentos, PixService pix,
                        GatewayPix gateway, AuditoriaService auditoria, ApplicationEventPublisher eventos) {
        this.configuracao = configuracao;
        this.agendamentos = agendamentos;
        this.pix = pix;
        this.gateway = gateway;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    /** Por que esse agendamento pede sinal (texto pro cliente), ou vazio se nao pede. */
    public Optional<String> motivo(Cliente c, LocalDateTime inicio, OrigemAgendamento origem) {
        ConfiguracaoRede r = configuracao.atual();
        if (!r.isSinalAtivo() || !ONLINE.contains(origem)) return Optional.empty();
        if (r.isSinalSempre()) return Optional.of("todo agendamento online");
        DayOfWeek d = inicio.getDayOfWeek();
        if (r.isSinalFimDeSemana() && (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY)) return Optional.of("horário de fim de semana");
        if (r.isSinalQuemFaltou() && c.getId() != null && agendamentos.countByClienteIdAndStatusAndInicioAfter(
                c.getId(), StatusAgendamento.NAO_COMPARECEU, LocalDateTime.now().minusDays(JANELA_FALTAS_DIAS)) > 0) {
            return Optional.of("falta sem aviso nos últimos 6 meses");
        }
        return Optional.empty();
    }

    /** Chamado logo depois de criar o agendamento online. */
    public void aplicar(Agendamento a) {
        Optional<String> motivo = motivo(a.getCliente(), a.getInicio(), a.getOrigem());
        if (motivo.isEmpty()) return;
        ConfiguracaoRede r = configuracao.atual();
        BigDecimal valor = r.getSinalValor().min(a.valorAPagar());
        if (valor.signum() <= 0) return; // cupom zerou o valor: nao tem o que adiantar

        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime expira = agora.plusMinutes(r.getSinalPrazoMinutos());
        if (expira.isAfter(a.getInicio())) expira = a.getInicio();
        a.setSinalValor(valor);
        a.setSinalSituacao(SituacaoSinal.PENDENTE);
        a.setSinalExpiraEm(expira);
        if (gateway.configurado()) {
            try {
                GatewayPix.Cobranca cob = gateway.criar(valor, "Sinal " + a.getServico().getNome() + " " + a.getCodigo(),
                        a.getCliente().getEmail(), a.getCodigo(), expira);
                a.setSinalGatewayId(cob.id());
                a.setSinalPixCopiaECola(cob.copiaECola());
            } catch (Exception e) {
                // gateway fora: cai no Pix estatico (a recepcao confirma), mas o agendamento nao se perde
                log.warn("Gateway Pix falhou pro agendamento {}: {} — usando Pix estático", a.getCodigo(), e.getMessage());
            }
        }
        auditoria.registrar("SINAL", "Agendamento", a.getId(), "Sinal de R$ " + valor + " pedido (" + motivo.get() + ")");
        eventos.publishEvent(new AgendamentoEvento(a.getId(), TipoNotificacao.SINAL_PENDENTE));
    }

    /** Pix pra pagar o sinal: o do gateway (confirma sozinho) ou o estatico da unidade. */
    @Transactional(readOnly = true)
    public PixResponseDTO pix(Agendamento a) {
        if (a.getSinalSituacao() != SituacaoSinal.PENDENTE) throw new BusinessException("Esse horário não tem sinal pendente.");
        if (a.getSinalPixCopiaECola() != null) {
            return new PixResponseDTO(a.getSinalPixCopiaECola(), PixService.qrCode(a.getSinalPixCopiaECola()), a.getSinalValor(),
                    null, gateway.nome(), a.getCodigo());
        }
        return pix.gerar(new PixRequestDTO(a.getSinalValor(), a.getUnidade().getId(), "Sinal " + a.getCodigo(), "SINAL" + a.getCodigo()));
    }

    public boolean confirmacaoAutomatica(Agendamento a) {
        return a.getSinalGatewayId() != null && gateway.configurado();
    }

    /** Pix caiu (gateway ou recepcao conferiu no banco). */
    public void confirmar(Agendamento a, String quem) {
        if (a.getSinalSituacao() == SituacaoSinal.PAGO) return;
        if (a.getSinalSituacao() != SituacaoSinal.PENDENTE && a.getSinalSituacao() != SituacaoSinal.EXPIRADO) {
            throw new BusinessException("Esse horário não tem sinal esperando pagamento.");
        }
        a.setSinalSituacao(SituacaoSinal.PAGO);
        a.setSinalPagoEm(LocalDateTime.now());
        auditoria.registrar("SINAL_PAGO", "Agendamento", a.getId(), "R$ " + a.getSinalValor() + " (" + quem + ")");
    }

    /**
     * Horario cancelado. Pela barbearia: devolve sempre. Pelo cliente: devolve se cancelou com a
     * antecedencia minima, senao fica retido. Nao pago: expira.
     */
    public void aoCancelar(Agendamento a, boolean peloCliente) {
        if (a.getSinalSituacao() == null) return;
        if (a.getSinalSituacao() == SituacaoSinal.PENDENTE) {
            a.setSinalSituacao(SituacaoSinal.EXPIRADO);
            return;
        }
        if (a.getSinalSituacao() != SituacaoSinal.PAGO) return;
        long horas = Duration.between(LocalDateTime.now(), a.getInicio()).toHours();
        boolean devolve = !peloCliente || horas >= configuracao.atual().getSinalDevolucaoHoras();
        a.setSinalSituacao(devolve ? SituacaoSinal.A_DEVOLVER : SituacaoSinal.RETIDO);
        auditoria.registrar("SINAL", "Agendamento", a.getId(), devolve ? "Sinal a devolver" : "Sinal retido (cancelamento em cima da hora)");
    }

    public void aoFaltar(Agendamento a) {
        if (a.getSinalSituacao() == SituacaoSinal.PAGO) {
            a.setSinalSituacao(SituacaoSinal.RETIDO);
            auditoria.registrar("SINAL", "Agendamento", a.getId(), "Sinal retido (falta)");
        } else if (a.getSinalSituacao() == SituacaoSinal.PENDENTE) {
            a.setSinalSituacao(SituacaoSinal.EXPIRADO);
        }
    }

    /** Atendimento pago: sinal vira parte do pagamento; se foi pelo clube/cortesia, o sinal volta pro cliente. */
    public void aoFinalizar(Agendamento a, boolean abatido) {
        if (a.getSinalSituacao() == SituacaoSinal.PAGO) a.setSinalSituacao(abatido ? SituacaoSinal.ABATIDO : SituacaoSinal.A_DEVOLVER);
        else if (a.getSinalSituacao() == SituacaoSinal.PENDENTE) a.setSinalSituacao(SituacaoSinal.EXPIRADO);
    }

    public void marcarDevolvido(Agendamento a) {
        if (a.getSinalSituacao() != SituacaoSinal.A_DEVOLVER) throw new BusinessException("Esse sinal não está marcado pra devolver.");
        a.setSinalSituacao(SituacaoSinal.DEVOLVIDO);
        auditoria.registrar("SINAL", "Agendamento", a.getId(), "Sinal devolvido ao cliente");
    }

    /** A recepcao decide ficar com um sinal que estava pra devolver (ex.: cliente aceitou virar credito). */
    public void reter(Agendamento a) {
        if (a.getSinalSituacao() != SituacaoSinal.A_DEVOLVER) throw new BusinessException("Esse sinal não está marcado pra devolver.");
        a.setSinalSituacao(SituacaoSinal.RETIDO);
        auditoria.registrar("SINAL", "Agendamento", a.getId(), "Sinal retido pela equipe");
    }

    /** Webhook do gateway: consulta o pagamento no proprio gateway antes de acreditar em qualquer coisa. */
    public boolean aoNotificarGateway(String pagamentoId) {
        if (!gateway.configurado() || pagamentoId == null || pagamentoId.isBlank()) return false;
        GatewayPix.Situacao s = gateway.consultar(pagamentoId);
        if (!s.aprovado() || s.referencia() == null) return false;
        return agendamentos.findByCodigo(s.referencia())
                .filter(a -> pagamentoId.equals(a.getSinalGatewayId()))
                .filter(a -> a.getSinalSituacao() == SituacaoSinal.PENDENTE || a.getSinalSituacao() == SituacaoSinal.EXPIRADO)
                .map(a -> { confirmar(a, gateway.nome()); return true; })
                .orElse(false);
    }

    /** Pendentes com gateway: pergunta de novo (se o webhook se perdeu enquanto o servidor dormia). */
    public int conferirPendentesNoGateway() {
        if (!gateway.configurado()) return 0;
        int ok = 0;
        for (Agendamento a : agendamentos.findBySinalSituacaoOrderByInicio(SituacaoSinal.PENDENTE)) {
            if (a.getSinalGatewayId() == null) continue;
            try {
                if (aoNotificarGateway(a.getSinalGatewayId())) ok++;
            } catch (Exception e) {
                log.warn("Consulta do sinal {} no gateway falhou: {}", a.getCodigo(), e.getMessage());
            }
        }
        return ok;
    }

    public GatewayPix gateway() {
        return gateway;
    }
}
