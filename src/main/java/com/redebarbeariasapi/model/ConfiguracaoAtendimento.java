package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Resposta automatica do WhatsApp, editavel pelo painel. Linha unica (id = 1). */
@Entity
@Table(name = "configuracao_atendimento")
@Getter @Setter
public class ConfiguracaoAtendimento {

    public static final String SAUDACAO_PADRAO = """
            Olá, {nome}! 💈 Seja bem-vindo(a) à Rede Barbearias.

            Pra agendar seu horário é rapidinho: escolha a unidade, o barbeiro e o horário por aqui 👇
            {link_agendar}

            Se preferir falar com a gente, é só mandar sua mensagem que já te respondemos 😉""";

    public static final String FORA_HORARIO_PADRAO = """
            Olá, {nome}! 💈 Agora estamos fechados — voltamos {abre}.

            Mas você já pode garantir seu horário agora mesmo, é rapidinho 👇
            {link_agendar}

            Sua mensagem fica aqui e respondemos assim que abrirmos 😉""";

    @Id
    private Long id = 1L;

    private boolean respostaAutomatica = true;

    @Column(nullable = false, length = 1000)
    private String saudacao = SAUDACAO_PADRAO;

    /** Responde de novo o mesmo cliente so depois desse tempo (evita responder cada mensagem da conversa). */
    private int intervaloHoras = 12;

    /** Quando o cliente ja tem horario marcado, acrescenta os dados dele e o link pra ver/cancelar. */
    private boolean mostrarProximoHorario = true;

    /** Fora do horario de todas as unidades, responde com a mensagem de "fechados" (com o link pra agendar). */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean foraHorarioAtivo = true;

    @Column(length = 1000)
    private String mensagemForaHorario = FORA_HORARIO_PADRAO;

    private LocalDateTime atualizadoEm;
    private String atualizadoPor;

    public String getMensagemForaHorario() {
        return mensagemForaHorario == null || mensagemForaHorario.isBlank() ? FORA_HORARIO_PADRAO : mensagemForaHorario;
    }
}
