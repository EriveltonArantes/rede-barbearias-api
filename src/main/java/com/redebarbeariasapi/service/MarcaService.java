package com.redebarbeariasapi.service;

import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.model.Arquivo;
import com.redebarbeariasapi.model.Marca;
import com.redebarbeariasapi.repository.ArquivoRepository;
import com.redebarbeariasapi.repository.MarcaRepository;
import com.redebarbeariasapi.security.Sessao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Nome, logo, cores e contatos da barbearia + os icones do app instalado no celular. */
@Service
public class MarcaService {

    private static final Logger log = LoggerFactory.getLogger(MarcaService.class);
    private static final String HEX = "^#[0-9a-fA-F]{6}$";
    public static final Set<Integer> TAMANHOS_ICONE = Set.of(180, 192, 512);

    private final MarcaRepository repo;
    private final ArquivoRepository arquivos;
    private final AuditoriaService auditoria;
    /** Icone gerado por (versao da marca + tamanho): gerar PNG a cada acesso seria desperdicio. */
    private final Map<String, byte[]> icones = new ConcurrentHashMap<>();
    private volatile String nomeCache;

    public MarcaService(MarcaRepository repo, ArquivoRepository arquivos, AuditoriaService auditoria) {
        this.repo = repo;
        this.arquivos = arquivos;
        this.auditoria = auditoria;
    }

    @Transactional
    public Marca atual() {
        return repo.findById(1L).orElseGet(() -> repo.save(new Marca()));
    }

    /** Nome da barbearia pras mensagens (sem ir no banco a cada mensagem). */
    public String nome() {
        String n = nomeCache;
        if (n == null) {
            try {
                n = repo.findById(1L).map(Marca::getNome).orElse(Marca.NOME_PADRAO);
            } catch (Exception e) {
                return Marca.NOME_PADRAO;
            }
            nomeCache = n;
        }
        return n;
    }

    /** Aplica so os campos enviados. */
    @Transactional
    public Marca salvar(Map<String, Object> c) {
        Marca m = atual();
        if (c.containsKey("nome")) {
            String n = texto(c.get("nome"), 80);
            if (n == null || n.length() < 2) throw new ValidacaoException("Informe o nome da barbearia.");
            m.setNome(n);
        }
        if (c.containsKey("nomeCurto")) m.setNomeCurto(texto(c.get("nomeCurto"), 20));
        if (c.containsKey("slogan")) m.setSlogan(texto(c.get("slogan"), 120));
        if (c.containsKey("sobre")) m.setSobre(texto(c.get("sobre"), 600));
        if (c.containsKey("logoUrl")) {
            String l = texto(c.get("logoUrl"), 255);
            if (l != null && !l.matches("^/api/arquivos/\\d+$") && !l.startsWith("https://")) {
                throw new ValidacaoException("Logo: envie a imagem pelo botão ou use um endereço https://");
            }
            m.setLogoUrl(l);
        }
        if (c.containsKey("emoji")) {
            String e = texto(c.get("emoji"), 8);
            m.setEmoji(e == null ? "💈" : e);
        }
        if (c.containsKey("corPrincipal")) m.setCorPrincipal(cor(c.get("corPrincipal"), "Cor principal"));
        if (c.containsKey("corDestaque")) m.setCorDestaque(cor(c.get("corDestaque"), "Cor de destaque"));
        if (c.containsKey("cidade")) m.setCidade(texto(c.get("cidade"), 80));
        if (c.containsKey("telefone")) m.setTelefone(texto(c.get("telefone"), 30));
        if (c.containsKey("whatsapp")) {
            String w = texto(c.get("whatsapp"), 30);
            m.setWhatsapp(w == null ? null : w.replaceAll("\\D", ""));
        }
        if (c.containsKey("instagram")) {
            String i = texto(c.get("instagram"), 80);
            if (i != null) i = i.replaceFirst("^https?://(www\\.)?instagram\\.com/", "").replace("@", "").replaceAll("[/?].*$", "");
            m.setInstagram(i == null || i.isBlank() ? null : i);
        }
        if (c.containsKey("email")) {
            String e = texto(c.get("email"), 150);
            if (e != null && !e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ValidacaoException("E-mail inválido.");
            m.setEmail(e);
        }
        m.setAtualizadoEm(LocalDateTime.now());
        m.setAtualizadoPor(Sessao.username());
        nomeCache = m.getNome();
        icones.clear();
        auditoria.registrar("CONFIGURAR", "Marca", 1L, "campos: " + String.join(", ", c.keySet()));
        return m;
    }

    @Transactional
    public Map<String, Object> publico() {
        return publico(atual());
    }

    private static Map<String, Object> publico(Marca m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("nome", m.getNome());
        r.put("nomeCurto", m.getNomeCurto());
        r.put("slogan", m.getSlogan());
        r.put("sobre", m.getSobre());
        r.put("logoUrl", m.getLogoUrl());
        r.put("emoji", m.getEmoji());
        r.put("corPrincipal", m.getCorPrincipal());
        r.put("corDestaque", m.getCorDestaque());
        r.put("cidade", m.getCidade());
        r.put("telefone", m.getTelefone());
        r.put("whatsapp", m.getWhatsapp());
        r.put("instagram", m.getInstagram());
        r.put("email", m.getEmail());
        r.put("versao", versao(m));
        return r;
    }

    private static String versao(Marca m) {
        return m.getAtualizadoEm() == null ? "0" : Integer.toString(m.getAtualizadoEm().hashCode() & 0x7fffffff, 36);
    }

    /**
     * Manifesto do app (PWA). origem = endereco do site que pediu; o app abre nele.
     * So aceita http(s)://host pra ninguem montar manifesto com javascript: etc.
     */
    @Transactional
    public Map<String, Object> manifesto(String origem, String apiUrl) {
        Marca m = atual();
        String site = origem != null && origem.matches("^https?://[A-Za-z0-9.-]+(:\\d+)?$") ? origem : "";
        String v = versao(m);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", site + "/");
        r.put("name", m.getNome());
        r.put("short_name", m.getNomeCurto());
        r.put("description", m.getSlogan() == null ? "Agende seu horário" : m.getSlogan());
        r.put("lang", "pt-BR");
        r.put("start_url", site + "/#/");
        r.put("scope", site + "/");
        r.put("display", "standalone");
        r.put("orientation", "portrait");
        r.put("background_color", "#0b0a09");
        r.put("theme_color", "#0b0a09");
        List<Map<String, String>> lista = new ArrayList<>();
        for (int t : List.of(192, 512)) {
            String src = apiUrl + "/api/publico/icone/" + t + ".png?v=" + v;
            lista.add(Map.of("src", src, "sizes", t + "x" + t, "type", "image/png", "purpose", "any"));
            lista.add(Map.of("src", src + "&maskable=true", "sizes", t + "x" + t, "type", "image/png", "purpose", "maskable"));
        }
        r.put("icons", lista);
        r.put("shortcuts", List.of(
                Map.of("name", "Agendar horário", "url", site + "/#/agendar"),
                Map.of("name", "Minha conta", "url", site + "/#/minha-conta")));
        return r;
    }

    /** PNG quadrado: logo enviada centralizada ou, sem logo, as iniciais num circulo com as cores da marca. */
    @Transactional(readOnly = true)
    public byte[] icone(int tamanho, boolean maskable) {
        int t = TAMANHOS_ICONE.contains(tamanho) ? tamanho : 192;
        Marca m = repo.findById(1L).orElseGet(Marca::new);
        String chave = versao(m) + "|" + t + "|" + maskable;
        return icones.computeIfAbsent(chave, k -> {
            try {
                return desenhar(m, t, maskable);
            } catch (Throwable e) {
                // sem fonte no servidor ou logo ilegivel: quadrado na cor da marca, nunca erro 500
                log.warn("Icone gerado sem texto: {}", e.toString());
                return quadrado(m, t);
            }
        });
    }

    private byte[] desenhar(Marca m, int t, boolean maskable) throws Exception {
        BufferedImage img = new BufferedImage(t, t, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        Color principal = Color.decode(m.getCorPrincipal());
        Color destaque = Color.decode(m.getCorDestaque());
        g.setPaint(new GradientPaint(0, 0, new Color(0x1c1814), t, t, new Color(0x0b0a09)));
        g.fillRect(0, 0, t, t);
        // o Android recorta o icone "maskable" em circulo: desenha dentro da area segura
        double area = maskable ? 0.62 : 0.80;
        BufferedImage logo = lerLogo(m.getLogoUrl());
        if (logo != null) {
            double escala = Math.min(t * area / logo.getWidth(), t * area / logo.getHeight());
            int w = (int) Math.round(logo.getWidth() * escala), h = (int) Math.round(logo.getHeight() * escala);
            g.drawImage(logo, (t - w) / 2, (t - h) / 2, w, h, null);
        } else {
            int d = (int) (t * area);
            g.setPaint(new GradientPaint(0, 0, principal, t, t, destaque));
            g.fillOval((t - d) / 2, (t - d) / 2, d, d);
            String ini = iniciais(m.getNome());
            g.setColor(new Color(0x0b0a09));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (d * (ini.length() > 1 ? 0.40 : 0.55))));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(ini, (t - fm.stringWidth(ini)) / 2, (t - fm.getHeight()) / 2 + fm.getAscent());
        }
        g.dispose();
        return png(img);
    }

    private static byte[] quadrado(Marca m, int t) {
        try {
            BufferedImage img = new BufferedImage(t, t, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.decode(m.getCorPrincipal()));
            g.fillRect(0, 0, t, t);
            g.dispose();
            return png(img);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private BufferedImage lerLogo(String url) {
        if (url == null || !url.matches("^/api/arquivos/\\d+$")) return null;
        try {
            Arquivo a = arquivos.findById(Long.valueOf(url.substring("/api/arquivos/".length()))).orElse(null);
            return a == null ? null : ImageIO.read(new ByteArrayInputStream(a.getDados()));
        } catch (Exception e) {
            return null;
        }
    }

    /** "Rede Barbearias" -> "RB"; "Barbearia do Zé" -> "BZ"; "Studio" -> "S". Ignora "da/do/de". */
    static String iniciais(String nome) {
        List<String> palavras = Arrays.stream(nome.trim().split("\\s+"))
                .filter(p -> !p.isBlank() && Character.isLetterOrDigit(p.codePointAt(0)))
                .filter(p -> p.length() > 2 || p.chars().allMatch(Character::isUpperCase))
                .toList();
        if (palavras.isEmpty()) return nome.isBlank() ? "B" : nome.trim().substring(0, 1).toUpperCase();
        if (palavras.size() == 1) return palavras.get(0).substring(0, 1).toUpperCase();
        return (palavras.get(0).substring(0, 1) + palavras.get(1).substring(0, 1)).toUpperCase();
    }

    private static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static String cor(Object o, String campo) {
        String s = o == null ? "" : String.valueOf(o).trim();
        if (!s.matches(HEX)) throw new ValidacaoException(campo + ": use o formato #RRGGBB.");
        return s.toLowerCase();
    }

    private static String texto(Object o, int max) {
        if (o == null) return null;
        String s = String.valueOf(o).strip();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
