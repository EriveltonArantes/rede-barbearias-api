package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Identidade da barbearia (linha unica, id = 1): nome, logo, cores e contatos.
 * O site, o painel, o app instalado no celular e as mensagens usam daqui —
 * trocar de cliente e so preencher esta tela, sem mexer no codigo.
 */
@Entity
@Table(name = "marca")
@Getter @Setter
public class Marca {

    public static final String NOME_PADRAO = "Rede Barbearias";
    public static final String COR_PRINCIPAL_PADRAO = "#d4a843";
    public static final String COR_DESTAQUE_PADRAO = "#c0392b";

    @Id
    private Long id = 1L;

    @Column(nullable = false, columnDefinition = "varchar(80) default 'Rede Barbearias'")
    private String nome = NOME_PADRAO;
    /** Nome embaixo do icone no celular (ate 12 letras cabe sem cortar). */
    @Column(length = 20)
    private String nomeCurto;
    @Column(length = 120)
    private String slogan = "Corte, barba e estilo — agende em 1 minuto";
    @Column(length = 600)
    private String sobre;
    /** "/api/arquivos/12" (enviado pelo painel) ou URL externa. Sem logo: usa o emoji. */
    private String logoUrl;
    @Column(length = 8)
    private String emoji = "💈";
    @Column(nullable = false, length = 7, columnDefinition = "varchar(7) default '#d4a843'")
    private String corPrincipal = COR_PRINCIPAL_PADRAO;
    @Column(nullable = false, length = 7, columnDefinition = "varchar(7) default '#c0392b'")
    private String corDestaque = COR_DESTAQUE_PADRAO;

    private String cidade = "Belo Horizonte";
    private String telefone;
    private String whatsapp;
    private String instagram;
    private String email;

    private LocalDateTime atualizadoEm;
    private String atualizadoPor;

    public String getNomeCurto() {
        if (nomeCurto != null && !nomeCurto.isBlank()) return nomeCurto;
        return nome.length() <= 12 ? nome : nome.substring(0, 12).strip();
    }
}
