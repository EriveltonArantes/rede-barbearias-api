package com.redebarbeariasapi;

import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.notificacao.AtendimentoWhatsAppService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cliente manda mensagem no WhatsApp -> recebe o link de agendamento (uma vez, sem repetir a cada "oi"). */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AtendimentoWhatsAppTest {

    @Autowired MockMvc mvc;
    @Autowired AtendimentoWhatsAppService atendimento;
    @Autowired ConversaWhatsAppRepository conversas;
    @Autowired ClienteRepository clientes;
    @Autowired UnidadeRepository unidades;
    @Autowired BarbeiroRepository barbeiros;
    @Autowired ServicoRepository servicos;
    @Autowired AgendamentoRepository agendamentos;

    Cliente cliente;

    @BeforeEach
    void preparar() {
        cliente = clientes.findByTelefone("31988887777").orElseGet(() -> {
            Cliente c = new Cliente();
            c.setNome("Marcos Oliveira");
            c.setTelefone("31988887777");
            c.setPontos(2); // no perfil de teste a cartela tem 3 pontos
            return clientes.save(c);
        });
    }

    private String assinar(String corpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("segredo-do-app".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    private static String mensagemDaMeta(String id, String de, String nome, String texto) {
        return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"1\",\"changes\":[{\"field\":\"messages\",\"value\":{"
                + "\"messaging_product\":\"whatsapp\",\"contacts\":[{\"profile\":{\"name\":\"" + nome + "\"},\"wa_id\":\"" + de + "\"}],"
                + "\"messages\":[{\"from\":\"" + de + "\",\"id\":\"" + id + "\",\"timestamp\":\"1700000000\",\"type\":\"text\",\"text\":{\"body\":\"" + texto + "\"}}]}}]}]}";
    }

    @Test
    void verificacaoDoWebhookDevolveODesafioSoComOTokenCerto() throws Exception {
        mvc.perform(get("/api/whatsapp/webhook").param("hub.mode", "subscribe").param("hub.verify_token", "token-de-teste").param("hub.challenge", "12345"))
                .andExpect(status().isOk()).andExpect(content().string("12345"));
        mvc.perform(get("/api/whatsapp/webhook").param("hub.mode", "subscribe").param("hub.verify_token", "errado").param("hub.challenge", "12345"))
                .andExpect(status().isForbidden());
    }

    @Test
    void webhookSemAssinaturaValidaEhRecusado() throws Exception {
        String corpo = mensagemDaMeta("wamid.X1", "5531911112222", "Intruso", "oi");
        mvc.perform(post("/api/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(corpo)
                        .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64)))
                .andExpect(status().isForbidden());
        assertThat(conversas.findByTelefone("5531911112222")).isEmpty();
    }

    @Test
    void webhookAssinadoRegistraAConversa() throws Exception {
        String corpo = mensagemDaMeta("wamid.X2", "5531933334444", "Rodrigo Silva", "Oi, tem horário sábado?");
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
    void mesmaMensagemReenviadaPelaMetaNaoContaDuasVezes() {
        LocalDateTime agora = LocalDateTime.now();
        atendimento.receber("wamid.R1", "5531955556666", "Ana", "oi", agora);
        var r = atendimento.receber("wamid.R1", "5531955556666", "Ana", "oi", agora);
        assertThat(r.motivo()).contains("repetida");
        assertThat(conversas.findByTelefone("5531955556666").orElseThrow().getTotalRecebidas()).isEqualTo(1);
    }

    @Test
    void respostaTrazLinkDeAgendamentoENomeDoPerfil() {
        String r = atendimento.simular("Rodrigo Silva", null, LocalDateTime.now());
        assertThat(r).startsWith("Olá, Rodrigo!").contains("https://site.teste/#/agendar");
        assertThat(atendimento.simular(null, null, LocalDateTime.now())).startsWith("Olá!");
    }

    @Test
    void clienteComHorarioMarcadoRecebeOHorarioEACartela() {
        Unidade u = new Unidade();
        u.setNome("Rede Barbearias — Teste Zap");
        u.setEndereco("Rua do Zap, 1");
        u.setCidade("Belo Horizonte");
        u.setHoraAbertura(LocalTime.of(0, 0));
        u.setHoraFechamento(LocalTime.of(23, 59));
        u.setDiasFuncionamento("1,2,3,4,5,6,7");
        unidades.save(u);
        Barbeiro b = new Barbeiro();
        b.setUnidade(u);
        b.setNome("Rafael Tesoura");
        b.setApelido("Rafa");
        barbeiros.save(b);
        Servico s = new Servico();
        s.setNome("Corte + Barba");
        s.setPreco(new BigDecimal("70"));
        s.setDuracaoMinutos(60);
        servicos.save(s);
        Agendamento a = new Agendamento();
        a.setCodigo("ZAP123");
        a.setUnidade(u);
        a.setBarbeiro(b);
        a.setCliente(cliente);
        a.setServico(s);
        a.setInicio(LocalDate.now().plusDays(3).atTime(15, 0));
        a.setFim(a.getInicio().plusHours(1));
        a.setValor(s.getPreco());
        agendamentos.save(a);

        // a Meta costuma mandar o celular SEM o 9 da frente: 55 31 8888-7777
        String r = atendimento.simular(null, "553188887777", LocalDateTime.now());
        assertThat(r).startsWith("Olá, Marcos!")
                .contains("Seu próximo horário").contains("15:00").contains("Corte + Barba com Rafa")
                .contains("https://site.teste/#/meu-horario/ZAP123")
                .contains("✅✅⭕").contains("2 de 3");
    }
}
