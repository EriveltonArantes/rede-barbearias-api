package com.redebarbeariasapi.service;

import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.notificacao.CanalNotificacao;
import com.redebarbeariasapi.notificacao.Mensagem;
import com.redebarbeariasapi.notificacao.WhatsAppCanal;
import com.redebarbeariasapi.repository.BarbeiroRepository;
import com.redebarbeariasapi.repository.ClienteRepository;
import com.redebarbeariasapi.repository.RecuperacaoSenhaRepository;
import com.redebarbeariasapi.repository.UsuarioRepository;
import com.redebarbeariasapi.security.Sessao;
import com.redebarbeariasapi.security.UsuarioLogado;
import com.redebarbeariasapi.sistema.Saude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * "Esqueci minha senha" sem depender de ninguem:
 * 1) a pessoa digita usuario, celular ou e-mail;
 * 2) chega um codigo de 6 digitos no WhatsApp e/ou e-mail cadastrado (vale 30 min, 5 tentativas);
 * 3) digita o codigo + senha nova e ja entra.
 * Se nenhum canal automatico estiver ligado (ou nao chegar), o pedido aparece no painel
 * e a recepcao manda o codigo pelo WhatsApp da barbearia com 1 clique.
 */
@Service
public class RecuperacaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);
    private static final int VALIDADE_MIN = 30;
    private static final int VALIDADE_EQUIPE_HORAS = 24;
    private static final int MAX_PEDIDOS_15MIN = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String RESPOSTA_PADRAO = "Se encontrarmos esse cadastro, enviamos um código de 6 números para o WhatsApp e/ou e-mail cadastrado. "
            + "Ele vale por 30 minutos. Não chegou? Fale com a barbearia: eles conseguem te enviar o código na hora.";

    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final BarbeiroRepository barbeiros;
    private final RecuperacaoSenhaRepository repo;
    private final PasswordEncoder encoder;
    private final WhatsAppCanal whatsapp;
    private final List<CanalNotificacao> canais;
    private final MarcaService marca;
    private final AuditoriaService auditoria;
    private final Saude saude;
    private final String siteUrl;

    public RecuperacaoSenhaService(UsuarioRepository usuarios, ClienteRepository clientes, BarbeiroRepository barbeiros,
                                   RecuperacaoSenhaRepository repo, PasswordEncoder encoder, WhatsAppCanal whatsapp,
                                   List<CanalNotificacao> canais, MarcaService marca, AuditoriaService auditoria, Saude saude,
                                   @Value("${app.site-url}") String siteUrl) {
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.barbeiros = barbeiros;
        this.repo = repo;
        this.encoder = encoder;
        this.whatsapp = whatsapp;
        this.canais = canais;
        this.marca = marca;
        this.auditoria = auditoria;
        this.saude = saude;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    // ------------------------------------------------------------------ localizar a conta

    /** Acha a conta pelo usuario, pelo celular ou pelo e-mail (o cliente nem sempre lembra o usuario). */
    @Transactional(readOnly = true)
    public Optional<Usuario> localizar(String login) {
        if (login == null || login.isBlank()) return Optional.empty();
        String l = login.trim();
        Optional<Usuario> direto = usuarios.findByUsernameIgnoreCase(l);
        if (direto.isPresent()) return direto;

        List<Usuario> achados = new ArrayList<>();
        if (l.contains("@")) {
            achados.addAll(usuarios.findByEmailIgnoreCase(l));
            clientes.findByEmailIgnoreCase(l).forEach(c -> usuarios.findByClienteId(c.getId()).ifPresent(achados::add));
            barbeiros.findAll().stream().filter(b -> l.equalsIgnoreCase(b.getEmail()))
                    .forEach(b -> achados.addAll(usuarios.findByBarbeiroId(b.getId())));
        } else {
            String digitos = l.replaceAll("\\D", "");
            if (digitos.length() >= 10 && l.replaceAll("[\\d\\s()+.-]", "").isEmpty()) {
                String tel;
                try {
                    tel = Textos.telefone(digitos);
                } catch (ValidacaoException e) {
                    return Optional.empty();
                }
                achados.addAll(usuarios.findByTelefone(tel));
                clientes.findByTelefone(tel).flatMap(c -> usuarios.findByClienteId(c.getId())).ifPresent(achados::add);
                barbeiros.findAll().stream().filter(b -> b.getTelefone() != null && tel.equals(soDigitos(b.getTelefone())))
                        .forEach(b -> achados.addAll(usuarios.findByBarbeiroId(b.getId())));
            }
        }
        // mais de uma conta ativa com o mesmo contato: ambiguo, melhor pedir o usuario
        List<Usuario> ativos = achados.stream().filter(Usuario::isAtivo).distinct().toList();
        return ativos.size() == 1 ? Optional.of(ativos.get(0)) : Optional.empty();
    }

    // ------------------------------------------------------------------ pedido pelo site

    /** Sempre responde igual (nao revela se a conta existe). */
    @Transactional
    public String pedir(String login) {
        Optional<Usuario> achado = localizar(login);
        if (achado.isEmpty() || !achado.get().isAtivo()) return RESPOSTA_PADRAO;
        Usuario u = achado.get();
        if (repo.countByUsuarioIdAndCriadoEmAfter(u.getId(), LocalDateTime.now().minusMinutes(15)) >= MAX_PEDIDOS_15MIN) {
            return RESPOSTA_PADRAO;
        }
        String codigo = codigo();
        RecuperacaoSenha r = novo(u, codigo, LocalDateTime.now().plusMinutes(VALIDADE_MIN), "CLIENTE");
        r.setEntreguePor(entregar(u, codigo));
        auditoria.registrar("PEDIR_CODIGO_SENHA", "Usuario", u.getId(),
                u.getUsername() + (r.getEntreguePor() == null ? " (nenhum canal automatico: aguardando a equipe)" : " via " + r.getEntreguePor()));
        return RESPOSTA_PADRAO;
    }

    @Transactional(noRollbackFor = ValidacaoException.class)
    public Usuario redefinir(String login, String codigo, String novaSenha) {
        if (novaSenha == null || novaSenha.length() < 6) throw new ValidacaoException("A nova senha precisa ter pelo menos 6 caracteres.");
        String cod = codigo == null ? "" : codigo.replaceAll("\\D", "");
        Usuario u = localizar(login).filter(Usuario::isAtivo)
                .orElseThrow(() -> new ValidacaoException("Código inválido ou vencido. Peça um novo."));
        LocalDateTime agora = LocalDateTime.now();
        RecuperacaoSenha r = repo.findFirstByUsuarioIdOrderByCriadoEmDesc(u.getId())
                .filter(x -> x.ativo(agora))
                .orElseThrow(() -> new ValidacaoException("Código inválido ou vencido. Peça um novo."));
        if (cod.length() != 6 || !encoder.matches(cod, r.getCodigoHash())) {
            r.setTentativas(r.getTentativas() + 1);
            int restam = 5 - r.getTentativas();
            throw new ValidacaoException(restam > 0 ? "Código incorreto. Você ainda tem " + restam + (restam == 1 ? " tentativa." : " tentativas.")
                    : "Código incorreto. Peça um novo código.");
        }
        u.setPassword(encoder.encode(novaSenha));
        u.setUltimoLogin(agora);
        r.setUsadoEm(agora);
        repo.substituirAbertos(u.getId());
        auditoria.registrar("REDEFINIR_SENHA", "Usuario", u.getId(), u.getUsername() + " (codigo " + r.getOrigem().toLowerCase() + ")");
        return u;
    }

    // ------------------------------------------------------------------ equipe ajudando

    /** Pedidos das ultimas 24h que ninguem concluiu: a recepcao ve e manda o codigo. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendentes() {
        UsuarioLogado eu = Sessao.atual();
        LocalDateTime agora = LocalDateTime.now();
        Map<Long, Map<String, Object>> porUsuario = new LinkedHashMap<>();
        for (RecuperacaoSenha r : repo.pendentes(agora.minusHours(24))) {
            Usuario u = r.getUsuario();
            if (porUsuario.containsKey(u.getId()) || !podeAtender(eu, u)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("usuarioId", u.getId());
            m.put("username", u.getUsername());
            m.put("nome", nome(u));
            m.put("papel", u.getPapel());
            m.put("telefone", telefone(u));
            m.put("pedidoEm", r.getCriadoEm());
            m.put("entreguePor", r.getEntreguePor());
            m.put("vencido", !r.ativo(agora));
            porUsuario.put(u.getId(), m);
        }
        return new ArrayList<>(porUsuario.values());
    }

    /** A equipe gera o codigo e manda pelo WhatsApp da barbearia (wa.me). Vale 24h. */
    @Transactional
    public Map<String, Object> gerarPelaEquipe(Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> ResourceNotFoundException.de("Usuário", usuarioId));
        UsuarioLogado eu = Sessao.atual();
        if (!podeAtender(eu, u)) throw new AccessDeniedException("Fora da sua gestão");
        if (!u.isAtivo()) throw new ValidacaoException("Essa conta está desativada. Ative antes de gerar o código.");
        String codigo = codigo();
        RecuperacaoSenha r = novo(u, codigo, LocalDateTime.now().plusHours(VALIDADE_EQUIPE_HORAS), "BARBEARIA");
        r.setAtendidoPor(eu.username());
        // os pedidos do site desse usuario saem da lista de pendentes
        auditoria.registrar("GERAR_CODIGO_SENHA", "Usuario", u.getId(), u.getUsername() + " por " + eu.username());
        String login = u.getUsername();
        String texto = "Olá, " + primeiroNome(nome(u)) + "! Seu código pra criar uma nova senha em " + marca.nome() + " é *" + codigo + "* (vale 24h).\n\n"
                + "1) Abra " + siteUrl + "/#/login\n2) Toque em \"Esqueci minha senha\" > \"Já tenho um código\"\n"
                + "3) Usuário: " + login + "\n\nSe não foi você quem pediu, pode ignorar esta mensagem.";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("validoAte", r.getExpiraEm());
        m.put("username", login);
        m.put("telefone", telefone(u));
        m.put("mensagem", texto);
        return m;
    }

    private static boolean podeAtender(UsuarioLogado eu, Usuario u) {
        if (eu.papel() == Papel.ADMIN) return true;
        if (u.getPapel() == Papel.CLIENTE) return eu.papel() == Papel.GERENTE || eu.papel() == Papel.RECEPCAO;
        if (eu.papel() != Papel.GERENTE || eu.unidadeId() == null) return false;
        if (u.getPapel() != Papel.RECEPCAO && u.getPapel() != Papel.BARBEIRO) return false;
        Long unidade = u.getUnidade() != null ? u.getUnidade().getId()
                : u.getBarbeiro() != null ? u.getBarbeiro().getUnidade().getId() : null;
        return eu.unidadeId().equals(unidade);
    }

    // ------------------------------------------------------------------ interno

    private RecuperacaoSenha novo(Usuario u, String codigo, LocalDateTime expira, String origem) {
        repo.substituirAbertos(u.getId());
        RecuperacaoSenha r = new RecuperacaoSenha();
        r.setUsuario(u);
        r.setCodigoHash(encoder.encode(codigo));
        r.setExpiraEm(expira);
        r.setOrigem(origem);
        return repo.save(r);
    }

    /** Manda por todo canal que tiver; devolve onde chegou (mascarado) ou null. */
    private String entregar(Usuario u, String codigo) {
        List<String> ok = new ArrayList<>();
        String tel = telefone(u);
        if (whatsapp.configurado() && tel != null && !bloqueadoNoWhatsApp(u)) {
            try {
                whatsapp.enviarCodigo(tel.startsWith("55") ? tel : "55" + tel, codigo);
                ok.add("WhatsApp " + mascararTelefone(tel));
                saude.ok("recuperacao-senha");
            } catch (Exception e) {
                log.warn("Codigo de senha nao foi pelo WhatsApp: {}", e.getMessage());
                saude.falha("recuperacao-senha", "WhatsApp: " + e.getMessage());
            }
        }
        String mail = email(u);
        CanalNotificacao email = canais.stream().filter(c -> c.tipo() == CanalNotificacaoTipo.EMAIL && c.configurado()).findFirst().orElse(null);
        if (email != null && mail != null) {
            try {
                email.enviar(mensagemEmail(u, mail, codigo), mail);
                ok.add("e-mail " + mascararEmail(mail));
                saude.ok("recuperacao-senha");
            } catch (Exception e) {
                log.warn("Codigo de senha nao foi por e-mail: {}", e.getMessage());
                saude.falha("recuperacao-senha", "E-mail: " + e.getMessage());
            }
        }
        return ok.isEmpty() ? null : String.join(" e ", ok);
    }

    private Mensagem mensagemEmail(Usuario u, String destino, String codigo) {
        String nome = primeiroNome(nome(u));
        String loja = marca.nome();
        String assunto = "Seu código pra criar uma nova senha — " + loja;
        String html = "<!doctype html><html><body style=\"margin:0;background:#f4efe6;font-family:Arial,sans-serif;color:#2a2118\">"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\" style=\"padding:24px 12px\">"
                + "<table width=\"100%\" style=\"max-width:480px;background:#ffffff;border-radius:14px\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"padding:28px\">"
                + "<p style=\"margin:0 0 6px;font-size:13px;color:#8a7f72\">" + esc(loja) + "</p>"
                + "<h1 style=\"margin:0 0 14px;font-size:21px\">Olá, " + esc(nome) + "!</h1>"
                + "<p style=\"margin:0 0 18px;font-size:15px;line-height:1.5\">Use este código pra criar uma nova senha. Ele vale por " + VALIDADE_MIN + " minutos:</p>"
                + "<p style=\"margin:0 0 18px;font-size:34px;font-weight:bold;letter-spacing:8px;text-align:center;background:#f4efe6;border-radius:10px;padding:14px\">" + codigo + "</p>"
                + "<p style=\"margin:0 0 6px;font-size:14px\">Seu usuário: <b>" + esc(u.getUsername()) + "</b></p>"
                + "<p style=\"margin:18px 0 0;font-size:12px;color:#8a7f72\">Não foi você que pediu? Pode ignorar este e-mail — sua senha continua a mesma.</p>"
                + "</td></tr></table></td></tr></table></body></html>";
        String texto = "Olá, " + nome + "!\n\nSeu código pra criar uma nova senha em " + loja + ": " + codigo + " (vale " + VALIDADE_MIN + " minutos).\n"
                + "Seu usuário: " + u.getUsername() + "\n\nNão foi você que pediu? Pode ignorar este e-mail.";
        return new Mensagem(null, nome(u), destino, telefone(u), assunto, html, texto, null, List.of(), List.of(), true);
    }

    private static boolean bloqueadoNoWhatsApp(Usuario u) {
        return u.getCliente() != null && u.getCliente().isWhatsappBloqueado();
    }

    static String telefone(Usuario u) {
        if (!Textos.vazio(u.getTelefone())) return soDigitos(u.getTelefone());
        if (u.getBarbeiro() != null && !Textos.vazio(u.getBarbeiro().getTelefone())) return soDigitos(u.getBarbeiro().getTelefone());
        if (u.getCliente() != null && u.getCliente().getAnonimizadoEm() == null && !Textos.vazio(u.getCliente().getTelefone())) {
            return soDigitos(u.getCliente().getTelefone());
        }
        return null;
    }

    static String email(Usuario u) {
        if (!Textos.vazio(u.getEmail())) return u.getEmail().trim();
        if (u.getBarbeiro() != null && !Textos.vazio(u.getBarbeiro().getEmail())) return u.getBarbeiro().getEmail().trim();
        if (u.getCliente() != null && u.getCliente().getAnonimizadoEm() == null && !Textos.vazio(u.getCliente().getEmail())) {
            return u.getCliente().getEmail().trim();
        }
        return null;
    }

    private static String nome(Usuario u) {
        if (!Textos.vazio(u.getNome())) return u.getNome();
        if (u.getBarbeiro() != null) return u.getBarbeiro().getNome();
        if (u.getCliente() != null) return u.getCliente().getNome();
        return u.getUsername();
    }

    private static String primeiroNome(String n) {
        return n == null || n.isBlank() ? "" : n.trim().split("\\s+")[0];
    }

    private static String soDigitos(String s) {
        String d = s.replaceAll("\\D", "");
        return d.length() > 11 && d.startsWith("55") ? d.substring(2) : d;
    }

    static String mascararTelefone(String tel) {
        String d = soDigitos(tel);
        return d.length() < 4 ? "••••" : "•••••-" + d.substring(d.length() - 4);
    }

    static String mascararEmail(String e) {
        int arroba = e.indexOf('@');
        if (arroba < 1) return "•••";
        return e.charAt(0) + "•••" + e.substring(arroba);
    }

    private static String codigo() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
