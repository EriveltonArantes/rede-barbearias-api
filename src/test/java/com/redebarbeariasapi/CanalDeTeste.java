package com.redebarbeariasapi;

import com.redebarbeariasapi.model.CanalNotificacaoTipo;
import com.redebarbeariasapi.model.TipoNotificacao;
import com.redebarbeariasapi.notificacao.CanalNotificacao;
import com.redebarbeariasapi.notificacao.Mensagem;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Canal de e-mail de mentira: "envia" guardando a mensagem pra o teste conferir. */
@TestConfiguration
public class CanalDeTeste {

    public static final List<Mensagem> CAIXA = new CopyOnWriteArrayList<>();

    @Bean
    CanalNotificacao canalEmailDeTeste() {
        return new CanalNotificacao() {
            public CanalNotificacaoTipo tipo() { return CanalNotificacaoTipo.EMAIL; }
            public boolean configurado() { return true; }
            public String destino(Mensagem m) { return m.email() == null || m.email().isBlank() ? null : m.email(); }
            public void enviar(Mensagem m, String destino) { CAIXA.add(m); }
            public String descricao() { return "teste"; }
        };
    }

    public static List<Mensagem> doTipo(TipoNotificacao tipo) {
        return CAIXA.stream().filter(m -> m.tipo() == tipo).toList();
    }

    /** Espera mensagens que saem em segundo plano (eventos @Async). */
    public static List<Mensagem> esperar(TipoNotificacao tipo, int quantidade) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            if (doTipo(tipo).size() >= quantidade) break;
            Thread.sleep(100);
        }
        return doTipo(tipo);
    }
}
