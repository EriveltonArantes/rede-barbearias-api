package com.redebarbeariasapi;

import com.redebarbeariasapi.config.BancoUrl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class RedeBarbeariasApiApplication {
    public static void main(String[] args) {
        // servidor na nuvem roda em UTC; a agenda da barbearia e em horario de Brasilia
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        // aceita a URL do provedor (postgresql://usuario:senha@host/banco) direto em DATABASE_URL
        BancoUrl.aplicar(System.getenv("DATABASE_URL"));
        SpringApplication.run(RedeBarbeariasApiApplication.class, args);
    }
}
