package com.redebarbeariasapi;

import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.*;
import com.redebarbeariasapi.service.Textos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Garante um ADMIN e, com o banco vazio, popula uma rede de demonstracao realista:
 * 3 unidades, 7 barbeiros, 11 servicos, 45 clientes, ~45 dias de atendimentos pagos,
 * agenda da proxima semana, clube, cupons, estoque, vendas, despesas e avaliacoes.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${app.seed:true}")
    private boolean seed;
    @Value("${app.admin.username:admin}")
    private String adminUsername;
    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Bean
    CommandLineRunner inicializar(UsuarioRepository usuarios, UnidadeRepository unidades, PasswordEncoder encoder,
                                  Semeador semeador, TransactionTemplate tx) {
        return args -> tx.executeWithoutResult(s -> {
            if (!usuarios.existsByPapel(Papel.ADMIN)) {
                Usuario admin = new Usuario();
                admin.setUsername(adminUsername);
                admin.setNome("Administrador");
                admin.setPassword(encoder.encode(adminPassword));
                admin.setPapel(Papel.ADMIN);
                usuarios.save(admin);
                log.info("Usuario ADMIN '{}' criado", adminUsername);
            }
            if (seed && unidades.count() == 0) {
                long t0 = System.currentTimeMillis();
                semeador.popular();
                log.info("Dados de demonstracao criados em {} ms", System.currentTimeMillis() - t0);
            }
        });
    }

    @Bean
    Semeador semeador(UnidadeRepository u, BarbeiroRepository b, ServicoRepository s, ClienteRepository c,
                      AgendamentoRepository a, AvaliacaoRepository av, CupomRepository cp, PlanoRepository pl,
                      AssinaturaRepository as, PagamentoAssinaturaRepository pg, ProdutoRepository pr,
                      MovimentoEstoqueRepository mv, VendaRepository vd, DespesaRepository dp,
                      BloqueioAgendaRepository bl, UsuarioRepository us, PasswordEncoder enc) {
        return new Semeador(u, b, s, c, a, av, cp, pl, as, pg, pr, mv, vd, dp, bl, us, enc);
    }

    public record Semeador(UnidadeRepository unidades, BarbeiroRepository barbeiros, ServicoRepository servicos,
                           ClienteRepository clientes, AgendamentoRepository agendamentos, AvaliacaoRepository avaliacoes,
                           CupomRepository cupons, PlanoRepository planos, AssinaturaRepository assinaturas,
                           PagamentoAssinaturaRepository pagamentos, ProdutoRepository produtos,
                           MovimentoEstoqueRepository movimentos, VendaRepository vendas, DespesaRepository despesas,
                           BloqueioAgendaRepository bloqueios, UsuarioRepository usuarios, PasswordEncoder encoder) {

        private static final String CODIGOS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        void popular() {
            Random rnd = new Random(2026);
            LocalDate hoje = LocalDate.now();
            LocalDateTime agora = LocalDateTime.now();

            // ------------------------------------------------ unidades
            Unidade savassi = unidade("Rede Barbearias — Savassi", "Rua Pernambuco, 1200", "Savassi", "(31) 3222-1200", "31999871200",
                    LocalTime.of(9, 0), LocalTime.of(20, 0), "1,2,3,4,5,6");
            Unidade centro = unidade("Rede Barbearias — Centro", "Av. Afonso Pena, 850", "Centro", "(31) 3274-0850", "31999870850",
                    LocalTime.of(8, 0), LocalTime.of(19, 0), "1,2,3,4,5,6");
            Unidade pampulha = unidade("Rede Barbearias — Pampulha", "Av. Otacílio Negrão de Lima, 3200", "Pampulha", "(31) 3441-3200", "31999873200",
                    LocalTime.of(10, 0), LocalTime.of(21, 0), "2,3,4,5,6,7");

            // ------------------------------------------------ barbeiros
            List<Barbeiro> equipe = List.of(
                    barbeiro(savassi, "Rafael Souza", "Rafa", "Degradê, navalhado e desenhos", "12 anos de navalha. Especialista em degradê na régua e freestyle.", "45", null),
                    barbeiro(savassi, "Diego Martins", "Dieguinho", "Barba terapia e toalha quente", "Barba é arte: modelagem, toalha quente e hidratação.", "40", null),
                    barbeiro(savassi, "Lucas Ferreira", null, "Cortes clássicos e infantil", "Paciência de sobra com a criançada e mão firme no clássico.", "40", "1,2,3,4,5"),
                    barbeiro(centro, "André Oliveira", "Dedé", "Social e executivo", "Corte rápido e impecável pra quem tem reunião depois.", "45", null),
                    barbeiro(centro, "Bruno Santos", "Brunão", "Platinado, luzes e química", "Colorista: platinado, luzes e pigmentação.", "50", null),
                    barbeiro(pampulha, "Thiago Lima", null, "Degradê e barba", "Do low fade ao mid fade, sempre com acabamento na navalha.", "45", null),
                    barbeiro(pampulha, "Mateus Rocha", "Teteu", "Cortes modernos e sobrancelha", "Tendências do momento, textura e sobrancelha alinhada.", "40", "3,4,5,6,7"));

            // ------------------------------------------------ servicos
            Servico corte = servico("Corte Masculino", "Corte na tesoura ou máquina, lavagem e finalização.", CategoriaServico.CORTE, "50", 30);
            Servico degrade = servico("Corte Degradê", "Degradê (low, mid ou high fade) com acabamento na navalha.", CategoriaServico.CORTE, "55", 40);
            Servico barba = servico("Barba Completa", "Modelagem, navalha e balm pós-barba.", CategoriaServico.BARBA, "40", 30);
            Servico barbaToalha = servico("Barba Terapia", "Toalha quente, óleo, esfoliação e navalha. O ritual completo.", CategoriaServico.BARBA, "55", 40);
            Servico combo = servico("Corte + Barba", "O combo mais pedido: corte à escolha e barba completa.", CategoriaServico.COMBO, "85", 60);
            Servico pezinho = servico("Pezinho e Acabamento", "Manutenção entre um corte e outro.", CategoriaServico.CORTE, "20", 15);
            Servico sobrancelha = servico("Sobrancelha", "Alinhamento na navalha ou pinça.", CategoriaServico.ESTETICA, "15", 15);
            Servico infantil = servico("Corte Infantil", "Até 10 anos, com toda a paciência do mundo.", CategoriaServico.CORTE, "40", 30);
            Servico platinado = servico("Platinado", "Descoloração global + matização. Inclui corte.", CategoriaServico.QUIMICA, "160", 120);
            Servico pigmentacao = servico("Pigmentação de Barba", "Preenche falhas e realça o desenho da barba.", CategoriaServico.QUIMICA, "45", 30);
            Servico hidratacao = servico("Hidratação Capilar", "Tratamento com máscara e vapor.", CategoriaServico.ESTETICA, "35", 30);
            List<Servico> cardapio = List.of(corte, corte, corte, degrade, degrade, degrade, barba, barba, combo, combo, combo,
                    pezinho, sobrancelha, infantil, barbaToalha, pigmentacao, hidratacao, platinado);

            // ------------------------------------------------ cupons
            cupom("BEMVINDO10", "10% na primeira visita", "10", null, hoje.plusMonths(6), null);
            cupom("AMIGO15", "R$ 15 de desconto indicando um amigo", null, "15", hoje.plusMonths(3), 200);
            cupom("NIVER15", "Presente de aniversário: 15% no mês do aniversário", "15", null, null, null);
            Cupom black = cupom("BLACK20", "Black Friday passada", "20", null, hoje.minusDays(20), 100);
            black.setUsos(37);

            // ------------------------------------------------ clube
            Plano clubeCorte = plano("Clube Corte", "Até 4 cortes por mês. Cabelo sempre na régua.", "89.90", 4, Set.of(corte, degrade, pezinho));
            Plano clubeCompleto = plano("Clube Corte + Barba", "Até 4 visitas de corte e barba por mês.", "149.90", 4, Set.of(combo, corte, degrade, barba));
            Plano clubeBarba = plano("Clube Barba", "Até 4 barbas por mês, com toalha quente.", "69.90", 4, Set.of(barba, barbaToalha));

            // ------------------------------------------------ clientes
            // ~600 clientes: numa barbearia real o cliente volta a cada 2-4 semanas
            String[] prenomes = {"João Pedro", "Gabriel", "Matheus", "Pedro Henrique", "Lucas", "Guilherme", "Rafael", "Felipe",
                    "Gustavo", "Leonardo", "Vinícius", "Eduardo", "Daniel", "Caio", "Bernardo", "Henrique", "Samuel", "Arthur",
                    "Davi", "Enzo", "Thiago", "Vitor Hugo", "Igor", "Renan", "Diego", "Murilo", "Otávio", "Paulo Sérgio",
                    "Ricardo", "Marcelo", "Fábio", "Anderson", "Wellington", "Rodrigo", "Leandro", "Alexandre", "Sérgio",
                    "Heitor", "Miguel", "Nicolas", "Luan", "Cauã", "Bruno", "Júlio César", "Kaique", "Carlos Eduardo", "Otto"};
            String[] sobrenomes = {"Almeida", "Costa", "Ribeiro", "Dias", "Nunes", "Carvalho", "Moreira", "Araújo", "Teixeira",
                    "Pinto", "Barbosa", "Gomes", "Cardoso", "Mendes", "Rocha", "Lopes", "Freitas", "Vieira", "Monteiro",
                    "Correia", "Batista", "Ramos", "Duarte", "Castro", "Fernandes", "Azevedo", "Farias", "Melo", "Nogueira",
                    "Cunha", "Rezende", "Pires", "Silva", "Brandão", "Macedo", "Lima", "Tavares", "Campos", "Andrade", "Prado",
                    "Marques", "Siqueira", "Sales", "Viana", "Moura", "Guimarães", "Drummond", "Resende"};
            String[] observacoes = {"Prefere máquina 1 na lateral.", "Gosta de café sem açúcar.", "Alergia a pomada com perfume forte.",
                    "Sempre pede o acabamento na navalha.", "Não gosta de conversar durante o corte.", "Cabelo crespo — usar pente garfo."};
            Set<String> nomesUsados = new HashSet<>();
            List<Cliente> base = new ArrayList<>();
            for (int i = 0; i < 600; i++) {
                String nome = i == 0 ? "João Pedro Almeida"
                        : prenomes[rnd.nextInt(prenomes.length)] + " " + sobrenomes[rnd.nextInt(sobrenomes.length)]
                        + (rnd.nextInt(3) == 0 ? " " + sobrenomes[rnd.nextInt(sobrenomes.length)] : "");
                if (!nomesUsados.add(nome)) { i--; continue; }
                Cliente c = new Cliente();
                c.setNome(nome);
                c.setTelefone("3198" + String.format("%07d", 1_000_000 + i * 7919));
                if (rnd.nextInt(10) < 6) {
                    String semAcento = java.text.Normalizer.normalize(nome.toLowerCase(), java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").replace(" ", ".");
                    c.setEmail(semAcento + "@email.com");
                }
                LocalDate nasc = hoje.minusYears(18 + rnd.nextInt(45)).withDayOfYear(1 + rnd.nextInt(360));
                c.setDataNascimento(rnd.nextInt(5) == 0 ? null : nasc);
                c.setCriadoEm(agora.minusDays(3 + rnd.nextInt(400)));
                if (rnd.nextInt(8) == 0) c.setObservacoes(observacoes[rnd.nextInt(observacoes.length)]);
                c.setUnidadePreferida(i % 3 == 0 ? savassi : i % 3 == 1 ? centro : pampulha);
                c.setAceitaMarketing(rnd.nextInt(10) > 0);
                base.add(clientes.save(c));
            }
            // os ultimos 50 so aparecem no inicio do periodo: viram "clientes sumidos" pra campanha de retorno
            List<Cliente> sumidos = base.subList(550, 600);

            // ------------------------------------------------ assinaturas
            List<Assinatura> assin = new ArrayList<>();
            Plano[] planosSorteio = new Plano[36];
            for (int i = 0; i < planosSorteio.length; i++) {
                planosSorteio[i] = i % 5 == 0 ? clubeBarba : i % 2 == 0 ? clubeCompleto : clubeCorte;
            }
            for (int i = 0; i < planosSorteio.length; i++) {
                Cliente c = base.get(i * 3 + 1);
                Assinatura a = new Assinatura();
                a.setCliente(c);
                a.setPlano(planosSorteio[i]);
                LocalDate inicio = i == 6 ? hoje.minusDays(50) : hoje.minusDays(20 + rnd.nextInt(70));
                a.setInicio(inicio);
                LocalDate ciclo = inicio;
                Unidade un = c.getUnidadePreferida();
                while (!ciclo.plusMonths(1).isAfter(hoje)) {
                    pagamento(a, un, ciclo, rnd);
                    ciclo = ciclo.plusMonths(1);
                }
                a.setCicloInicio(ciclo);
                a.setValidaAte(ciclo.plusMonths(1).minusDays(1));
                if (i == 6) { // inadimplente: nao pagou o ciclo atual
                    a.setCicloInicio(ciclo.minusMonths(1));
                    a.setValidaAte(ciclo.minusDays(1));
                    a.setStatus(a.getValidaAte().isBefore(hoje.minusDays(5)) ? StatusAssinatura.SUSPENSA : StatusAssinatura.ATIVA);
                } else {
                    pagamento(a, un, ciclo, rnd);
                }
                if (i == 7 || i == 23) {
                    a.setStatus(StatusAssinatura.CANCELADA);
                    a.setCanceladaEm(hoje.minusDays(3));
                }
                assinaturas.save(a);
                assin.add(a);
            }
            pagamentos.flush();

            // ------------------------------------------------ agenda: 60 dias pra tras + 7 pra frente
            String[] comentariosBons = {"Melhor degradê que já fiz!", "Atendimento nota 10, ambiente top.", "Pontual e caprichoso.",
                    "Saí na régua, voltarei com certeza.", "Barba terapia é outro nível.", "Muito atencioso, entendeu exatamente o que eu queria.",
                    "Café, conversa boa e corte perfeito.", "Agendei pelo site em 1 minuto, muito prático."};
            String[] comentariosMedios = {"Bom corte, mas atrasou uns 10 minutos.", "Gostei, só achei a música alta.", null, null};
            FormaPagamento[] formas = {FormaPagamento.PIX, FormaPagamento.PIX, FormaPagamento.PIX, FormaPagamento.PIX,
                    FormaPagamento.CARTAO_CREDITO, FormaPagamento.CARTAO_CREDITO, FormaPagamento.CARTAO_DEBITO,
                    FormaPagamento.CARTAO_DEBITO, FormaPagamento.DINHEIRO, FormaPagamento.DINHEIRO};
            Set<String> codigos = new HashSet<>();
            Map<Long, Integer> pontos = new HashMap<>();
            Map<Long, LocalDateTime> ultimaVisita = new HashMap<>();
            List<Cliente> ativos = base.subList(0, 550);
            int total = 0;

            for (LocalDate dia = hoje.minusDays(60); !dia.isAfter(hoje.plusDays(7)); dia = dia.plusDays(1)) {
                boolean futuro = dia.isAfter(hoje);
                for (Barbeiro b : equipe) {
                    if (!b.trabalhaEm(dia.getDayOfWeek())) continue;
                    Unidade u = b.getUnidade();
                    double ocupacao = futuro ? 0.12 + 0.18 * (7 - (dia.toEpochDay() - hoje.toEpochDay())) / 7.0
                            : (dia.getDayOfWeek() == DayOfWeek.FRIDAY || dia.getDayOfWeek() == DayOfWeek.SATURDAY) ? 0.5 : 0.34;
                    LocalDateTime t = dia.atTime(u.getHoraAbertura());
                    LocalDateTime fecha = dia.atTime(u.getHoraFechamento());
                    while (t.isBefore(fecha)) {
                        Servico s = cardapio.get(rnd.nextInt(cardapio.size()));
                        if ((b.getNome().startsWith("Bruno") && rnd.nextInt(4) == 0)) s = rnd.nextBoolean() ? platinado : pigmentacao;
                        LocalDateTime fim = t.plusMinutes(s.getDuracaoMinutos());
                        if (fim.isAfter(fecha) || rnd.nextDouble() > ocupacao) {
                            t = t.plusMinutes(30);
                            continue;
                        }
                        Cliente c = (!futuro && dia.isBefore(hoje.minusDays(48)) && rnd.nextInt(5) == 0)
                                ? sumidos.get(rnd.nextInt(sumidos.size()))
                                : ativos.get(rnd.nextInt(ativos.size()));
                        Agendamento a = new Agendamento();
                        a.setCodigo(codigo(rnd, codigos));
                        a.setUnidade(u);
                        a.setBarbeiro(b);
                        a.setCliente(c);
                        a.setServico(s);
                        a.setInicio(t);
                        a.setFim(fim);
                        a.setValor(s.getPreco());
                        a.setOrigem(rnd.nextInt(10) < 4 ? OrigemAgendamento.ONLINE
                                : rnd.nextBoolean() ? OrigemAgendamento.WHATSAPP : OrigemAgendamento.BALCAO);
                        a.setCriadoEm(t.minusDays(rnd.nextInt(6)).minusHours(rnd.nextInt(8) + 1));
                        if (rnd.nextInt(12) == 0) {
                            a.setCupomCodigo(rnd.nextBoolean() ? "BEMVINDO10" : "AMIGO15");
                            a.setDesconto("BEMVINDO10".equals(a.getCupomCodigo())
                                    ? Textos.percentual(s.getPreco(), BigDecimal.TEN) : new BigDecimal("15.00").min(s.getPreco()));
                        }

                        boolean jaPassou = fim.isBefore(agora);
                        if (jaPassou) {
                            int sorte = rnd.nextInt(100);
                            if (sorte < 8) {
                                a.setStatus(StatusAgendamento.CANCELADO);
                                a.setMotivoCancelamento(rnd.nextBoolean() ? "Cliente: imprevisto no trabalho" : "Remarcou por WhatsApp");
                            } else if (sorte < 12) {
                                a.setStatus(StatusAgendamento.NAO_COMPARECEU);
                            } else {
                                fechar(a, c, formas[rnd.nextInt(formas.length)], assin, dia);
                                pontos.merge(c.getId(), a.getFormaPagamento() == FormaPagamento.CORTESIA ? 0 : 1, Integer::sum);
                                ultimaVisita.merge(c.getId(), a.getPagoEm(), (x, y) -> x.isAfter(y) ? x : y);
                            }
                        } else if (t.isBefore(agora)) {
                            a.setStatus(StatusAgendamento.EM_ATENDIMENTO);
                        } else {
                            a.setStatus(rnd.nextInt(3) == 0 ? StatusAgendamento.CONFIRMADO : StatusAgendamento.AGENDADO);
                        }
                        agendamentos.save(a);
                        total++;
                        if (a.getStatus() == StatusAgendamento.CONCLUIDO && rnd.nextInt(100) < 42) {
                            Avaliacao av = new Avaliacao();
                            av.setAgendamento(a);
                            int r = rnd.nextInt(100);
                            av.setNota(r < 72 ? 5 : r < 92 ? 4 : r < 97 ? 3 : 2);
                            av.setComentario(av.getNota() >= 4 ? (rnd.nextInt(3) == 0 ? null : comentariosBons[rnd.nextInt(comentariosBons.length)])
                                    : comentariosMedios[rnd.nextInt(comentariosMedios.length)]);
                            av.setCriadaEm(a.getPagoEm().plusHours(2 + rnd.nextInt(30)));
                            if (av.getNota() <= 3) av.setResposta("Obrigado pelo retorno! Já conversamos com a equipe pra melhorar.");
                            avaliacoes.save(av);
                        }
                        t = fim;
                    }
                }
            }
            for (Cliente c : base) {
                c.setPontos(pontos.getOrDefault(c.getId(), 0) % 12);
                c.setUltimaVisita(ultimaVisita.get(c.getId()));
            }
            // cliente demo com pontos pra resgatar e um horario futuro
            base.get(0).setPontos(10);

            // ------------------------------------------------ bloqueios
            LocalDate proxTerca = hoje.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.TUESDAY));
            BloqueioAgenda folga = new BloqueioAgenda();
            folga.setUnidade(savassi);
            folga.setBarbeiro(equipe.get(1));
            folga.setInicio(proxTerca.plusDays(7).atTime(9, 0));
            folga.setFim(proxTerca.plusDays(7).atTime(20, 0));
            folga.setMotivo("Folga — curso de visagismo");
            bloqueios.save(folga);

            // ------------------------------------------------ produtos, estoque e vendas
            Object[][] catalogo = {
                    {"Pomada Modeladora Matte", "Barba Brava", "Finalizador", "18.00", "45.00"},
                    {"Óleo para Barba 30ml", "Barba Brava", "Barba", "15.00", "39.00"},
                    {"Balm Pós-Barba", "Viking", "Barba", "16.00", "42.00"},
                    {"Shampoo Anticaspa 250ml", "Viking", "Cabelo", "14.00", "35.00"},
                    {"Cera Efeito Molhado", "QOD", "Finalizador", "17.00", "42.00"},
                    {"Minoxidil 5% 60ml", "Kirkland", "Tratamento", "42.00", "89.00"},
                    {"Pente de Madeira", "Rede Barbearias", "Acessório", "5.00", "19.00"},
                    {"Kit Barba Completo", "Barba Brava", "Kit", "55.00", "129.00"}};
            for (Unidade u : List.of(savassi, centro, pampulha)) {
                List<Produto> doSalao = new ArrayList<>();
                int idx = 0;
                for (Object[] p : catalogo) {
                    Produto pr = new Produto();
                    pr.setUnidade(u);
                    pr.setNome((String) p[0]);
                    pr.setMarca((String) p[1]);
                    pr.setCategoria((String) p[2]);
                    pr.setPrecoCusto(new BigDecimal((String) p[3]));
                    pr.setPrecoVenda(new BigDecimal((String) p[4]));
                    pr.setCodigoBarras("789" + String.format("%010d", u.getId() * 1000 + idx++));
                    pr.setEstoqueMinimo(4);
                    pr.setEstoque(0);
                    produtos.save(pr);
                    int compra = 18 + rnd.nextInt(14);
                    mov(pr, TipoMovimento.ENTRADA, compra, pr.getPrecoCusto(), "Compra fornecedor", agora.minusDays(65));
                    doSalao.add(pr);
                }
                List<Barbeiro> daUnidade = equipe.stream().filter(b -> b.getUnidade() == u).toList();
                for (int i = 0; i < 45; i++) {
                    Venda v = new Venda();
                    v.setUnidade(u);
                    v.setDataHora(hoje.minusDays(rnd.nextInt(60)).atTime(10 + rnd.nextInt(8), rnd.nextInt(60)));
                    if (rnd.nextBoolean()) v.setCliente(ativos.get(rnd.nextInt(ativos.size())));
                    if (rnd.nextInt(3) > 0) v.setBarbeiro(daUnidade.get(rnd.nextInt(daUnidade.size())));
                    v.setFormaPagamento(formas[rnd.nextInt(formas.length)]);
                    v.setUsuario("recepcao");
                    BigDecimal subtotal = BigDecimal.ZERO;
                    int itens = 1 + rnd.nextInt(2);
                    Set<Produto> usados = new HashSet<>();
                    for (int k = 0; k < itens; k++) {
                        Produto pr = doSalao.get(rnd.nextInt(doSalao.size()));
                        if (!usados.add(pr) || pr.getEstoque() < 2) continue;
                        VendaItem it = new VendaItem();
                        it.setVenda(v);
                        it.setProduto(pr);
                        it.setQuantidade(1);
                        it.setPrecoUnitario(pr.getPrecoVenda());
                        it.setTotal(pr.getPrecoVenda());
                        v.getItens().add(it);
                        subtotal = subtotal.add(it.getTotal());
                    }
                    if (v.getItens().isEmpty()) continue;
                    v.setSubtotal(subtotal);
                    v.setTotal(subtotal);
                    v.setComissaoValor(v.getBarbeiro() == null ? BigDecimal.ZERO : Textos.percentual(subtotal, v.getBarbeiro().getComissaoProduto()));
                    vendas.save(v);
                    for (VendaItem it : v.getItens()) {
                        mov(it.getProduto(), TipoMovimento.SAIDA_VENDA, -it.getQuantidade(), null, "Venda #" + v.getId(), v.getDataHora());
                    }
                }
                // deixa 2 itens abaixo do minimo pra mostrar o alerta de reposicao
                for (Produto pr : List.of(doSalao.get(5), doSalao.get(7))) {
                    int alvo = 1 + rnd.nextInt(2);
                    if (pr.getEstoque() > alvo) mov(pr, TipoMovimento.AJUSTE, alvo - pr.getEstoque(), null, "Inventário", agora.minusDays(2));
                }
            }

            // ------------------------------------------------ despesas (mes anterior pago + mes atual)
            Object[][] contas = {
                    {"Aluguel", CategoriaDespesa.ALUGUEL, 5, new int[]{3900, 2900, 3400}},
                    {"Energia elétrica (Cemig)", CategoriaDespesa.CONTAS_CONSUMO, 12, new int[]{520, 430, 560}},
                    {"Água (Copasa)", CategoriaDespesa.CONTAS_CONSUMO, 15, new int[]{150, 130, 160}},
                    {"Internet fibra", CategoriaDespesa.CONTAS_CONSUMO, 10, new int[]{130, 130, 130}},
                    {"Salário recepcionista", CategoriaDespesa.SALARIOS, 5, new int[]{2100, 2000, 2100}},
                    {"Reposição de lâminas, toalhas e descartáveis", CategoriaDespesa.PRODUTOS, 20, new int[]{380, 300, 340}},
                    {"Anúncios Instagram", CategoriaDespesa.MARKETING, 25, new int[]{350, 250, 300}},
                    {"Simples Nacional", CategoriaDespesa.IMPOSTOS, 20, new int[]{1300, 950, 1100}}};
            List<Unidade> lojas = List.of(savassi, centro, pampulha);
            for (int m = -1; m <= 0; m++) {
                LocalDate mes = hoje.plusMonths(m).withDayOfMonth(1);
                for (int li = 0; li < lojas.size(); li++) {
                    for (Object[] c : contas) {
                        Despesa d = new Despesa();
                        d.setUnidade(lojas.get(li));
                        d.setDescricao((String) c[0]);
                        d.setCategoria((CategoriaDespesa) c[1]);
                        d.setValor(BigDecimal.valueOf(((int[]) c[3])[li]));
                        d.setVencimento(mes.withDayOfMonth(Math.min((Integer) c[2], mes.lengthOfMonth())));
                        boolean paga = m < 0 || (d.getVencimento().isBefore(hoje) && !(li == 1 && c[1] == CategoriaDespesa.CONTAS_CONSUMO && (Integer) c[2] == 12));
                        d.setPaga(paga);
                        if (paga) d.setPagaEm(d.getVencimento());
                        d.setFornecedor(c[1] == CategoriaDespesa.ALUGUEL ? "Imobiliária Horizonte" : null);
                        despesas.save(d);
                    }
                }
            }

            // ------------------------------------------------ contas de acesso de demonstracao
            usuario("gerente", "gerente123", "Carla Menezes", Papel.GERENTE, savassi, null, null);
            usuario("recepcao", "recepcao123", "Juliana Prado", Papel.RECEPCAO, centro, null, null);
            usuario("barbeiro", "barbeiro123", null, Papel.BARBEIRO, null, equipe.get(0), null);
            usuario("cliente", "cliente123", null, Papel.CLIENTE, null, null, base.get(0));

            log.info("Seed: {} agendamentos, {} clientes, {} assinaturas", total, base.size(), assin.size());
        }

        private void fechar(Agendamento a, Cliente c, FormaPagamento forma, List<Assinatura> assin, LocalDate dia) {
            Assinatura cobre = assin.stream()
                    .filter(x -> x.getCliente().getId().equals(c.getId()) && x.getStatus() != StatusAssinatura.CANCELADA
                            && !dia.isBefore(x.getInicio()) && !dia.isAfter(x.getValidaAte())
                            && x.getPlano().getServicos().contains(a.getServico()))
                    .findFirst().orElse(null);
            BigDecimal cobrado;
            if (cobre != null) {
                forma = FormaPagamento.ASSINATURA;
                cobrado = BigDecimal.ZERO;
                a.setDesconto(BigDecimal.ZERO);
                a.setCupomCodigo(null);
                if (!dia.isBefore(cobre.getCicloInicio())) cobre.setUsosNoCiclo(Math.min(cobre.getPlano().getUsosPorMes(), cobre.getUsosNoCiclo() + 1));
            } else {
                cobrado = a.valorAPagar();
            }
            BigDecimal base = cobrado.signum() > 0 ? cobrado : a.getValor();
            a.setComissaoValor(Textos.percentual(base, a.getBarbeiro().getComissaoServico()));
            a.setValorFinal(Textos.dinheiro(cobrado));
            a.setFormaPagamento(forma);
            a.setPago(true);
            a.setPagoEm(a.getFim().plusMinutes(3));
            a.setStatus(StatusAgendamento.CONCLUIDO);
        }

        private void pagamento(Assinatura a, Unidade u, LocalDate dia, Random rnd) {
            if (a.getId() == null) {
                a.setCicloInicio(dia);
                a.setValidaAte(dia.plusMonths(1).minusDays(1));
                assinaturas.save(a);
            }
            PagamentoAssinatura p = new PagamentoAssinatura();
            p.setAssinatura(a);
            p.setUnidade(u);
            p.setValor(a.getPlano().getPrecoMensal());
            p.setFormaPagamento(rnd.nextBoolean() ? FormaPagamento.PIX : FormaPagamento.CARTAO_CREDITO);
            p.setDataHora(dia.atTime(11, 0));
            pagamentos.save(p);
        }

        private void mov(Produto p, TipoMovimento tipo, int qtd, BigDecimal custo, String motivo, LocalDateTime quando) {
            p.setEstoque(p.getEstoque() + qtd);
            MovimentoEstoque m = new MovimentoEstoque();
            m.setProduto(p);
            m.setTipo(tipo);
            m.setQuantidade(qtd);
            m.setCustoUnitario(custo);
            m.setMotivo(motivo);
            m.setUsuario("sistema");
            m.setDataHora(quando);
            movimentos.save(m);
        }

        private String codigo(Random rnd, Set<String> usados) {
            while (true) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 6; i++) sb.append(CODIGOS.charAt(rnd.nextInt(CODIGOS.length())));
                if (usados.add(sb.toString())) return sb.toString();
            }
        }

        private Unidade unidade(String nome, String endereco, String bairro, String tel, String whats,
                                LocalTime abre, LocalTime fecha, String dias) {
            Unidade u = new Unidade();
            u.setNome(nome);
            u.setEndereco(endereco);
            u.setBairro(bairro);
            u.setCidade("Belo Horizonte");
            u.setTelefone(tel);
            u.setWhatsapp(whats);
            u.setEmail(bairro.toLowerCase() + "@redebarbearias.com.br");
            u.setHoraAbertura(abre);
            u.setHoraFechamento(fecha);
            u.setDiasFuncionamento(dias);
            return unidades.save(u);
        }

        private Barbeiro barbeiro(Unidade u, String nome, String apelido, String esp, String bio, String comissao, String dias) {
            Barbeiro b = new Barbeiro();
            b.setUnidade(u);
            b.setNome(nome);
            b.setApelido(apelido);
            b.setEspecialidades(esp);
            b.setBio(bio);
            b.setComissaoServico(new BigDecimal(comissao));
            b.setComissaoProduto(BigDecimal.TEN);
            b.setDiasTrabalho(dias);
            b.setTelefone("3197" + String.format("%07d", Math.abs(nome.hashCode()) % 10_000_000));
            return barbeiros.save(b);
        }

        private Servico servico(String nome, String desc, CategoriaServico cat, String preco, int minutos) {
            Servico s = new Servico();
            s.setNome(nome);
            s.setDescricao(desc);
            s.setCategoria(cat);
            s.setPreco(new BigDecimal(preco));
            s.setDuracaoMinutos(minutos);
            return servicos.save(s);
        }

        private Cupom cupom(String codigo, String desc, String pct, String valor, LocalDate validade, Integer limite) {
            Cupom c = new Cupom();
            c.setCodigo(codigo);
            c.setDescricao(desc);
            c.setPercentual(pct == null ? null : new BigDecimal(pct));
            c.setValorFixo(valor == null ? null : new BigDecimal(valor));
            c.setValidoAte(validade);
            c.setLimiteUsos(limite);
            return cupons.save(c);
        }

        private Plano plano(String nome, String desc, String preco, int usos, Set<Servico> incluidos) {
            Plano p = new Plano();
            p.setNome(nome);
            p.setDescricao(desc);
            p.setPrecoMensal(new BigDecimal(preco));
            p.setUsosPorMes(usos);
            p.setServicos(new HashSet<>(incluidos));
            return planos.save(p);
        }

        private void usuario(String login, String senha, String nome, Papel papel, Unidade u, Barbeiro b, Cliente c) {
            if (usuarios.existsByUsernameIgnoreCase(login)) return;
            Usuario x = new Usuario();
            x.setUsername(login);
            x.setPassword(encoder.encode(senha));
            x.setNome(nome);
            x.setPapel(papel);
            x.setUnidade(u);
            x.setBarbeiro(b);
            x.setCliente(c);
            usuarios.save(x);
        }
    }
}
