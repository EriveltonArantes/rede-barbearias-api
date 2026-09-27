package com.redebarbeariasapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redebarbeariasapi.espera.ListaEsperaService;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.notificacao.Mensagem;
import com.redebarbeariasapi.repository.*;
import com.redebarbeariasapi.service.ConfiguracaoRedeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lista de espera: entra pelo site, e avisada na ordem quando alguem cancela, sai quando agenda. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CanalDeTeste.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ListaEsperaTest {

    private static final AtomicInteger SEQ = new AtomicInteger(200);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ListaEsperaService espera;
    @Autowired ListaEsperaRepository fila;
    @Autowired ConfiguracaoRedeService configuracao;
    @Autowired AgendamentoRepository agendamentos;
    @Autowired UnidadeRepository unidades;
    @Autowired BarbeiroRepository barbeiros;
    @Autowired ServicoRepository servicos;

    Unidade unidade;
    Barbeiro barbeiro;
    Servico servico;
    LocalDate dia;

    @BeforeEach
    void preparar() {
        CanalDeTeste.CAIXA.clear();
        unidade = new Unidade();
        unidade.setNome("Rede Barbearias — Espera " + SEQ.incrementAndGet());
        unidade.setEndereco("Rua da Fila, 5");
        unidade.setCidade("Belo Horizonte");
        unidade.setHoraAbertura(LocalTime.of(8, 0));
        unidade.setHoraFechamento(LocalTime.of(20, 0));
        unidade.setDiasFuncionamento("1,2,3,4,5,6,7");
        unidades.save(unidade);
        barbeiro = new Barbeiro();
        barbeiro.setUnidade(unidade);
        barbeiro.setNome("Beto Fila");
        barbeiros.save(barbeiro);
        servico = new Servico();
        servico.setNome("Corte Fila");
        servico.setPreco(new BigDecimal("45"));
        servico.setDuracaoMinutos(30);
        servicos.save(servico);
        dia = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY)).plusWeeks(1);
        configuracao.salvar(Map.of("esperaAtiva", true, "esperaAvisarQuantos", 2));
    }

    private static String tel() {
        return "3196" + String.format("%07d", SEQ.incrementAndGet());
    }

    private JsonNode entrar(String nome, String telefone, String periodo) throws Exception {
        String corpo = "{\"unidadeId\":" + unidade.getId() + ",\"servicoId\":" + servico.getId() + ",\"data\":\"" + dia + "\",\"periodo\":\"" + periodo
                + "\",\"nome\":\"" + nome + "\",\"telefone\":\"" + telefone + "\",\"email\":\"" + telefone + "@teste.com\"}";
        return json.readTree(mvc.perform(post("/api/publico/lista-espera").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String agendar(String telefone, LocalTime hora) throws Exception {
        String corpo = "{\"unidadeId\":" + unidade.getId() + ",\"servicoId\":" + servico.getId() + ",\"barbeiroId\":" + barbeiro.getId()
                + ",\"inicio\":\"" + dia.atTime(hora) + "\",\"nome\":\"Quem Marcou\",\"telefone\":\"" + telefone + "\"}";
        String r = mvc.perform(post("/api/publico/agendamentos").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("codigo").asText();
    }

    @Test
    void entraPeloSiteComPosicaoESemDuplicar() throws Exception {
        String t1 = tel();
        assertThat(entrar("Ana Fila", t1, "TARDE").get("posicao").asInt()).isEqualTo(1);
        assertThat(entrar("Bia Fila", tel(), "MANHA").get("posicao").asInt()).isEqualTo(2);
        // a mesma pessoa de novo so atualiza a preferencia, sem perder o lugar
        assertThat(entrar("Ana Fila", t1, "QUALQUER").get("posicao").asInt()).isEqualTo(1);
        assertThat(fila.findByUnidadeIdAndDataAndStatusInOrderByCriadoEm(unidade.getId(), dia,
                List.of(ListaEspera.Status.AGUARDANDO))).hasSize(2);
    }

    @Test
    void quandoAlguemCancelaAvisaOsPrimeirosDaFila() throws Exception {
        String quemMarcou = tel();
        String codigo = agendar(quemMarcou, LocalTime.of(15, 0));
        String a = tel(), b = tel(), c = tel();
        entrar("Primeiro Fila", a, "TARDE");
        entrar("Segundo Fila", b, "QUALQUER");
        entrar("Terceiro Fila", c, "QUALQUER");

        mvc.perform(post("/api/publico/agendamentos/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefone\":\"" + quemMarcou + "\"}")).andExpect(status().isOk());

        List<Mensagem> avisos = CanalDeTeste.esperar(TipoNotificacao.VAGA_LIBERADA, 2);
        Thread.sleep(300);
        avisos = CanalDeTeste.doTipo(TipoNotificacao.VAGA_LIBERADA);
        assertThat(avisos).extracting(Mensagem::email).containsExactlyInAnyOrder(a + "@teste.com", b + "@teste.com");
        Mensagem doPrimeiro = avisos.stream().filter(m -> m.email().startsWith(a)).findFirst().orElseThrow();
        // o horario que liberou vai pra quem pediu a tarde, com o link ja preenchido
        assertThat(doPrimeiro.assunto()).contains("15:00");
        assertThat(doPrimeiro.texto()).contains("#/agendar?unidade=" + unidade.getId() + "&servico=" + servico.getId() + "&data=" + dia);
        assertThat(fila.findByUnidadeIdAndDataAndStatusInOrderByCriadoEm(unidade.getId(), dia, List.of(ListaEspera.Status.AVISADO))).hasSize(2);
    }

    @Test
    void quemEstaNaFilaEAgendaSaiDaFila() throws Exception {
        String t = tel();
        entrar("Carlos Fila", t, "QUALQUER");
        agendar(t, LocalTime.of(10, 0));
        assertThat(fila.findByUnidadeIdAndDataAndStatusInOrderByCriadoEm(unidade.getId(), dia, List.of(ListaEspera.Status.AGENDOU)))
                .extracting(e -> e.getCliente().getTelefone()).contains(t);
    }

    @Test
    void diaQuePassouExpira() throws Exception {
        entrar("Dani Fila", tel(), "QUALQUER");
        ListaEspera e = fila.findByUnidadeIdAndDataAndStatusInOrderByCriadoEm(unidade.getId(), dia, List.of(ListaEspera.Status.AGUARDANDO)).get(0);
        e.setData(LocalDate.now().minusDays(1));
        fila.save(e);
        espera.expirar();
        assertThat(fila.findById(e.getId()).orElseThrow().getStatus()).isEqualTo(ListaEspera.Status.EXPIROU);
    }

    @Test
    void desligadaRecusaEntrada() throws Exception {
        configuracao.salvar(Map.of("esperaAtiva", false));
        String corpo = "{\"unidadeId\":" + unidade.getId() + ",\"servicoId\":" + servico.getId() + ",\"data\":\"" + dia
                + "\",\"nome\":\"Eva Fila\",\"telefone\":\"" + tel() + "\"}";
        mvc.perform(post("/api/publico/lista-espera").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());
        configuracao.salvar(Map.of("esperaAtiva", true));
    }
}
