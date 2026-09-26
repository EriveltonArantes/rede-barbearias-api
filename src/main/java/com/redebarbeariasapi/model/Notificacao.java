package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Historico de cada mensagem automatica — evita mandar duas vezes e mostra pra equipe o que o cliente recebeu. */
@Entity
@Table(name = "notificacoes", indexes = @Index(name = "idx_notif_agendamento", columnList = "agendamento_id"))
@Getter @Setter
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "agendamento_id")
    private Agendamento agendamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoNotificacao tipo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalNotificacaoTipo canal;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusNotificacao status;

    @Column(nullable = false)
    private String destino;
    /** Horario do agendamento quando a mensagem saiu: se o horario mudar, lembrete/confirmacao valem de novo. */
    @Column(nullable = false, length = 30)
    private String referencia;
    @Column(length = 500)
    private String erro;
    @Column(nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();
}
