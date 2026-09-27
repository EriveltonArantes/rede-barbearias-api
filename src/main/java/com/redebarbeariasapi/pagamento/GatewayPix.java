package com.redebarbeariasapi.pagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pix com confirmacao automatica (o gateway avisa quando cai). Sem gateway configurado o sinal
 * usa o Pix "copia e cola" estatico com a chave da unidade e a recepcao confirma no painel.
 */
public interface GatewayPix {

    boolean configurado();

    /** Cria a cobranca. referencia = codigo do agendamento (volta no webhook). */
    Cobranca criar(BigDecimal valor, String descricao, String emailPagador, String referencia, LocalDateTime expiraEm);

    /** Consulta direto no gateway — o webhook so diz "mudou algo", quem confirma e essa consulta. */
    Situacao consultar(String id);

    String nome();

    record Cobranca(String id, String copiaECola, String qrCodeBase64) {}

    record Situacao(String id, boolean aprovado, String referencia, BigDecimal valor) {}
}
