package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.model.Cliente;
import com.redebarbeariasapi.model.ConversaWhatsApp;
import com.redebarbeariasapi.repository.ClienteRepository;
import com.redebarbeariasapi.repository.ConversaWhatsAppRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Base de demonstracao: algumas conversas de WhatsApp pra tela de atendimento nao abrir vazia.
 * Roda com a aplicacao pronta (depois de todos os CommandLineRunner, inclusive o seed principal)
 * e so quando o seed esta ligado (app.seed=true).
 */
@Component
public class ConversasDemo {

    private final ConversaWhatsAppRepository conversas;
    private final ClienteRepository clientes;
    private final AtendimentoWhatsAppService atendimento;
    private final boolean seed;

    public ConversasDemo(ConversaWhatsAppRepository conversas, ClienteRepository clientes,
                         AtendimentoWhatsAppService atendimento, @Value("${app.seed:true}") boolean seed) {
        this.conversas = conversas;
        this.clientes = clientes;
        this.atendimento = atendimento;
        this.seed = seed;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void popular() {
        if (!seed || conversas.count() > 0 || clientes.count() == 0) return;
        LocalDateTime agora = LocalDateTime.now();
        List<Cliente> base = clientes.findAllByOrderByNome();
        Cliente conhecido = base.get(Math.min(7, base.size() - 1));
        criar("5531987654321", "Lucas Ferreira", null, "Boa tarde! Vocês têm horário hoje à noite?", agora.minusMinutes(12), 1, true);
        ConversaWhatsApp c2 = criar("55" + conhecido.getTelefone(), conhecido.getNome(), conhecido, "Oi, consigo passar meu horário pra mais tarde?", agora.minusMinutes(47), 2, true);
        c2.setUltimaAcao("✅ Confirmou presença pelo lembrete");
        conversas.save(c2);
        criar("5531991112233", "Thiago", null, "Qual o valor do corte + barba?", agora.minusHours(3), 3, true);
        criar("5531993334455", "Gabriel M.", null, "(audio)", agora.minusHours(20), 1, true);
        criar("5531995556677", "Diego", null, "Blz, obrigado!", agora.minusDays(2), 4, true);
        ConversaWhatsApp parou = criar("5531997778899", "Renato", null, "PARAR", agora.minusDays(3), 2, false);
        parou.setOptOut(true);
        parou.setUltimaAcao("🚫 Pediu pra não receber mais mensagens");
        conversas.save(parou);
    }

    private ConversaWhatsApp criar(String tel, String nome, Cliente cliente, String msg, LocalDateTime quando, int total, boolean respondida) {
        ConversaWhatsApp c = new ConversaWhatsApp();
        c.setTelefone(tel.replaceAll("\\D", ""));
        c.setNome(nome);
        c.setCliente(cliente);
        c.setUltimaMensagem(msg);
        c.setUltimaMensagemId("demo-" + tel);
        c.setUltimaRecebidaEm(quando);
        c.setTotalRecebidas(total);
        if (respondida) {
            c.setUltimaRespostaEm(total == 1 ? quando.plusSeconds(2) : quando.minusMinutes(30));
            c.setUltimaResposta(atendimento.simular(nome, cliente == null ? null : cliente.getTelefone(), msg, false, quando).resposta());
        }
        return conversas.save(c);
    }
}
