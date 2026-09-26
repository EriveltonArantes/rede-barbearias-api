package com.redebarbeariasapi.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Neon, Render, Supabase e Railway entregam o banco como
 * {@code postgresql://usuario:senha@host:5432/banco?sslmode=require}, mas o Spring quer
 * {@code jdbc:postgresql://...} com usuario e senha separados. Converte na partida, pra
 * bastar colar a URL do provedor em DATABASE_URL.
 */
public final class BancoUrl {

    private BancoUrl() {}

    /** Devolve url/username/password prontos pro Spring, ou null se nao precisa converter. */
    public static Map<String, String> converter(String url) {
        if (url == null || url.isBlank() || url.startsWith("jdbc:")) return null;
        if (!url.startsWith("postgres://") && !url.startsWith("postgresql://")) return null;
        URI u = URI.create(url.replaceFirst("^postgres(ql)?://", "http://"));
        String usuario = "", senha = "";
        if (u.getRawUserInfo() != null) {
            String[] partes = u.getRawUserInfo().split(":", 2);
            usuario = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
            senha = partes.length > 1 ? URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "";
        }
        String query = u.getRawQuery();
        // provedor na nuvem sempre exige SSL; se a URL nao disser nada, pede
        if (query == null || !query.contains("sslmode")) query = (query == null ? "" : query + "&") + "sslmode=require";
        String jdbc = "jdbc:postgresql://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") + u.getRawPath() + "?" + query;
        return Map.of("url", jdbc, "username", usuario, "password", senha);
    }

    /** Aplica como propriedades de sistema (valem acima do application.properties). */
    public static void aplicar(String url) {
        Map<String, String> c = converter(url);
        if (c == null) return;
        System.setProperty("spring.datasource.url", c.get("url"));
        if (!c.get("username").isEmpty()) System.setProperty("spring.datasource.username", c.get("username"));
        if (!c.get("password").isEmpty()) System.setProperty("spring.datasource.password", c.get("password"));
    }
}
