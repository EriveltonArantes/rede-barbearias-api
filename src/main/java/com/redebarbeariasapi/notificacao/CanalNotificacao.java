package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.model.CanalNotificacaoTipo;

/** Um meio de entrega (e-mail, WhatsApp...). Novo canal = nova implementacao, nada mais muda. */
public interface CanalNotificacao {

    CanalNotificacaoTipo tipo();

    /** Configurado no servidor (credenciais presentes)? */
    boolean configurado();

    /** Destino desse cliente nesse canal, ou null se ele nao tem (ex.: sem e-mail). */
    String destino(Mensagem m);

    /** Envia de verdade. Lanca excecao com mensagem legivel se falhar. */
    void enviar(Mensagem m, String destino) throws Exception;

    /** Texto curto pra tela de configuracao. */
    String descricao();
}
