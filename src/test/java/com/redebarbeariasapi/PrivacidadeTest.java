package com.redebarbeariasapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** LGPD: o cliente baixa os proprios dados e pode pedir a exclusao (vira anonimo). */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PrivacidadeTest {

    private static final AtomicInteger SEQ = new AtomicInteger(400);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClienteRepository clientes;
    @Autowired AgendamentoRepository agendamentos;
    @Autowired ConversaWhatsAppRepository conversas;
    @Autowired UnidadeRepository unidades;
    @Autowired BarbeiroRepository barbeiros;
    @Autowired ServicoRepository servicos;
    @Autowired PlanoRepository planos;
    @Autowired AssinaturaRepository assinaturas;

    private record Conta(String token, String telefone, Cliente cliente, String usuario) {}

    private Conta registrar() throws Exception {
        int n = SEQ.incrementAndGet();
        String tel = "3194" + String.format("%07d", n);
        String corpo = "{\"username\":\"lgpd" + n + "\",\"password\":\"senha123\",\"nome\":\"Paula Privada\",\"telefone\":\"" + tel
                + "\",\"email\":\"paula" + n + "@teste.com\",\"dataNascimento\":\"1990-05-10\"}";
        mvc.perform(post("/api/auth/registrar").contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().is2xxSuccessful());
        return new Conta(login("lgpd" + n, "senha123"), tel, clientes.findByTelefone(tel).orElseThrow(), "lgpd" + n);
    }

    private String login(String user, String senha) throws Exception {
        String r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + senha + "\"}")).andReturn().getResponse().getContentAsString();
        JsonNode t = json.readTree(r).get("token");
        return t == null ? null : "Bearer " + t.asText();
    }

    private Agendamento horarioFuturo(Cliente c) {
        Unidade u = new Unidade();
        u.setNome("Rede Barbearias — LGPD " + SEQ.incrementAndGet());
        u.setEndereco("Rua Privada, 1");
        u.setHoraAbertura(LocalTime.of(0, 0));
        u.setHoraFechamento(LocalTime.of(23, 59));
        unidades.save(u);
        Barbeiro b = new Barbeiro();
        b.setUnidade(u);
        b.setNome("Otto");
        barbeiros.save(b);
        Servico s = new Servico();
        s.setNome("Corte LGPD");
        s.setPreco(new BigDecimal("40"));
        s.setDuracaoMinutos(30);
        servicos.save(s);
        Agendamento a = new Agendamento();
        a.setCodigo("L" + SEQ.incrementAndGet());
        a.setUnidade(u);
        a.setBarbeiro(b);
        a.setCliente(c);
        a.setServico(s);
        a.setInicio(LocalDateTime.now().plusDays(4).withNano(0));
        a.setFim(a.getInicio().plusMinutes(30));
        a.setValor(s.getPreco());
        a.setObservacao("alergia a pomada X");
        return agendamentos.save(a);
    }

    @Test
    void clienteBaixaTudoQueExisteSobreEle() throws Exception {
        Conta c = registrar();
        Agendamento a = horarioFuturo(c.cliente());
        String r = mvc.perform(get("/api/minha-conta/meus-dados").header("Authorization", c.token()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=meus-dados.json"))
                .andReturn().getResponse().getContentAsString();
        JsonNode d = json.readTree(r);
        assertThat(d.path("cadastro").path("nome").asText()).isEqualTo("Paula Privada");
        assertThat(d.path("cadastro").path("dataNascimento").asText()).isEqualTo("1990-05-10");
        assertThat(d.path("agendamentos").get(0).path("codigo").asText()).isEqualTo(a.getCodigo());
    }

    @Test
    void exclusaoAnonimizaCancelaFuturosEBloqueiaLogin() throws Exception {
        Conta c = registrar();
        Agendamento a = horarioFuturo(c.cliente());
        ConversaWhatsApp conv = new ConversaWhatsApp();
        conv.setTelefone("55" + c.telefone());
        conv.setUltimaMensagem("oi, sou a Paula");
        conversas.save(conv);

        mvc.perform(post("/api/minha-conta/excluir-conta").header("Authorization", c.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmacao\":\"sim\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/minha-conta/excluir-conta").header("Authorization", c.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmacao\":\"excluir\"}")).andExpect(status().isOk());

        Cliente depois = clientes.findById(c.cliente().getId()).orElseThrow();
        assertThat(depois.getNome()).startsWith("Cliente removido");
        assertThat(depois.getTelefone()).startsWith("anon-");
        assertThat(depois.getEmail()).isNull();
        assertThat(depois.getDataNascimento()).isNull();
        assertThat(depois.isAceitaMarketing()).isFalse();
        assertThat(depois.getAnonimizadoEm()).isNotNull();

        Agendamento ag = agendamentos.findById(a.getId()).orElseThrow();
        assertThat(ag.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(ag.getMotivoCancelamento()).contains("LGPD");
        assertThat(ag.getObservacao()).isNull();
        assertThat(conversas.findByTelefone("55" + c.telefone())).isEmpty();
        assertThat(login(c.usuario(), "senha123")).isNull();
    }

    @Test
    void comClubeAtivoPedeParaCancelarAntes() throws Exception {
        Conta c = registrar();
        Plano p = new Plano();
        p.setNome("Clube LGPD " + SEQ.incrementAndGet());
        p.setPrecoMensal(new BigDecimal("99"));
        planos.save(p);
        Assinatura as = new Assinatura();
        as.setCliente(c.cliente());
        as.setPlano(p);
        as.setStatus(StatusAssinatura.ATIVA);
        as.setInicio(LocalDate.now());
        as.setCicloInicio(LocalDate.now());
        as.setValidaAte(LocalDate.now().plusDays(30));
        assinaturas.save(as);

        String admin = login("admin", "admin123");
        mvc.perform(post("/api/clientes/" + c.cliente().getId() + "/anonimizar").header("Authorization", admin))
                .andExpect(status().isConflict());
        assertThat(clientes.findById(c.cliente().getId()).orElseThrow().getAnonimizadoEm()).isNull();
    }
}
