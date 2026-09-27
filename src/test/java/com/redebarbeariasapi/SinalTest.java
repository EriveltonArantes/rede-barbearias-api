package com.redebarbeariasapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.pagamento.GatewayPix;
import com.redebarbeariasapi.pagamento.SinalRotina;
import com.redebarbeariasapi.pagamento.SinalService;
import com.redebarbeariasapi.repository.*;
import com.redebarbeariasapi.service.ConfiguracaoRedeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sinal por Pix: quando pede, quanto abate, quando devolve, quando fica com a barbearia. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SinalTest.GatewayFalso.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SinalTest {

    /** Gateway de mentira: cria cobranca e responde "aprovado" so pros ids que o teste mandar. */
    @TestConfiguration
    static class GatewayFalso {
        static volatile boolean ligado;
        static final Map<String, String> REFERENCIAS = new ConcurrentHashMap<>();
        static final Map<String, Boolean> APROVADOS = new ConcurrentHashMap<>();
        static final AtomicInteger SEQ = new AtomicInteger();

        @Bean
        @Primary
        GatewayPix gatewayDeTeste() {
            return new GatewayPix() {
                public boolean configurado() { return ligado; }
                public Cobranca criar(BigDecimal valor, String descricao, String email, String referencia, LocalDateTime expiraEm) {
                    String id = "pay-" + SEQ.incrementAndGet();
                    REFERENCIAS.put(id, referencia);
                    return new Cobranca(id, "00020126PIXGATEWAY" + id, null);
                }
                public Situacao consultar(String id) {
                    return new Situacao(id, APROVADOS.getOrDefault(id, false), REFERENCIAS.get(id), null);
                }
                public String nome() { return "Gateway de teste"; }
            };
        }
    }

    private static final AtomicInteger TEL = new AtomicInteger(1000);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ConfiguracaoRedeService configuracao;
    @Autowired SinalService sinal;
    @Autowired SinalRotina rotina;
    @Autowired AgendamentoRepository agendamentos;
    @Autowired UnidadeRepository unidades;
    @Autowired BarbeiroRepository barbeiros;
    @Autowired ServicoRepository servicos;
    @Autowired ClienteRepository clientes;

    Unidade unidade;
    Barbeiro barbeiro;
    Servico servico;
    String admin;

    @BeforeEach
    void preparar() throws Exception {
        unidade = new Unidade();
        unidade.setNome("Rede Barbearias — Sinal " + TEL.incrementAndGet());
        unidade.setEndereco("Rua do Pix, 10");
        unidade.setCidade("Belo Horizonte");
        unidade.setHoraAbertura(LocalTime.of(0, 0));
        unidade.setHoraFechamento(LocalTime.of(23, 59));
        unidade.setDiasFuncionamento("1,2,3,4,5,6,7");
        unidades.save(unidade);
        barbeiro = new Barbeiro();
        barbeiro.setUnidade(unidade);
        barbeiro.setNome("Joca Navalha");
        barbeiros.save(barbeiro);
        servico = new Servico();
        servico.setNome("Corte Sinal");
        servico.setPreco(new BigDecimal("50"));
        servico.setDuracaoMinutos(30);
        servicos.save(servico);
        configuracao.salvar(Map.of("sinalAtivo", true, "sinalFimDeSemana", true, "sinalQuemFaltou", true, "sinalSempre", false,
                "sinalValor", 10, "sinalDevolucaoHoras", 24, "sinalCancelarSemPagamento", false));
        String r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}")).andReturn().getResponse().getContentAsString();
        admin = "Bearer " + json.readTree(r).get("token").asText();
    }

    @AfterEach
    void desligarGateway() {
        GatewayFalso.ligado = false;
    }

    private static String novoTelefone() {
        return "3197" + String.format("%07d", TEL.incrementAndGet());
    }

    private Agendamento online(LocalDateTime inicio, String telefone) throws Exception {
        String corpo = "{\"unidadeId\":" + unidade.getId() + ",\"servicoId\":" + servico.getId() + ",\"barbeiroId\":" + barbeiro.getId()
                + ",\"inicio\":\"" + inicio.withSecond(0).withNano(0) + "\",\"nome\":\"Cliente Sinal\",\"telefone\":\"" + telefone + "\"}";
        String r = mvc.perform(post("/api/publico/agendamentos").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return agendamentos.findByCodigo(json.readTree(r).get("codigo").asText()).orElseThrow();
    }

    private static LocalDateTime proximo(DayOfWeek dia, int semanasDepois) {
        return LocalDate.now().with(TemporalAdjusters.next(dia)).plusWeeks(semanasDepois).atTime(15, 0);
    }

    private Agendamento pagar(Agendamento a) throws Exception {
        mvc.perform(post("/api/agendamentos/" + a.getId() + "/sinal").param("acao", "RECEBIDO").header("Authorization", admin))
                .andExpect(status().isOk());
        return agendamentos.findById(a.getId()).orElseThrow();
    }

    @Test
    void fimDeSemanaPedeSinalComPixEDiaDeSemanaNao() throws Exception {
        Agendamento sabado = online(proximo(DayOfWeek.SATURDAY, 1), novoTelefone());
        assertThat(sabado.getSinalSituacao()).isEqualTo(SituacaoSinal.PENDENTE);
        assertThat(sabado.getSinalValor()).isEqualByComparingTo("10");
        assertThat(sabado.getSinalExpiraEm()).isAfter(LocalDateTime.now());

        JsonNode pix = json.readTree(mvc.perform(get("/api/publico/agendamentos/" + sabado.getCodigo() + "/sinal"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(pix.get("payload").asText()).contains("br.gov.bcb.pix").contains("10.00");
        assertThat(pix.get("qrCodeBase64").asText()).startsWith("data:image/png;base64,");

        Agendamento quarta = online(proximo(DayOfWeek.WEDNESDAY, 1), novoTelefone());
        assertThat(quarta.getSinalSituacao()).isNull();
    }

    @Test
    void quemFaltouPagaSinalAteEmDiaDeSemana() throws Exception {
        String tel = novoTelefone();
        Agendamento primeiro = online(proximo(DayOfWeek.TUESDAY, 1), tel);
        primeiro.setInicio(LocalDateTime.now().minusDays(20));
        primeiro.setFim(primeiro.getInicio().plusMinutes(30));
        primeiro.setStatus(StatusAgendamento.NAO_COMPARECEU);
        agendamentos.save(primeiro);

        Agendamento depois = online(proximo(DayOfWeek.THURSDAY, 1), tel);
        assertThat(depois.getSinalSituacao()).isEqualTo(SituacaoSinal.PENDENTE);
    }

    @Test
    void balcaoNuncaPedeSinal() {
        Cliente c = new Cliente();
        c.setNome("Cliente Balcão");
        c.setTelefone(novoTelefone());
        clientes.save(c);
        assertThat(sinal.motivo(c, proximo(DayOfWeek.SATURDAY, 1), OrigemAgendamento.BALCAO)).isEmpty();
        assertThat(sinal.motivo(c, proximo(DayOfWeek.SATURDAY, 1), OrigemAgendamento.ONLINE)).isPresent();
    }

    @Test
    void sinalPagoEAbatidoNoDiaEOValorFinalEOTotal() throws Exception {
        LocalDateTime agora = LocalDateTime.now();
        org.junit.jupiter.api.Assumptions.assumeTrue(agora.getHour() < 21);
        configuracao.salvar(Map.of("sinalSempre", true));
        Agendamento a = pagar(online(agora.plusHours(2), novoTelefone()));
        assertThat(a.getSinalSituacao()).isEqualTo(SituacaoSinal.PAGO);
        assertThat(a.valorAPagar()).isEqualByComparingTo("40");

        mvc.perform(post("/api/agendamentos/" + a.getId() + "/finalizar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"formaPagamento\":\"PIX\"}"))
                .andExpect(status().isOk());
        Agendamento fim = agendamentos.findById(a.getId()).orElseThrow();
        assertThat(fim.getSinalSituacao()).isEqualTo(SituacaoSinal.ABATIDO);
        // no caixa entraram 40 hoje, mas o atendimento valeu 50 (os 10 do sinal fazem parte)
        assertThat(fim.getValorFinal()).isEqualByComparingTo("50");
    }

    @Test
    void cancelarComAntecedenciaDevolveEmCimaDaHoraRetem() throws Exception {
        String tel1 = novoTelefone();
        Agendamento longe = pagar(online(proximo(DayOfWeek.SATURDAY, 2), tel1));
        mvc.perform(post("/api/publico/agendamentos/" + longe.getCodigo() + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefone\":\"" + tel1 + "\"}")).andExpect(status().isOk());
        assertThat(agendamentos.findById(longe.getId()).orElseThrow().getSinalSituacao()).isEqualTo(SituacaoSinal.A_DEVOLVER);

        LocalDateTime agora = LocalDateTime.now();
        org.junit.jupiter.api.Assumptions.assumeTrue(agora.getHour() < 20);
        configuracao.salvar(Map.of("sinalSempre", true));
        String tel2 = novoTelefone();
        Agendamento perto = pagar(online(agora.plusHours(3), tel2));
        mvc.perform(post("/api/publico/agendamentos/" + perto.getCodigo() + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefone\":\"" + tel2 + "\"}")).andExpect(status().isOk());
        assertThat(agendamentos.findById(perto.getId()).orElseThrow().getSinalSituacao()).isEqualTo(SituacaoSinal.RETIDO);
        assertThat(agendamentos.sinaisRetidos(agora.minusMinutes(5), agora.plusMinutes(5), null))
                .extracting(Agendamento::getId).contains(perto.getId());

        // a recepcao devolve o primeiro
        mvc.perform(post("/api/agendamentos/" + longe.getId() + "/sinal").param("acao", "DEVOLVIDO").header("Authorization", admin))
                .andExpect(status().isOk());
        assertThat(agendamentos.findById(longe.getId()).orElseThrow().getSinalSituacao()).isEqualTo(SituacaoSinal.DEVOLVIDO);
    }

    @Test
    void faltaSemAvisoRetemOSinal() throws Exception {
        Agendamento a = pagar(online(proximo(DayOfWeek.SATURDAY, 1), novoTelefone()));
        a.setInicio(LocalDateTime.now().minusHours(2));
        a.setFim(a.getInicio().plusMinutes(30));
        agendamentos.save(a);
        mvc.perform(patch("/api/agendamentos/" + a.getId() + "/status").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"NAO_COMPARECEU\"}")).andExpect(status().isOk());
        assertThat(agendamentos.findById(a.getId()).orElseThrow().getSinalSituacao()).isEqualTo(SituacaoSinal.RETIDO);
    }

    @Test
    void comGatewayOPixConfirmaSozinhoPeloWebhook() throws Exception {
        GatewayFalso.ligado = true;
        Agendamento a = online(proximo(DayOfWeek.SUNDAY, 1), novoTelefone());
        assertThat(a.getSinalGatewayId()).startsWith("pay-");
        JsonNode pix = json.readTree(mvc.perform(get("/api/publico/agendamentos/" + a.getCodigo() + "/sinal"))
                .andReturn().getResponse().getContentAsString());
        assertThat(pix.get("payload").asText()).isEqualTo("00020126PIXGATEWAY" + a.getSinalGatewayId());

        // aviso falso (id que o gateway nao aprovou) nao confirma nada
        mvc.perform(post("/api/pagamentos/mercadopago/webhook").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"payment\",\"data\":{\"id\":\"" + a.getSinalGatewayId() + "\"}}")).andExpect(status().isOk());
        assertThat(agendamentos.findById(a.getId()).orElseThrow().getSinalSituacao()).isEqualTo(SituacaoSinal.PENDENTE);

        GatewayFalso.APROVADOS.put(a.getSinalGatewayId(), true);
        mvc.perform(post("/api/pagamentos/mercadopago/webhook").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"payment\",\"data\":{\"id\":\"" + a.getSinalGatewayId() + "\"}}")).andExpect(status().isOk());
        assertThat(agendamentos.findById(a.getId()).orElseThrow().getSinalSituacao()).isEqualTo(SituacaoSinal.PAGO);
    }

    @Test
    void semPagamentoNoPrazoCancelaSoQuandoLigado() throws Exception {
        Agendamento a = online(proximo(DayOfWeek.SATURDAY, 3), novoTelefone());
        a.setSinalExpiraEm(LocalDateTime.now().minusMinutes(1));
        agendamentos.save(a);

        rotina.processar(LocalDateTime.now());
        assertThat(agendamentos.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(StatusAgendamento.AGENDADO);

        configuracao.salvar(Map.of("sinalCancelarSemPagamento", true));
        rotina.processar(LocalDateTime.now());
        Agendamento depois = agendamentos.findById(a.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(depois.getMotivoCancelamento()).contains("Sinal não pago");
        assertThat(depois.getSinalSituacao()).isEqualTo(SituacaoSinal.EXPIRADO);
        configuracao.salvar(Map.of("sinalCancelarSemPagamento", false));
    }
}
