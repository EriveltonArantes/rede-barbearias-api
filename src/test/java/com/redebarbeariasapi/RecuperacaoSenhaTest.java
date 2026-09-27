package com.redebarbeariasapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redebarbeariasapi.notificacao.Mensagem;
import com.redebarbeariasapi.repository.RecuperacaoSenhaRepository;
import com.redebarbeariasapi.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Esqueci minha senha": codigo por e-mail/WhatsApp, entrar por celular/e-mail e a equipe ajudando pelo painel. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CanalDeTeste.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecuperacaoSenhaTest {

    private static final AtomicInteger SEQ = new AtomicInteger(700);
    /** Cada teste usa um "IP" proprio: o limite por IP das rotas publicas nao interfere. */
    private static final AtomicInteger IP = new AtomicInteger(10);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired RecuperacaoSenhaRepository recuperacoes;

    private record Conta(String usuario, String telefone, String email) {}

    private MockHttpServletRequestBuilder publico(MockHttpServletRequestBuilder b, String ip) {
        return b.contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", ip);
    }

    private Conta registrar() throws Exception {
        int n = SEQ.incrementAndGet();
        String tel = "3197" + String.format("%07d", n);
        String email = "rec" + n + "@teste.com";
        String corpo = "{\"username\":\"rec" + n + "\",\"password\":\"antiga123\",\"nome\":\"Rita Recupera\",\"telefone\":\"" + tel
                + "\",\"email\":\"" + email + "\"}";
        mvc.perform(publico(post("/api/auth/registrar"), "10.0.0." + IP.incrementAndGet()).content(corpo)).andExpect(status().isCreated());
        return new Conta("rec" + n, tel, email);
    }

    private int login(String usuario, String senha, String ip) throws Exception {
        return mvc.perform(publico(post("/api/auth/login"), ip)
                .content("{\"username\":\"" + usuario + "\",\"password\":\"" + senha + "\"}")).andReturn().getResponse().getStatus();
    }

    @Autowired com.redebarbeariasapi.repository.UnidadeRepository unidades;
    @Autowired com.redebarbeariasapi.repository.UsuarioRepository usuariosRepo;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

    private String token(String usuario, String senha) throws Exception {
        if (!usuario.equals("admin")) Equipe.garantir(usuariosRepo, unidades, encoder, usuario, senha,
                com.redebarbeariasapi.model.Papel.valueOf(usuario.toUpperCase()));
        String r = mvc.perform(publico(post("/api/auth/login"), "10.9.9." + IP.incrementAndGet())
                .content("{\"username\":\"" + usuario + "\",\"password\":\"" + senha + "\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(r).get("token").asText();
    }

    private String codigoRecebido(String email) {
        Mensagem m = CanalDeTeste.CAIXA.stream().filter(x -> email.equals(x.email()) && x.assunto().contains("nova senha"))
                .reduce((a, b) -> b).orElseThrow(() -> new AssertionError("código não chegou no e-mail " + email));
        Matcher mt = Pattern.compile("(\\d{6})").matcher(m.texto());
        assertThat(mt.find()).isTrue();
        return mt.group(1);
    }

    @Test
    void entraComCelularOuEmailAlemDoUsuario() throws Exception {
        Conta c = registrar();
        String ip = "10.1.0." + IP.incrementAndGet();
        assertThat(login(c.usuario(), "antiga123", ip)).isEqualTo(200);
        assertThat(login("(" + c.telefone().substring(0, 2) + ") " + c.telefone().substring(2, 7) + "-" + c.telefone().substring(7), "antiga123", ip)).isEqualTo(200);
        assertThat(login(c.email().toUpperCase(), "antiga123", ip)).isEqualTo(200);
        assertThat(login(c.email(), "errada999", ip)).isEqualTo(401);
    }

    @Test
    void codigoPorEmailTrocaASenhaEJaEntra() throws Exception {
        Conta c = registrar();
        String ip = "10.2.0." + IP.incrementAndGet();
        String r = mvc.perform(publico(post("/api/auth/esqueci-senha"), ip).content("{\"login\":\"" + c.telefone() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(r).path("mensagem").asText()).contains("código");
        String codigo = codigoRecebido(c.email());

        String ok = mvc.perform(publico(post("/api/auth/redefinir-senha"), ip)
                        .content("{\"login\":\"" + c.email() + "\",\"codigo\":\"" + codigo + "\",\"novaSenha\":\"nova12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(ok).path("token").asText()).isNotBlank();
        assertThat(login(c.usuario(), "antiga123", ip)).isEqualTo(401);
        assertThat(login(c.usuario(), "nova12345", ip)).isEqualTo(200);

        // o mesmo codigo nao serve duas vezes
        mvc.perform(publico(post("/api/auth/redefinir-senha"), ip)
                        .content("{\"login\":\"" + c.usuario() + "\",\"codigo\":\"" + codigo + "\",\"novaSenha\":\"outra12345\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cincoCodigosErradosQueimamOPedido() throws Exception {
        Conta c = registrar();
        String ip = "10.3.0." + IP.incrementAndGet();
        mvc.perform(publico(post("/api/auth/esqueci-senha"), ip).content("{\"login\":\"" + c.usuario() + "\"}")).andExpect(status().isOk());
        String certo = codigoRecebido(c.email());
        String errado = certo.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            String r = mvc.perform(publico(post("/api/auth/redefinir-senha"), ip)
                            .content("{\"login\":\"" + c.usuario() + "\",\"codigo\":\"" + errado + "\",\"novaSenha\":\"nova12345\"}"))
                    .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
            if (i == 0) assertThat(json.readTree(r).path("erro").asText()).contains("4 tentativas");
        }
        // agora nem o codigo certo vale: tem que pedir outro
        mvc.perform(publico(post("/api/auth/redefinir-senha"), ip)
                        .content("{\"login\":\"" + c.usuario() + "\",\"codigo\":\"" + certo + "\",\"novaSenha\":\"nova12345\"}"))
                .andExpect(status().isBadRequest());
        assertThat(login(c.usuario(), "antiga123", ip)).isEqualTo(200);
    }

    @Test
    void contaInexistenteRecebeAMesmaRespostaSemRevelarNada() throws Exception {
        String ip = "10.4.0." + IP.incrementAndGet();
        String existe = mvc.perform(publico(post("/api/auth/esqueci-senha"), ip).content("{\"login\":\"cliente\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String naoExiste = mvc.perform(publico(post("/api/auth/esqueci-senha"), ip).content("{\"login\":\"ninguem-aqui-999\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(existe).isEqualTo(naoExiste);
    }

    @Test
    void recepcaoVeOPedidoEGeraCodigoPraMandarNoWhatsApp() throws Exception {
        Conta c = registrar();
        String ip = "10.5.0." + IP.incrementAndGet();
        mvc.perform(publico(post("/api/auth/esqueci-senha"), ip).content("{\"login\":\"" + c.usuario() + "\"}")).andExpect(status().isOk());

        String recepcao = token("recepcao", "recepcao123");
        String lista = mvc.perform(get("/api/usuarios/recuperacoes-pendentes").header("Authorization", recepcao))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode pedido = null;
        for (JsonNode p : json.readTree(lista)) if (c.usuario().equals(p.path("username").asText())) pedido = p;
        assertThat(pedido).as("pedido aparece pra recepção").isNotNull();
        assertThat(pedido.path("telefone").asText()).isEqualTo(c.telefone());

        String gerado = mvc.perform(post("/api/usuarios/" + pedido.path("usuarioId").asLong() + "/codigo-senha").header("Authorization", recepcao))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode g = json.readTree(gerado);
        String codigo = g.path("codigo").asText();
        assertThat(g.path("mensagem").asText()).contains(codigo).contains(c.usuario());

        // o codigo que o cliente recebeu antes por e-mail deixou de valer; o da recepcao vale
        mvc.perform(publico(post("/api/auth/redefinir-senha"), ip)
                        .content("{\"login\":\"" + c.usuario() + "\",\"codigo\":\"" + codigo + "\",\"novaSenha\":\"balcao123\"}"))
                .andExpect(status().isOk());
        assertThat(login(c.usuario(), "balcao123", ip)).isEqualTo(200);

        // concluido: sai da lista
        String depois = mvc.perform(get("/api/usuarios/recuperacoes-pendentes").header("Authorization", recepcao))
                .andReturn().getResponse().getContentAsString();
        assertThat(depois).doesNotContain("\"" + c.usuario() + "\"");
    }

    @Test
    void recepcaoNaoGeraCodigoPraContaDaEquipe() throws Exception {
        String recepcao = token("recepcao", "recepcao123");
        Long admin = usuarios.findByUsernameIgnoreCase("admin").orElseThrow().getId();
        mvc.perform(post("/api/usuarios/" + admin + "/codigo-senha").header("Authorization", recepcao)).andExpect(status().isForbidden());
    }
}
