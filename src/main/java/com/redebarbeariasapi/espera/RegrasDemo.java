package com.redebarbeariasapi.espera;

import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.*;
import com.redebarbeariasapi.service.ConfiguracaoRedeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Base de demonstracao das regras novas: sinal ligado (fim de semana / quem faltou), alguns
 * horarios com sinal em situacoes diferentes e uma fila de espera no proximo sabado.
 * So com app.seed=true e banco recem-populado.
 */
@Component
public class RegrasDemo {

    private final ConfiguracaoRedeService configuracao;
    private final ListaEsperaRepository espera;
    private final ClienteRepository clientes;
    private final AgendamentoRepository agendamentos;
    private final UnidadeRepository unidades;
    private final ServicoRepository servicos;
    private final boolean seed;

    public RegrasDemo(ConfiguracaoRedeService configuracao, ListaEsperaRepository espera, ClienteRepository clientes,
                      AgendamentoRepository agendamentos, UnidadeRepository unidades, ServicoRepository servicos,
                      @Value("${app.seed:true}") boolean seed) {
        this.configuracao = configuracao;
        this.espera = espera;
        this.clientes = clientes;
        this.agendamentos = agendamentos;
        this.unidades = unidades;
        this.servicos = servicos;
        this.seed = seed;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void popular() {
        if (!seed || espera.count() > 0 || clientes.count() == 0) return;
        ConfiguracaoRede r = configuracao.atual();
        r.setSinalAtivo(true);
        r.setRazaoSocial("Rede Barbearias Ltda. (demonstração)");
        r.setEmailPrivacidade("privacidade@redebarbearias.com.br");

        LocalDateTime agora = LocalDateTime.now();
        // horarios futuros ja marcados: 3 viram exemplos de sinal
        List<Agendamento> futuros = agendamentos.filtrar(agora.plusHours(3), agora.plusDays(10), null, null, null, StatusAgendamento.AGENDADO);
        if (futuros.size() >= 3) {
            sinal(futuros.get(0), SituacaoSinal.PENDENTE, agora.plusMinutes(45), null);
            sinal(futuros.get(1), SituacaoSinal.PAGO, null, agora.minusHours(2));
            Agendamento cancelado = futuros.get(2);
            sinal(cancelado, SituacaoSinal.A_DEVOLVER, null, agora.minusDays(1));
            cancelado.setStatus(StatusAgendamento.CANCELADO);
            cancelado.setMotivoCancelamento("Cliente: viagem de trabalho");
        }

        // fila de espera do proximo sabado
        LocalDate sabado = agora.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        List<Unidade> uns = unidades.findByAtivaTrueOrderByNome();
        List<Servico> svs = servicos.findAll().stream().filter(Servico::isAtivo).toList();
        List<Cliente> base = clientes.findAllByOrderByNome();
        if (uns.isEmpty() || svs.isEmpty() || base.size() < 10) return;
        ListaEspera.Periodo[] periodos = {ListaEspera.Periodo.MANHA, ListaEspera.Periodo.QUALQUER, ListaEspera.Periodo.TARDE, ListaEspera.Periodo.MANHA};
        for (int i = 0; i < 4; i++) {
            ListaEspera e = new ListaEspera();
            e.setCliente(base.get(20 + i * 7));
            e.setUnidade(uns.get(i % uns.size()));
            e.setServico(svs.get(i % svs.size()));
            e.setData(sabado);
            e.setPeriodo(periodos[i]);
            e.setCriadoEm(agora.minusHours(30 - i * 5L));
            espera.save(e);
        }
    }

    private static void sinal(Agendamento a, SituacaoSinal situacao, LocalDateTime expira, LocalDateTime pagoEm) {
        a.setOrigem(OrigemAgendamento.ONLINE);
        a.setSinalValor(new BigDecimal("10.00").min(a.valorAPagar()));
        a.setSinalSituacao(situacao);
        a.setSinalExpiraEm(expira);
        a.setSinalPagoEm(pagoEm);
    }
}
