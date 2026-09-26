package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Quem mandou mensagem no WhatsApp da barbearia: guarda a ultima mensagem e quando
 * a resposta automatica saiu, pra nao responder o mesmo cliente a cada "oi".
 */
@Entity
@Table(name = "conversas_whatsapp")
@Getter @Setter
public class ConversaWhatsApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numero como a Meta envia (wa_id, ex.: 5531999998888) — e pra ele que a resposta volta. */
    @Column(nullable = false, unique = true, length = 20)
    private String telefone;
    /** Nome do perfil do WhatsApp (ou do cadastro, quando ja e cliente). */
    private String nome;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(length = 500)
    private String ultimaMensagem;
    /** Id da ultima mensagem processada: a Meta reenvia o webhook se demorar, e nao pode responder duas vezes. */
    @Column(length = 120)
    private String ultimaMensagemId;
    private LocalDateTime ultimaRecebidaEm;
    private int totalRecebidas;

    private LocalDateTime ultimaRespostaEm;
    @Column(length = 1500)
    private String ultimaResposta;
    /** Pediu PARAR: o sistema nao manda mais nada automatico pra esse numero (ate ele mandar VOLTAR). */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean optOut;
    /** O que o cliente fez pela conversa por ultimo (confirmou, cancelou, pediu pra parar...), pra equipe ver. */
    @Column(length = 200)
    private String ultimaAcao;
    /** Ultimo aviso de "estamos fechados" — tem intervalo proprio, separado da saudacao. */
    private LocalDateTime ultimoForaHorarioEm;

    /** Erro da ultima tentativa de resposta (token vencido, janela de 24h...), pra aparecer no painel. */
    @Column(length = 500)
    private String erroResposta;
}
