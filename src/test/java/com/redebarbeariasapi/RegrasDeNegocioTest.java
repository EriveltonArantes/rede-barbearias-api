package com.redebarbeariasapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.*;
import com.redebarbeariasapi.service.PixService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegrasDeNegocioTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UnidadeRepository unidades;
    @Autowired BarbeiroRepository barbeiros;
    @Autowired ServicoRepository servicos;
    @Autowired ClienteRepository clientes;
    @Autowired ProdutoRepository produtos;
    @Autowired PlanoRepository planos;
    @Autowired AssinaturaRepository assinaturas;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;

    Unidade unidade;
    Barbeiro barbeiro;
    Barbeiro outroBarbeiro;
    Servico corte;
    String admin;
    final LocalDate amanha = LocalDate.now().plusDays(1);
    final LocalDate hoje = LocalDate.now();

    @BeforeEach
    void preparar() throws Exception {
        unidade = new Unidade();
        unidade.setNome("Unidade Teste");
        unidade.setEndereco("Rua A, 1");
        unidade.setHoraAbertura(LocalTime.of(8, 0));
        unidade.setHoraFechamento(LocalTime.of(22, 0));
        unidade.setDiasFuncionamento("1,2,3,4,5,6,7");
        unidades.save(unidade);
        barbeiro = barbeiro("Rafael");
        outroBarbeiro = barbeiro("Diego");
        corte = new Servico();
        corte.setNome("Corte");
        corte.setPreco(new BigDecimal("50.00"));
        corte.setDuracaoMinutos(30);
        servicos.save(corte);
        admin = token("admin", "admin123");
    }

    private Barbeiro barbeiro(String nome) {
        Barbeiro b = new Barbeiro();
        b.setUnidade(unidade);
        b.setNome(nome);
        b.setComissaoServico(new BigDecimal("40"));
        b.setComissaoProduto(new BigDecimal("10"));
        return barbeiros.save(b);
    }

    private String token(String user, String senha) throws Exception {
        String r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(r).get("token").asText();
    }

    private String usuario(String login, Papel papel, Barbeiro b) throws Exception {
        Usuario u = new Usuario();
        u.setUsername(login);
        u.setPassword(encoder.encode("senha123"));
        u.setPapel(papel);
        if (papel == Papel.BARBEIRO) u.setBarbeiro(b);
        else if (papel != Papel.ADMIN) u.setUnidade(unidade);
        usuarios.save(u);
        return token(login, "senha123");
    }

    private ResultActions agendar(String tk, Barbeiro b, LocalDate dia, String hora, String telefone) throws Exception {
        String body = """
                {"barbeiroId":%d,"servicoId":%d,"clienteNome":"Cliente Teste","clienteTelefone":"%s","inicio":"%sT%s:00"}
                """.formatted(b.getId(), corte.getId(), telefone, dia, hora);
        return mvc.perform(post("/api/agendamentos").header("Authorization", tk)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode corpo(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void autoCadastroSempreViraClienteMesmoPedindoAdmin() throws Exception {
        mvc.perform(post("/api/auth/registrar").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"espertinho","password":"123456","nome":"Esperto","telefone":"31988887777","papel":"ADMIN","role":"ADMIN"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("CLIENTE"));
        mvc.perform(get("/api/usuarios").header("Authorization", token("espertinho", "123456")))
                .andExpect(status().isForbidden());
    }

    @Test
    void naoDeixaDoisClientesNoMesmoHorarioDoMesmoBarbeiro() throws Exception {
        agendar(admin, barbeiro, amanha, "10:00", "31999990001").andExpect(status().isCreated());
        agendar(admin, barbeiro, amanha, "10:15", "31999990002")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value(org.hamcrest.Matchers.containsString("Horário indisponível")));
        // outro barbeiro no mesmo horario pode
        agendar(admin, outroBarbeiro, amanha, "10:15", "31999990002").andExpect(status().isCreated());
    }

    @Test
    void recusaForaDoHorarioDeFuncionamento() throws Exception {
        agendar(admin, barbeiro, amanha, "21:45", "31999990001").andExpect(status().isConflict());
        agendar(admin, barbeiro, amanha, "07:30", "31999990001").andExpect(status().isConflict());
    }

    @Test
    void disponibilidadeEscondeHorarioOcupadoEBloqueado() throws Exception {
        agendar(admin, barbeiro, amanha, "10:00", "31999990001").andExpect(status().isCreated());
        mvc.perform(post("/api/bloqueios").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unidadeId\":%d,\"barbeiroId\":%d,\"inicio\":\"%sT14:00:00\",\"fim\":\"%sT15:00:00\",\"motivo\":\"Almoço\"}"
                                .formatted(unidade.getId(), barbeiro.getId(), amanha, amanha)))
                .andExpect(status().isCreated());

        JsonNode slots = corpo(mvc.perform(get("/api/publico/disponibilidade")
                .param("unidadeId", unidade.getId().toString()).param("servicoId", corte.getId().toString())
                .param("data", amanha.toString()).param("barbeiroId", barbeiro.getId().toString())));
        Set<String> horas = new HashSet<>();
        slots.forEach(s -> horas.add(s.get("hora").asText()));
        assertThat(horas).doesNotContain("09:45", "10:00", "10:15", "13:45", "14:00", "14:30");
        assertThat(horas).contains("09:30", "10:30", "15:00");
    }

    @Test
    void agendamentoOnlineSemLoginCancelaSoComTelefoneCerto() throws Exception {
        JsonNode ag = corpo(mvc.perform(post("/api/publico/agendamentos").contentType(MediaType.APPLICATION_JSON).content("""
                        {"unidadeId":%d,"servicoId":%d,"inicio":"%sT11:00:00","nome":"Maria Cliente","telefone":"(31) 98888-1234"}
                        """.formatted(unidade.getId(), corte.getId(), amanha)))
                .andExpect(status().isCreated()));
        String codigo = ag.get("codigo").asText();
        assertThat(codigo).hasSize(6);
        assertThat(ag.get("barbeiroNome").asText()).isNotBlank(); // "sem preferencia" escolheu alguem

        mvc.perform(post("/api/publico/agendamentos/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"31900000000\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/publico/agendamentos/" + codigo + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefone\":\"31988881234\",\"motivo\":\"imprevisto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
        // avaliar antes de ser atendido nao pode
        mvc.perform(post("/api/publico/agendamentos/" + codigo + "/avaliar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":5}"))
                .andExpect(status().isConflict());
    }

    @Test
    void finalizarCalculaComissaoPontuaEResgataFidelidade() throws Exception {
        long id = corpo(agendar(admin, barbeiro, hoje, "09:00", "31977776666").andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(post("/api/agendamentos/" + id + "/finalizar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"formaPagamento\":\"PIX\",\"descontoExtra\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.valorFinal").value(40.00))
                .andExpect(jsonPath("$.comissaoValor").value(16.00));
        Cliente c = clientes.findByTelefone("31977776666").orElseThrow();
        assertThat(c.getPontos()).isEqualTo(1);

        // pontos-resgate=3 no perfil de teste
        c.setPontos(3);
        long id2 = corpo(agendar(admin, barbeiro, hoje, "11:00", "31977776666")).get("id").asLong();
        mvc.perform(post("/api/agendamentos/" + id2 + "/finalizar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usarFidelidade\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formaPagamento").value("CORTESIA"))
                .andExpect(jsonPath("$.valorFinal").value(0))
                .andExpect(jsonPath("$.comissaoValor").value(20.00));
        assertThat(clientes.findByTelefone("31977776666").orElseThrow().getPontos()).isZero();

        // nao finaliza duas vezes
        mvc.perform(post("/api/agendamentos/" + id2 + "/finalizar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"formaPagamento\":\"PIX\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void clubeConsomeUsosEBloqueiaQuandoAcaba() throws Exception {
        Plano p = new Plano();
        p.setNome("Clube");
        p.setPrecoMensal(new BigDecimal("89.90"));
        p.setUsosPorMes(1);
        p.setServicos(new HashSet<>(Set.of(corte)));
        planos.save(p);
        long clienteId = corpo(mvc.perform(post("/api/clientes").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Assinante\",\"telefone\":\"31955554444\"}"))
                .andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(post("/api/clube/assinaturas").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":%d,\"planoId\":%d,\"unidadeId\":%d,\"formaPagamento\":\"PIX\"}"
                                .formatted(clienteId, p.getId(), unidade.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usosRestantes").value(1));

        long a1 = corpo(agendar(admin, barbeiro, hoje, "12:00", "31955554444")).get("id").asLong();
        mvc.perform(post("/api/agendamentos/" + a1 + "/finalizar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usarAssinatura\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formaPagamento").value("ASSINATURA"));
        long a2 = corpo(agendar(admin, barbeiro, hoje, "13:00", "31955554444")).get("id").asLong();
        mvc.perform(post("/api/agendamentos/" + a2 + "/finalizar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usarAssinatura\":true}"))
                .andExpect(status().isConflict());

        // receita do clube entra no financeiro
        mvc.perform(get("/api/financeiro/resumo").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receitaClube").value(89.90));
    }

    @Test
    void recepcaoNaoExcluiEBarbeiroSoVeAPropriaAgenda() throws Exception {
        long id = corpo(agendar(admin, barbeiro, amanha, "10:00", "31999990001")).get("id").asLong();
        agendar(admin, outroBarbeiro, amanha, "10:00", "31999990002").andExpect(status().isCreated());

        String recepcao = usuario("rec", Papel.RECEPCAO, null);
        mvc.perform(delete("/api/agendamentos/" + id).header("Authorization", recepcao)).andExpect(status().isForbidden());
        mvc.perform(get("/api/financeiro/resumo").header("Authorization", recepcao)).andExpect(status().isForbidden());

        String rafa = usuario("rafa", Papel.BARBEIRO, barbeiro);
        JsonNode lista = corpo(mvc.perform(get("/api/agendamentos").param("de", amanha.toString())
                .header("Authorization", rafa)).andExpect(status().isOk()));
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("barbeiroId").asLong()).isEqualTo(barbeiro.getId());

        // sem token = 401
        mvc.perform(get("/api/agendamentos")).andExpect(status().isUnauthorized());
    }

    @Test
    void vendaBaixaEstoqueRecusaSemSaldoEEstornoDevolve() throws Exception {
        Produto p = new Produto();
        p.setUnidade(unidade);
        p.setNome("Pomada");
        p.setPrecoVenda(new BigDecimal("45.00"));
        p.setPrecoCusto(new BigDecimal("18.00"));
        p.setEstoque(2);
        produtos.save(p);
        String venda = "{\"unidadeId\":%d,\"barbeiroId\":%d,\"formaPagamento\":\"DINHEIRO\",\"itens\":[{\"produtoId\":%d,\"quantidade\":%d}]}";

        mvc.perform(post("/api/vendas").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(venda.formatted(unidade.getId(), barbeiro.getId(), p.getId(), 3)))
                .andExpect(status().isConflict());
        JsonNode v = corpo(mvc.perform(post("/api/vendas").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(venda.formatted(unidade.getId(), barbeiro.getId(), p.getId(), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(90.00))
                .andExpect(jsonPath("$.comissaoValor").value(9.00)));
        assertThat(produtos.findById(p.getId()).orElseThrow().getEstoque()).isZero();

        mvc.perform(post("/api/vendas/" + v.get("id").asLong() + "/cancelar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"cliente desistiu\"}"))
                .andExpect(status().isOk());
        assertThat(produtos.findById(p.getId()).orElseThrow().getEstoque()).isEqualTo(2);
    }

    @Test
    void pixGeraBrCodeComCrcValido() throws Exception {
        assertThat(PixService.crc16("123456789")).isEqualTo("29B1");
        JsonNode pix = corpo(mvc.perform(post("/api/pix/gerar").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":85,\"referencia\":\"AG-12\"}"))
                .andExpect(status().isOk()));
        String payload = pix.get("payload").asText();
        assertThat(payload).startsWith("000201").contains("br.gov.bcb.pix").contains("540585.00").contains("5802BR");
        String semCrc = payload.substring(0, payload.length() - 4);
        assertThat(payload.substring(payload.length() - 4)).isEqualTo(PixService.crc16(semCrc));
        assertThat(pix.get("qrCodeBase64").asText()).startsWith("data:image/png;base64,");
    }

    @Test
    void dashboardResponde() throws Exception {
        long id = corpo(agendar(admin, barbeiro, hoje, "08:30", "31911112222")).get("id").asLong();
        mvc.perform(post("/api/agendamentos/" + id + "/finalizar").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"formaPagamento\":\"CARTAO_DEBITO\"}"));
        mvc.perform(get("/api/dashboard/resumo").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.faturamentoHoje").value(50.00))
                .andExpect(jsonPath("$.faturamento14Dias.length()").value(14));
    }
}
