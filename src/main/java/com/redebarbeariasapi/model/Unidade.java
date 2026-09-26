package com.redebarbeariasapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "unidades")
@Getter @Setter
public class Unidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)
    private String endereco;
    private String bairro;
    private String cidade;
    private String telefone;
    private String whatsapp;
    private String email;
    private String fotoUrl;

    @Column(nullable = false)
    private LocalTime horaAbertura = LocalTime.of(9, 0);
    @Column(nullable = false)
    private LocalTime horaFechamento = LocalTime.of(20, 0);
    /** Dias de funcionamento no padrao ISO (1=segunda ... 7=domingo), separados por virgula. */
    @Column(nullable = false)
    private String diasFuncionamento = "1,2,3,4,5,6";

    /** Chave Pix propria da unidade (se vazia, usa a chave da rede). */
    private String chavePix;
    private boolean ativa = true;

    public Set<DayOfWeek> dias() {
        return Dias.parse(diasFuncionamento);
    }
}
