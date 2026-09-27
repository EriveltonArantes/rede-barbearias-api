package com.redebarbeariasapi;

import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.notificacao.Mensagem;
import com.redebarbeariasapi.notificacao.RelacionamentoService;
import com.redebarbeariasapi.repository.*;
import com.redebarbeariasapi.service.ConfiguracaoRedeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Aniversario e "bora voltar?": saem sozinhos, 1 vez, so pra quem aceitou, com cupom pessoal. */
@SpringBootTest
@Import(CanalDeTeste.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RelacionamentoTest {

    private static final AtomicInteger SEQ = new AtomicInteger(300);

    @Autowired RelacionamentoService relacionamento;
    @Autowired ConfiguracaoRedeService configuracao;
    @Autowired ClienteRepository clientes;
    @Autowired CupomRepository cupons;
    @Autowired AgendamentoRepository agendamentos;
    @Autowired UnidadeRepository unidades;
    @Autowired BarbeiroRepository barbeiros;
    @Autowired ServicoRepository servicos;

    LocalDateTime dezDaManha;

    @BeforeEach
    void preparar() {
        CanalDeTeste.CAIXA.clear();
        dezDaManha = LocalDate.now().atTime(10, 0);
        configuracao.salvar(Map.of("aniversarioAtivo", true, "aniversarioDesconto", 15, "retornoAtivo", true,
                "retornoDias", 30, "retornoDesconto", 10, "horaMensagens", 9));
    }

    private Cliente cliente(String nome, boolean aceita) {
        Cliente c = new Cliente();
        c.setNome(nome);
        String tel = "3195" + String.format("%07d", SEQ.incrementAndGet());
        c.setTelefone(tel);
        c.setEmail(tel + "@teste.com");
        c.setAceitaMarketing(aceita);
        return clientes.save(c);
    }

    private List<Mensagem> para(Cliente c, TipoNotificacao tipo) {
        return CanalDeTeste.doTipo(tipo).stream().filter(m -> c.getEmail().equals(m.email())).toList();
    }

    @Test
    void aniversarianteGanhaParabensECupomUmaVezSo() {
        Cliente c = cliente("Heitor Niver", true);
        c.setDataNascimento(LocalDate.now().minusYears(30));
        clientes.save(c);

        relacionamento.processar(dezDaManha);
        relacionamento.processar(dezDaManha.plusMinutes(30));
        List<Mensagem> m = para(c, TipoNotificacao.ANIVERSARIO);
        assertThat(m).hasSize(1);
        assertThat(m.get(0).texto()).contains("15%").contains("NIVER");

        String codigo = m.get(0).texto().replaceAll("(?s).*Cupom: (NIVER[A-Z0-9]+).*", "$1");
        Cupom cp = cupons.findByCodigoIgnoreCase(codigo).orElseThrow();
        assertThat(cp.getPercentual()).isEqualByComparingTo("15");
        assertThat(cp.getLimiteUsos()).isEqualTo(1);
        assertThat(cp.getValidoAte()).isAfter(LocalDate.now());
    }

    @Test
    void semConsentimentoOuAntesDaHoraNaoManda() {
        Cliente sem = cliente("Iago Sem", false);
        sem.setDataNascimento(LocalDate.now().minusYears(25));
        clientes.save(sem);
        Cliente com = cliente("Joana Cedo", true);
        com.setDataNascimento(LocalDate.now().minusYears(22));
        clientes.save(com);

        relacionamento.processar(LocalDate.now().atTime(8, 0)); // antes das 9h configuradas
        assertThat(para(com, TipoNotificacao.ANIVERSARIO)).isEmpty();
        relacionamento.processar(dezDaManha);
        assertThat(para(sem, TipoNotificacao.ANIVERSARIO)).isEmpty();
        assertThat(para(com, TipoNotificacao.ANIVERSARIO)).hasSize(1);
    }

    @Test
    void sumidoHa30DiasRecebeConviteComCupomQuemJaMarcouNao() {
        Cliente sumido = cliente("Kleber Sumido", true);
        sumido.setUltimaVisita(dezDaManha.minusDays(31));
        clientes.save(sumido);
        Cliente recente = cliente("Luan Recente", true);
        recente.setUltimaVisita(dezDaManha.minusDays(10));
        clientes.save(recente);
        Cliente jaMarcou = cliente("Mauro Marcado", true);
        jaMarcou.setUltimaVisita(dezDaManha.minusDays(32));
        clientes.save(jaMarcou);
        horarioFuturo(jaMarcou);

        relacionamento.processar(dezDaManha);
        relacionamento.processar(dezDaManha.plusDays(1));
        List<Mensagem> m = para(sumido, TipoNotificacao.RETORNO);
        assertThat(m).hasSize(1);
        assertThat(m.get(0).texto()).contains("31 dias").contains("VOLTA").contains("10%");
        assertThat(para(recente, TipoNotificacao.RETORNO)).isEmpty();
        assertThat(para(jaMarcou, TipoNotificacao.RETORNO)).isEmpty();
    }

    @Test
    void nascidoEm29DeFevereiroComemoraDia28() throws Exception {
        var m = RelacionamentoService.class.getDeclaredMethod("fazAniversario", Cliente.class, LocalDate.class);
        m.setAccessible(true);
        Cliente c = new Cliente();
        c.setDataNascimento(LocalDate.of(2000, 2, 29));
        assertThat(m.invoke(null, c, LocalDate.of(2027, 2, 28))).isEqualTo(true);
        assertThat(m.invoke(null, c, LocalDate.of(2028, 2, 28))).isEqualTo(false);
        assertThat(m.invoke(null, c, LocalDate.of(2028, 2, 29))).isEqualTo(true);
    }

    private void horarioFuturo(Cliente c) {
        Unidade u = new Unidade();
        u.setNome("Rede Barbearias — Relac " + SEQ.incrementAndGet());
        u.setEndereco("Rua X, 1");
        u.setHoraAbertura(LocalTime.of(0, 0));
        u.setHoraFechamento(LocalTime.of(23, 59));
        unidades.save(u);
        Barbeiro b = new Barbeiro();
        b.setUnidade(u);
        b.setNome("Nico");
        barbeiros.save(b);
        Servico s = new Servico();
        s.setNome("Corte Relac");
        s.setPreco(new BigDecimal("40"));
        s.setDuracaoMinutos(30);
        servicos.save(s);
        Agendamento a = new Agendamento();
        a.setCodigo("R" + SEQ.incrementAndGet());
        a.setUnidade(u);
        a.setBarbeiro(b);
        a.setCliente(c);
        a.setServico(s);
        a.setInicio(LocalDateTime.now().plusDays(3));
        a.setFim(a.getInicio().plusMinutes(30));
        a.setValor(s.getPreco());
        agendamentos.save(a);
    }
}
