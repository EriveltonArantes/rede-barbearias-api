package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Regras da rede editaveis pelo painel (linha unica, id = 1): sinal por Pix, lista de espera,
 * mensagens de aniversario/retorno e dados da politica de privacidade.
 * Toda coluna tem default no banco: o ddl-auto=update consegue acrescentar em tabela com dados.
 */
@Entity
@Table(name = "configuracao_rede")
@Getter @Setter
public class ConfiguracaoRede {

    @Id
    private Long id = 1L;

    // ---------------- sinal (reduz falta) ----------------
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean sinalAtivo;
    @Column(nullable = false, precision = 10, scale = 2, columnDefinition = "numeric(10,2) default 10")
    private BigDecimal sinalValor = new BigDecimal("10.00");
    /** Exigir em sabado e domingo (horarios mais disputados). */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean sinalFimDeSemana = true;
    /** Exigir de quem faltou sem avisar nos ultimos 180 dias. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean sinalQuemFaltou = true;
    /** Exigir em todo agendamento online. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean sinalSempre;
    /** Tempo pra pagar depois de agendar. */
    @Column(nullable = false, columnDefinition = "integer default 60")
    private int sinalPrazoMinutos = 60;
    /** Sem pagamento no prazo, cancela sozinho e libera o horario (so faz sentido com confirmacao automatica). */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean sinalCancelarSemPagamento;
    /** Cancelou com pelo menos essa antecedencia: o sinal e devolvido. Menos que isso (ou falta): fica com a barbearia. */
    @Column(nullable = false, columnDefinition = "integer default 24")
    private int sinalDevolucaoHoras = 24;

    // ---------------- lista de espera ----------------
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean esperaAtiva = true;
    /** Quantas pessoas da fila sao avisadas quando abre um horario (quem agendar primeiro leva). */
    @Column(nullable = false, columnDefinition = "integer default 3")
    private int esperaAvisarQuantos = 3;

    // ---------------- relacionamento ----------------
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean aniversarioAtivo = true;
    @Column(nullable = false, precision = 5, scale = 2, columnDefinition = "numeric(5,2) default 15")
    private BigDecimal aniversarioDesconto = new BigDecimal("15");
    @Column(nullable = false, columnDefinition = "integer default 30")
    private int aniversarioValidadeDias = 30;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean retornoAtivo = true;
    /** Manda o "bora voltar?" quando a ultima visita faz esse tanto de dias (e nao ha horario marcado). */
    @Column(nullable = false, columnDefinition = "integer default 30")
    private int retornoDias = 30;
    /** Desconto do cupom de retorno (0 = mensagem sem cupom). */
    @Column(nullable = false, precision = 5, scale = 2, columnDefinition = "numeric(5,2) default 10")
    private BigDecimal retornoDesconto = new BigDecimal("10");
    @Column(nullable = false, columnDefinition = "integer default 15")
    private int retornoValidadeDias = 15;
    /** Hora do dia em que saem as mensagens de aniversario e retorno. */
    @Column(nullable = false, columnDefinition = "integer default 10")
    private int horaMensagens = 10;

    // ---------------- privacidade (LGPD) ----------------
    private String razaoSocial;
    private String cnpj;
    /** Contato do encarregado de dados (quem responde pedidos de acesso/exclusao). */
    private String emailPrivacidade;

    private LocalDateTime atualizadoEm;
    private String atualizadoPor;
}
