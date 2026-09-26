package com.redebarbeariasapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Documentacao Swagger com botao "Authorize" (JWT) + tarefas agendadas (clube, lembretes) + envio assincrono de mensagens. */
@Configuration
@EnableScheduling
@EnableAsync
public class WebConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Rede Barbearias API").version("1.0")
                        .description("Agenda multi-unidade, agendamento online, clientes e fidelidade, clube de assinatura, "
                                + "estoque e PDV, financeiro com comissões e caixa. Faça login em /api/auth/login e use o token."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
