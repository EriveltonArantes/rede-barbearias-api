package com.redebarbeariasapi;

import com.redebarbeariasapi.config.BancoUrl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Colar a URL do Neon/Render em DATABASE_URL tem que funcionar sem mexer em nada. */
class BancoUrlTest {

    @Test
    void urlDoNeonViraJdbcComUsuarioESenhaSeparados() {
        Map<String, String> c = BancoUrl.converter("postgresql://barbearia_owner:s3nh%40forte@ep-cool-1234.sa-east-1.aws.neon.tech/barbearia?sslmode=require");
        assertThat(c.get("url")).isEqualTo("jdbc:postgresql://ep-cool-1234.sa-east-1.aws.neon.tech/barbearia?sslmode=require");
        assertThat(c.get("username")).isEqualTo("barbearia_owner");
        assertThat(c.get("password")).isEqualTo("s3nh@forte");
    }

    @Test
    void urlDoRenderComPortaGanhaSsl() {
        Map<String, String> c = BancoUrl.converter("postgres://user:pw@dpg-abc.oregon-postgres.render.com:5432/db");
        assertThat(c.get("url")).isEqualTo("jdbc:postgresql://dpg-abc.oregon-postgres.render.com:5432/db?sslmode=require");
    }

    @Test
    void urlJdbcOuVaziaFicaComoEsta() {
        assertThat(BancoUrl.converter("jdbc:postgresql://localhost/db")).isNull();
        assertThat(BancoUrl.converter(null)).isNull();
        assertThat(BancoUrl.converter("jdbc:h2:mem:x")).isNull();
    }
}
