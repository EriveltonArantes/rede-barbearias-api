package com.redebarbeariasapi;

import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.notificacao.AtendimentoWhatsAppService;
import com.redebarbeariasapi.notificacao.AtendimentoWhatsAppService.Acao;
import com.redebarbeariasapi.notificacao.AtendimentoWhatsAppService.Resultado;
import com.redebarbeariasapi.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WhatsApp da barbearia: boas-vindas com link, confirmar/cancelar pelo lembrete,
 * PARAR/VOLTAR e aviso de "fechados" — e nada disso responde em dobro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AtendimentoWhatsAppTest {

    private static final AtomicInteger SEQ = new AtomicInteger(100);

    @Autowired MockMvc mvc;
    @Autowired AtendimentoWhatsAppService atendimento;
    @Autowired ConversaWhatsAppRepository conversas;
    @Autowired ClienteRepository clientes;
    @Autowired UnidadeRepository unidades;
    @Autowired BarbeiroRepository barbeiros;
    @Autowired ServicoRepository servicos;
    @Autowired AgendamentoRepository agendamentos;

    Unidade unidade;
    Barbeiro barbeiro;
    Servico servico;

    @BeforeEach
    void preparar() {
        unidade = new Unidade();
        unidade.setNome("Rede Barbearias — Teste Zap " + SEQ.incrementAndGet());
        unidade.setEndereco("Rua do Zap, 1");
        unidade.setBairro("Centro");
        unidade.setCidade("Belo Horizonte");
        unidade.setHoraAbertura(LocalTime.of(0, 0));
        unidade.setHoraFechamento(LocalTime.of(23, 59));
        unidade.setDiasFuncionamento("1,2,3,4,5,6,7");
        unidades.save(unidade);
        barbeiro = new Barbeiro();
        barbeiro.setUnidade(unidade);
        barbeiro.setNome("Rafael Tesoura");
        barbeiro.setApelido("Rafa");
        barbeiros.save(barbeiro);
        servico = new Servico();
        servico.setNome("Corte + Barba");
        servico.setPreco(new BigDecimal("70"));
        servico.setDuracaoMinutos(60);
        servicos.save(servico);
    }

    /** Cliente novo com celular 31 98xxx-xxxx (11 digitos, como no cadastro). */
    private Cliente cliente(String nome, int pontos) {
        Cliente c = new Cliente();
        c.setNome(nome);
        c.setTelefone("3198" + String.format("%07d", SEQ.incrementAndGet()));
        c.setPontos(pontos);
        return clientes.save(c);
    }

    private Agendamento horario(Cliente c, LocalDateTime inicio, boolean lembrado) {
        Agendamento a = new Agendamento();
        a.setCodigo("Z" + SEQ.incrementAndGet());
        a.setUnidade(unidade);
        a.setBarbeiro(barbeiro);
        a.setCliente(c);
        a.setServico(servico);
        a.setInicio(inicio.withSecond(0).withNano(0));
        a.setFim(a.getInicio().plusHours(1));
        a.setValor(servico.getPreco());
        a.setLembreteEnviado(lembrado);
        a.setCriadoEm(LocalDateTime.now().minusDays(2));
        return agendamentos.save(a);
    }

    /** Como a Meta manda: 55 + DDD + numero. */
    private static String wa(Cliente c) {
        return "55" + c.getTelefone();
    }

    private Resultado escreve(Cliente c, String texto) {
        return atendimento.receber("wamid." + SEQ.incrementAndGet(), wa(c), c.getNome(), texto, null, LocalDateTime.now());
    }

    private String assinar(String corpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("segredo-do-app".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    private static String mensagemDaMeta(String id, String de, String nome, String mensagemJson) {
        return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"1\",\"changes\":[{\"field\":\"messages\",\"value\":{"
                + "\"messaging_product\":\"whatsapp\",\"contacts\":[{\"profile\":{\"name\":\"" + nome + "\"},\"wa_id\":\"" + de + "\"}],"
                + "\"messages\":[{\"from\":\"" + de + "\",\"id\":\"" + id + "\",\"timestamp\":\"1700000000\"," + mensagemJson + "}]}}]}]}";
    }

    private static String texto(String t) {
        return "\"type\":\"text\",\"text\":{\"body\":\"" + t + "\"}";
    }

    // ------------------------------------------------------------------ webhook

    @Test
    void verificacaoDoWebhookDevolveODesafioSoComOTokenCerto() throws Exception {
        mvc.perform(get("/api/whatsapp/webhook").param("hub.mode", "subscribe").param("hub.verify_token", "token-de-teste").param("hub.challenge", "12345"))
                .andExpect(status().isOk()).andExpect(content().string("12345"));
        mvc.perform(get("/api/whatsapp/webhook").param("hub.mode", "subscribe").param("hub.verify_token", "errado").param("hub.challenge", "12345"))
                .andExpect(status().isForbidden());
    }

    @Test
    void webhookSemAssinaturaValidaEhRecusado() throws Exception {
        String corpo = mensagemDaMeta("wamid.X1", "5531911112222", "Intruso", texto("oi"));
        mvc.perform(post("/api/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(corpo)
                        .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64)))
                .andExpect(status().isForbidden());
        assertThat(conversas.findByTelefone("5531911112222")).isEmpty();
    }

    @Test
    void webhookAssinadoRegistraAConversa() throws Exception {
        String corpo = mensagemDaMeta("wamid.X2", "5531933334444", "Rodrigo Silva", texto("Oi, tem horário sábado?"));
        mvc.perform(post("/api/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(corpo)
                        .header("X-Hub-Signature-256", assinar(corpo)))
                .andExpect(status().isOk());
        ConversaWhatsApp c = conversas.findByTelefone("5531933334444").orElseThrow();
        assertThat(c.getNome()).isEqualTo("Rodrigo Silva");
        assertThat(c.getUltimaMensagem()).contains("sábado");
        // no teste o WhatsApp oficial nao tem token: registra o motivo em vez de fingir que respondeu
        assertThat(c.getErroResposta()).contains("não configurado");
    }

    @Test
    void botaoDoLembreteChegandoPeloWebhookConfirmaOHorario() throws Exception {
        Cliente c = cliente("Paulo Botão", 0);
        Agendamento a = horario(c, LocalDateTime.now().plusHours(5), true);
        String botao = "\"type\":\"button\",\"button\":{\"payload\":\"CONFIRMAR:" + a.getCodigo() + "\",\"text\":\"✅ Confirmo\"}";
        String corpo = mensagemDaMeta("wamid.B1", wa(c), "Paulo", botao);
        mvc.perform(post("/api/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(corpo)
                        .header("X-Hub-Signature-256", assinar(corpo)))
                .andExpect(status().isOk());
        assertThat(agendamentos.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);
        assertThat(conversas.findByTelefone(wa(c)).orElseThrow().getUltimaAcao()).contains("Confirmou").contains(a.getCodigo());
    }

    @Test
    void mesmaMensagemReenviadaPelaMetaNaoContaDuasVezes() {
        LocalDateTime agora = LocalDateTime.now();
        atendimento.receber("wamid.R1", "5531955556666", "Ana", "oi", null, agora);
        Resultado r = atendimento.receber("wamid.R1", "5531955556666", "Ana", "oi", null, agora);
        assertThat(r.motivo()).contains("repetida");
        assertThat(conversas.findByTelefone("5531955556666").orElseThrow().getTotalRecebidas()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ boas-vindas

    @Test
    void respostaTrazLinkDeAgendamentoENomeDoPerfil() {
        Resultado r = atendimento.simular("Rodrigo Silva", null, "oi", false, LocalDateTime.now());
        assertThat(r.acao()).isEqualTo(Acao.SAUDACAO);
        assertThat(r.resposta()).startsWith("Olá, Rodrigo!").contains("https://site.teste/#/agendar");
        assertThat(atendimento.simular(null, null, "oi", false, LocalDateTime.now()).resposta()).startsWith("Olá!");
    }

    @Test
    void clienteComHorarioMarcadoRecebeOHorarioEACartela() {
        Cliente c = cliente("Marcos Oliveira", 2); // no perfil de teste a cartela tem 3 pontos
        Agendamento a = horario(c, LocalDate.now().plusDays(3).atTime(15, 0), false);
        // a Meta costuma mandar o celular SEM o 9 da frente: 55 31 8xxx-xxxx
        String semNove = "55" + c.getTelefone().substring(0, 2) + c.getTelefone().substring(3);
        String r = atendimento.simular(null, semNove, "oi", false, LocalDateTime.now()).resposta();
        assertThat(r).startsWith("Olá, Marcos!")
                .contains("Seu próximo horário").contains("15:00").contains("Corte + Barba com Rafa")
                .contains("https://site.teste/#/meu-horario/" + a.getCodigo())
                .contains("✅✅⭕").contains("2 de 3");
    }

    // ------------------------------------------------------------------ confirmar / cancelar

    @Test
    void botaoConfirmarMarcaPresencaENaoRepete() {
        Cliente c = cliente("Bruno Confirma", 0);
        Agendamento a = horario(c, LocalDateTime.now().plusHours(3), true);
        LocalDateTime agora = LocalDateTime.now();

        Resultado r = atendimento.receber("wamid.C1", wa(c), "Bruno", "✅ Confirmo", "CONFIRMAR:" + a.getCodigo(), agora);
        assertThat(r.acao()).isEqualTo(Acao.CONFIRMAR);
        assertThat(r.resposta()).contains("Presença confirmada, Bruno").contains("com Rafa").contains("Rua do Zap");
        assertThat(agendamentos.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(StatusAgendamento.CONFIRMADO);

        Resultado de_novo = atendimento.receber("wamid.C2", wa(c), "Bruno", "✅ Confirmo", "CONFIRMAR:" + a.getCodigo(), agora);
        assertThat(de_novo.acao()).isEqualTo(Acao.INFORMAR);
        assertThat(de_novo.resposta()).contains("já está confirmada");
    }

    @Test
    void responder2CancelaMesmoEmCimaDaHora() {
        Cliente c = cliente("Caio Cancela", 0);
        Agendamento a = horario(c, LocalDateTime.now().plusMinutes(50), true);

        Resultado r = escreve(c, "2");
        assertThat(r.acao()).isEqualTo(Acao.CANCELAR);
        assertThat(r.resposta()).contains("cancelamos seu horário").contains("https://site.teste/#/agendar");
        Agendamento depois = agendamentos.findById(a.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        // pelo site nao daria (regra das 2h), mas quem avisa pelo WhatsApp libera a cadeira
        assertThat(depois.getMotivoCancelamento()).contains("WhatsApp").contains("menos de 2h");
    }

    @Test
    void numeroSemLembreteRecenteNaoMexeNaAgenda() {
        Cliente c = cliente("Davi Solto", 0);
        Agendamento a = horario(c, LocalDateTime.now().plusHours(4), false); // lembrete ainda nao saiu
        Resultado r = escreve(c, "1");
        assertThat(r.acao()).isEqualTo(Acao.SAUDACAO);
        assertThat(agendamentos.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
    }

    @Test
    void botaoComCodigoDeOutroClienteEhIgnorado() {
        Cliente dono = cliente("Dono Horario", 0);
        Cliente outro = cliente("Outro Numero", 0);
        Agendamento a = horario(dono, LocalDateTime.now().plusHours(3), true);
        Resultado r = atendimento.receber("wamid.O1", wa(outro), "Outro", "❌ Preciso cancelar", "CANCELAR:" + a.getCodigo(), LocalDateTime.now());
        assertThat(r.acao()).isNotIn(Acao.CANCELAR, Acao.CONFIRMAR);
        assertThat(agendamentos.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
    }

    @Test
    void horarioQueJaPassouSoRespondeSemMexer() {
        Cliente c = cliente("Elias Atrasado", 0);
        Agendamento a = horario(c, LocalDateTime.now().minusMinutes(30), true);
        Resultado r = atendimento.receber("wamid.P1", wa(c), "Elias", "✅ Confirmo", "CONFIRMAR:" + a.getCodigo(), LocalDateTime.now());
        assertThat(r.acao()).isEqualTo(Acao.INFORMAR);
        assertThat(r.resposta()).contains("já passou");
        assertThat(agendamentos.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
    }

    // ------------------------------------------------------------------ PARAR / VOLTAR

    @Test
    void pararDesligaTudoEVoltarReliga() {
        Cliente c = cliente("Fabio Para", 0);
        Resultado r = escreve(c, "PARAR");
        assertThat(r.acao()).isEqualTo(Acao.OPT_OUT);
        assertThat(r.resposta()).contains("não vai mais receber").contains("VOLTAR");
        assertThat(clientes.findById(c.getId()).orElseThrow().isWhatsappBloqueado()).isTrue();
        assertThat(conversas.findByTelefone(wa(c)).orElseThrow().isOptOut()).isTrue();

        assertThat(escreve(c, "oi, tudo bem?").acao()).isEqualTo(Acao.NENHUMA);

        assertThat(escreve(c, "Voltar").acao()).isEqualTo(Acao.OPT_IN);
        assertThat(clientes.findById(c.getId()).orElseThrow().isWhatsappBloqueado()).isFalse();
    }

    // ------------------------------------------------------------------ fora do horario

    @Test
    void foraDoHorarioAvisaQueEstaFechadoComOLink() {
        Resultado r = atendimento.simular("Gustavo", null, "tem horário hoje?", true, LocalDateTime.now());
        assertThat(r.acao()).isEqualTo(Acao.FORA_HORARIO);
        assertThat(r.resposta()).startsWith("Olá, Gustavo!").contains("fechados").contains("https://site.teste/#/agendar");
    }

    @Test
    void proximaAberturaEscritaComoGente() throws Exception {
        Unidade u = new Unidade(); // padrao: seg a sab, 9h as 20h
        var m = AtendimentoWhatsAppService.class.getDeclaredMethod("proximaAbertura", List.class, LocalDateTime.class);
        m.setAccessible(true);
        LocalDate sabado = LocalDate.of(2026, 10, 3);
        assertThat(m.invoke(null, List.of(u), sabado.atTime(21, 0))).isEqualTo("na segunda-feira às 9h");
        assertThat(m.invoke(null, List.of(u), sabado.minusDays(1).atTime(22, 0))).isEqualTo("amanhã às 9h");
        assertThat(m.invoke(null, List.of(u), sabado.minusDays(2).atTime(7, 30))).isEqualTo("hoje às 9h");
    }

    @Test
    void textoDoBotaoViraComandoSemEmojiNemAcento() throws Exception {
        var m = AtendimentoWhatsAppService.class.getDeclaredMethod("normalizar", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(null, "❌ Não vou conseguir!")).isEqualTo("nao vou conseguir");
        assertThat(m.invoke(null, "  PARAR ")).isEqualTo("parar");
    }
}
