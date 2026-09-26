package com.redebarbeariasapi.notificacao;

import com.redebarbeariasapi.model.TipoNotificacao;

import java.util.List;

/**
 * Uma mensagem pronta pra qualquer canal: assunto + HTML pro e-mail,
 * texto puro, e nome/parametros do modelo aprovado no WhatsApp oficial.
 */
public record Mensagem(TipoNotificacao tipo, String nomeCliente, String email, String telefone,
                       String assunto, String html, String texto,
                       String modeloWhatsApp, List<String> parametrosWhatsApp) {
}
