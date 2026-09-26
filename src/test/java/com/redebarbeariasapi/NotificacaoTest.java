package com.redebarbeariasapi;

import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.notificacao.CanalNotificacao;
import com.redebarbeariasapi.notificacao.Mensagem;
import com.redebarbeariasapi.notificacao.MensagemFactory;
import com.redebarbeariasapi.notificacao.NotificacaoService;
import com.redebarbeariasapi.repository.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Confirmacao, lembrete do dia e cancelamento saem sozinhos — e nunca em dobro. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(NotificacaoTest.CanalFalso.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificacaoTest {

    /** Canal de teste: "envia" guardando a mensagem em memoria. */
    @TestConfiguration
    static class CanalFalso {
        static final List<Mensagem> CAIXA = new CopyOnWriteArrayList<>();

        @Bean
        CanalNotificacao canalDeTeste() {
            return new CanalNotificacao() {
                public CanalNotificacaoTipo tipo() { return CanalNotificacaoTipo.EMAIL; }
                public boolean configurado() { return true; }
                public String destino(Mensagem m) { return m.email() == null || m.email().isBlank() ? null : m.email(); }
                public void enviar(Mensagem m, String destino) { CAIXA.add(m); }
                public String descricao() { return "teste"; }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired NotificacaoService notificacoes;
    @Autowired NotificacaoRepository historico;
    @Autowired UnidadeRepository unidades;
    @Autowired BarbeiroRepository barbeiros;
    @Autowired ServicoRepository servicos;
    @Autowired ClienteRepository clientes;
    @Autowired AgendamentoRepository agendamentos;

    Unidade unidade;
    Barbeiro barbeiro;
    Servico servico;

    @BeforeEach
    void preparar() {
        CanalFalso.CAIXA.clear();
        unidade = new Unidade();
        unidade.setNome("Rede Barbearias — Teste Notif");
        unidade.setEndereco("Rua das Mensagens, 10");
        unidade.setBairro("Centro");
        unidade.setCidade("Belo Horizonte");
        unidade.setHoraAbertura(LocalTime.of(0, 0));
        unidade.setHoraFechamento(LocalTime.of(23, 59));
        unidade.setDiasFuncionamento("1,2,3,4,5,6,7");
        unidades.save(unidade);
        barbeiro = new Barbeiro();
        barbeiro.setUnidade(unidade);
        barbeiro.setNome("Carlos Navalha");
        barbeiros.save(barbeiro);
        servico = new Servico();
        servico.setNome("Corte Teste");
        servico.setPreco(new BigDecimal("50"));
        servico.setDuracaoMinutos(30);
        servicos.save(servico);
    }

    private List<Mensagem> esperar(TipoNotificacao tipo, int quantidade) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            List<Mensagem> l = CanalFalso.CAIXA.stream().filter(m -> m.tipo() == tipo).toList();
            if (l.size() >= quantidade) return l;
            Thread.sleep(100);
        }
        return CanalFalso.CAIXA.stream().filter(m -> m.tipo() == tipo).toList();
    }

    private Agendamento marcadoOntem(String telefone, String email, LocalDateTime inicio) {
        Cliente c = new Cliente();
        c.setNome("Pedro Lembrete");
        c.setTelefone(telefone);
        c.setEmail(email);
        clientes.save(c);
        Agendamento a = new Agendamento();
        a.setCodigo("T" + telefone.substring(telefone.length() - 5));
        a.setUnidade(unidade);
        a.setBarbeiro(barbeiro);
        a.setCliente(c);
        a.setServico(servico);
        a.setInicio(inicio);
        a.setFim(inicio.plusMinutes(30));
        a.setValor(servico.getPreco());
        a.setCriadoEm(LocalDate.now().minusDays(1).atTime(10, 0));
        return agendamentos.save(a);
    }

    private String json(String nome, String telefone, String email, LocalDateTime inicio) {
        return "{\"unidadeId\":" + unidade.getId() + ",\"servicoId\":" + servico.getId() + ",\"barbeiroId\":" + barbeiro.getId()
                + ",\"inicio\":\"" + inicio + "\",\"nome\":\"" + nome + "\",\"telefone\":\"" + telefone + "\""
                + (email == null ? "" : ",\"email\":\"" + email + "\"") + "}";
    }

    @Test
    void agendamentoOnlineDisparaConfirmacaoPorEmail() throws Exception {
        LocalDateTime inicio = LocalDate.now().plusDays(2).atTime(15, 0);
        mvc.perform(post("/api/publico/agendamentos").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana Confirmação", "31966660001", "ana@teste.com", inicio)))
                .andExpect(status().isCreated());

        List<Mensagem> enviadas = esperar(TipoNotificacao.CONFIRMACAO, 1);
        assertThat(enviadas).hasSize(1);
        Mensagem m = enviadas.get(0);
        assertThat(m.email()).isEqualTo("ana@teste.com");
        assertThat(m.assunto()).contains("15:00");
        assertThat(m.html()).contains("Carlos").contains("Rua das Mensagens").contains("https://site.teste/#/meu-horario/");
        assertThat(m.texto()).contains("Código");
    }

    @Test
    void clienteSemEmailNaoRecebeNadaPorEmail() throws Exception {
        mvc.perform(post("/api/publico/agendamentos").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Sem Email", "31966660002", null, LocalDate.now().plusDays(2).atTime(16, 0))))
                .andExpect(status().isCreated());
        Thread.sleep(800);
        assertThat(CanalFalso.CAIXA).isEmpty();
    }

    @Test
    void lembreteDoDiaSaiUmaVezSoEVoltaSeOHorarioMudar() {
        LocalDateTime agora = LocalDateTime.now();
        // precisa de um horario ainda "hoje": perto da meia-noite o teste nao tem como rodar
        Assumptions.assumeTrue(agora.getHour() < 22);
        Agendamento a = marcadoOntem("31966660003", "pedro@teste.com", agora.plusMinutes(90).withSecond(0).withNano(0));

        notificacoes.processarAutomaticos(agora);
        notificacoes.processarAutomaticos(agora.plusMinutes(10));
        assertThat(CanalFalso.CAIXA.stream().filter(m -> m.tipo() == TipoNotificacao.LEMBRETE)).hasSize(1);
        assertThat(agendamentos.findById(a.getId()).orElseThrow().isLembreteEnviado()).isTrue();

        a = agendamentos.findById(a.getId()).orElseThrow();
        a.setInicio(a.getInicio().plusMinutes(30));
        a.setFim(a.getFim().plusMinutes(30));
        agendamentos.save(a);
        notificacoes.processarAutomaticos(agora.plusMinutes(20));
        assertThat(CanalFalso.CAIXA.stream().filter(m -> m.tipo() == TipoNotificacao.LEMBRETE)).hasSize(2);
        assertThat(historico.findByAgendamentoIdOrderByDataHoraDesc(a.getId())).hasSize(2);
    }

    @Test
    void cancelarPeloSiteAvisaOCliente() throws Exception {
        LocalDateTime inicio = LocalDate.now().plusDays(3).atTime(11, 0);
        String r = mvc.perform(post("/api/publico/agendamentos").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Bia Cancela", "31966660004", "bia@teste.com", inicio)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String codigo = r.replaceAll(".*\"codigo\":\"([A-Z0-9]+)\".*", "$1");
        esperar(TipoNotificacao.CONFIRMACAO, 1);
        mvc.perform(post("/api/publico/agendamentos/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefone\":\"31966660004\"}")).andExpect(status().isOk());
        assertThat(esperar(TipoNotificacao.CANCELAMENTO, 1)).hasSize(1);
    }

    @Test
    void modelosDoWhatsAppBatemComOsParametros() {
        Agendamento a = marcadoOntem("31966660005", null, LocalDate.now().plusDays(5).atTime(10, 0));
        MensagemFactory f = new MensagemFactory("https://site.teste");
        Pattern p = Pattern.compile("\\{\\{(\\d+)}}");
        for (TipoNotificacao tipo : TipoNotificacao.values()) {
            Mensagem m = f.criar(a, tipo);
            String modelo = MensagemFactory.MODELOS.get(m.modeloWhatsApp());
            assertThat(modelo).as("modelo %s cadastrado", m.modeloWhatsApp()).isNotNull();
            Matcher mt = p.matcher(modelo);
            int maior = 0;
            while (mt.find()) maior = Math.max(maior, Integer.parseInt(mt.group(1)));
            assertThat(m.parametrosWhatsApp()).as("parametros de %s", tipo).hasSize(maior);
        }
    }
}
