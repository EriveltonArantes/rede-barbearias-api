package com.redebarbeariasapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redebarbeariasapi.sistema.AlertaService;
import com.redebarbeariasapi.sistema.Saude;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Marca configuravel (site/app/mensagens), painel de saude, alertas e backup. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MarcaESistemaTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Saude saude;
    @Autowired AlertaService alertas;

    @Autowired com.redebarbeariasapi.repository.UnidadeRepository unidades;
    @Autowired com.redebarbeariasapi.repository.UsuarioRepository usuariosRepo;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

    private String token(String usuario, String senha) throws Exception {
        if (!usuario.equals("admin")) Equipe.garantir(usuariosRepo, unidades, encoder, usuario, senha,
                com.redebarbeariasapi.model.Papel.valueOf(usuario.toUpperCase()));
        String r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "10.20.0." + usuario.length())
                .content("{\"username\":\"" + usuario + "\",\"password\":\"" + senha + "\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(r).get("token").asText();
    }

    @Test
    void adminTrocaAMarcaEOSiteEOAppMudamJunto() throws Exception {
        String admin = token("admin", "admin123");
        mvc.perform(put("/api/configuracoes/marca").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Barbearia do Zé\",\"corPrincipal\":\"#1E90FF\",\"instagram\":\"https://instagram.com/barbeariadoze/\",\"whatsapp\":\"(31) 99999-0000\"}"))
                .andExpect(status().isOk());

        JsonNode m = json.readTree(mvc.perform(get("/api/publico/marca")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(m.path("nome").asText()).isEqualTo("Barbearia do Zé");
        assertThat(m.path("corPrincipal").asText()).isEqualTo("#1e90ff");
        assertThat(m.path("instagram").asText()).isEqualTo("barbeariadoze");
        assertThat(m.path("whatsapp").asText()).isEqualTo("31999990000");
        assertThat(m.path("nomeCurto").asText()).hasSizeLessThanOrEqualTo(12);

        JsonNode man = json.readTree(mvc.perform(get("/api/publico/manifest.webmanifest").param("origem", "https://barbeariadoze.com.br"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(man.path("name").asText()).isEqualTo("Barbearia do Zé");
        assertThat(man.path("start_url").asText()).isEqualTo("https://barbeariadoze.com.br/#/");
        assertThat(man.path("display").asText()).isEqualTo("standalone");
        assertThat(man.path("icons").size()).isEqualTo(4);

        // origem estranha nao entra no manifesto
        JsonNode ruim = json.readTree(mvc.perform(get("/api/publico/manifest.webmanifest").param("origem", "javascript:alert(1)"))
                .andReturn().getResponse().getContentAsString());
        assertThat(ruim.path("start_url").asText()).isEqualTo("/#/");

        byte[] png = mvc.perform(get("/api/publico/icone/192.png")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(png.length).isGreaterThan(100);
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(png[2]).isEqualTo((byte) 'N');

        mvc.perform(put("/api/configuracoes/marca").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"corPrincipal\":\"azul\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/configuracoes/marca").header("Authorization", token("gerente", "gerente123")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Invasor\"}")).andExpect(status().isForbidden());
    }

    @Test
    void painelDeSaudeMostraOQuePrecisaDeAtencao() throws Exception {
        String admin = token("admin", "admin123");
        JsonNode s = json.readTree(mvc.perform(get("/api/sistema/saude").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(s.path("banco").path("ok").asBoolean()).isTrue();
        assertThat(s.path("banco").path("temporario").asBoolean()).isTrue();
        String verificacoes = s.path("verificacoes").toString();
        assertThat(verificacoes).contains("Banco temporário").contains("senha padrão").contains("Alertas desligados");
        // os itens mais graves vem primeiro
        assertThat(s.path("verificacoes").get(0).path("nivel").asText()).isEqualTo("erro");

        mvc.perform(get("/api/sistema/saude").header("Authorization", token("gerente", "gerente123"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/sistema/saude")).andExpect(status().isUnauthorized());
    }

    @Test
    void mesmaPecaFalhandoTresVezesViraAlertaUmaVezSo() {
        int antes = alertas.recentes().size();
        for (int i = 0; i < 5; i++) saude.falha("teste-peca", "timeout");
        assertThat(alertas.recentes().size()).isEqualTo(antes + 1);
        assertThat(alertas.recentes().get(0).titulo()).contains("teste-peca").contains("3 vezes");
        saude.ok("teste-peca");
        assertThat(alertas.recentes().get(0).titulo()).contains("voltou");
    }

    @Test
    void backupTemTodasAsTabelasEmCsvESemSenha() throws Exception {
        String admin = token("admin", "admin123");
        byte[] zip = mvc.perform(get("/api/sistema/backup").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        Map<String, String> arquivos = new HashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().endsWith(".csv") || e.getName().endsWith(".txt")) arquivos.put(e.getName(), new String(z.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(arquivos).containsKeys("clientes.csv", "agendamentos.csv", "usuarios.csv", "LEIA-ME.txt");
        String cabecalhoUsuarios = arquivos.get("usuarios.csv").lines().findFirst().orElse("");
        assertThat(cabecalhoUsuarios).contains("username").doesNotContain("password");
        assertThat(arquivos.get("clientes.csv").lines().findFirst().orElse("")).contains("telefone").contains("nome");
        assertThat(arquivos.get("LEIA-ME.txt")).contains("Senhas NÃO");

        mvc.perform(get("/api/sistema/backup").header("Authorization", token("recepcao", "recepcao123"))).andExpect(status().isForbidden());
    }

    @Test
    void testeDeAlertaSemCanalExplicaOQueConfigurar() throws Exception {
        String r = mvc.perform(post("/api/sistema/alerta-teste").header("Authorization", token("admin", "admin123")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(r).contains("ALERTA_TELEGRAM_TOKEN");
    }
}
