package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.LoginRequestDTO;
import com.redebarbeariasapi.dto.LoginResponseDTO;
import com.redebarbeariasapi.dto.RegistroClienteDTO;
import com.redebarbeariasapi.dto.TrocarSenhaDTO;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.model.Cliente;
import com.redebarbeariasapi.model.Papel;
import com.redebarbeariasapi.model.Usuario;
import com.redebarbeariasapi.repository.ClienteRepository;
import com.redebarbeariasapi.repository.UsuarioRepository;
import com.redebarbeariasapi.security.JwtUtil;
import com.redebarbeariasapi.security.Sessao;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final ClienteService clienteService;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    private final AuditoriaService auditoria;

    public Optional<LoginResponseDTO> login(LoginRequestDTO dto) {
        return usuarios.findByUsernameIgnoreCase(dto.username().trim())
                .filter(Usuario::isAtivo)
                .filter(u -> encoder.matches(dto.password(), u.getPassword()))
                .map(u -> {
                    u.setUltimoLogin(LocalDateTime.now());
                    return resposta(u, jwt.gerarToken(u.getUsername(), u.getPapel().name()));
                });
    }

    /** Auto-cadastro do cliente no app. Se o telefone ja existe (cliente de balcao), a conta e vinculada a ficha dele. */
    public LoginResponseDTO registrarCliente(RegistroClienteDTO dto) {
        if (usuarios.existsByUsernameIgnoreCase(dto.username())) throw new BusinessException("Esse usuário já existe. Escolha outro.");
        String tel = Textos.telefone(dto.telefone());
        Cliente c = clientes.findByTelefone(tel).orElse(null);
        if (c != null && usuarios.findByClienteId(c.getId()).isPresent()) {
            throw new BusinessException("Já existe uma conta pra esse telefone. Faça login ou fale com a barbearia.");
        }
        if (c == null) c = clienteService.obterOuCriar(dto.nome(), tel, dto.email());
        if (c.getDataNascimento() == null) c.setDataNascimento(dto.dataNascimento());
        if (Textos.vazio(c.getEmail()) && !Textos.vazio(dto.email())) c.setEmail(dto.email().trim());

        Usuario u = new Usuario();
        u.setUsername(dto.username().trim().toLowerCase());
        u.setPassword(encoder.encode(dto.password()));
        u.setNome(dto.nome().trim());
        u.setPapel(Papel.CLIENTE);
        u.setCliente(c);
        u.setUltimoLogin(LocalDateTime.now());
        usuarios.save(u);
        auditoria.registrar("REGISTRAR", "Usuario", u.getId(), "cliente " + c.getNome());
        return resposta(u, jwt.gerarToken(u.getUsername(), u.getPapel().name()));
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO eu() {
        Usuario u = usuarios.findById(Sessao.atual().id()).orElseThrow();
        return resposta(u, null);
    }

    public void trocarSenha(TrocarSenhaDTO dto) {
        Usuario u = usuarios.findById(Sessao.atual().id()).orElseThrow();
        if (!encoder.matches(dto.senhaAtual(), u.getPassword())) throw new ValidacaoException("Senha atual incorreta.");
        if (dto.senhaAtual().equals(dto.novaSenha())) throw new ValidacaoException("A nova senha precisa ser diferente da atual.");
        u.setPassword(encoder.encode(dto.novaSenha()));
        auditoria.registrar("TROCAR_SENHA", "Usuario", u.getId(), u.getUsername());
    }

    private static LoginResponseDTO resposta(Usuario u, String token) {
        Long unidadeId = u.getUnidade() != null ? u.getUnidade().getId()
                : u.getBarbeiro() != null ? u.getBarbeiro().getUnidade().getId() : null;
        String unidadeNome = u.getUnidade() != null ? u.getUnidade().getNome()
                : u.getBarbeiro() != null ? u.getBarbeiro().getUnidade().getNome() : null;
        String nome = u.getNome() != null ? u.getNome()
                : u.getBarbeiro() != null ? u.getBarbeiro().getNome()
                : u.getCliente() != null ? u.getCliente().getNome() : u.getUsername();
        return new LoginResponseDTO(token, u.getUsername(), nome, u.getPapel(), u.getPapel().name(), unidadeId, unidadeNome,
                u.getBarbeiro() == null ? null : u.getBarbeiro().getId(),
                u.getCliente() == null ? null : u.getCliente().getId());
    }
}
