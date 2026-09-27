package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Pedido de "esqueci minha senha": codigo de 6 digitos (guardado so o hash), validade curta
 * e limite de tentativas. origem = CLIENTE (pediu no site) ou BARBEARIA (a equipe gerou pelo painel).
 */
@Entity
@Table(name = "recuperacoes_senha")
@Getter @Setter
public class RecuperacaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private String codigoHash;
    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
    @Column(nullable = false)
    private LocalDateTime expiraEm;
    private int tentativas;
    private LocalDateTime usadoEm;
    /** Outro pedido mais novo tomou o lugar deste. */
    private boolean substituido;

    @Column(nullable = false, length = 20)
    private String origem = "CLIENTE";
    /** Onde o codigo foi entregue ("WhatsApp •••• 8888", "e-mail j•••@gmail.com") ou vazio se nenhum canal chegou. */
    @Column(length = 200)
    private String entreguePor;
    /** Quem da equipe gerou/enviou (origem BARBEARIA). */
    private String atendidoPor;

    public boolean ativo(LocalDateTime agora) {
        return usadoEm == null && !substituido && expiraEm.isAfter(agora) && tentativas < 5;
    }
}
