package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.UsuarioRequestDTO;
import com.redebarbeariasapi.dto.UsuarioResponseDTO;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.mapper.OperacaoMapper;
import com.redebarbeariasapi.model.Papel;
import com.redebarbeariasapi.model.Usuario;
import com.redebarbeariasapi.repository.BarbeiroRepository;
import com.redebarbeariasapi.repository.ClienteRepository;
import com.redebarbeariasapi.repository.UsuarioRepository;
import com.redebarbeariasapi.security.Sessao;
import com.redebarbeariasapi.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** Contas de acesso da equipe. ADMIN gerencia todas; GERENTE so recepcao/barbeiros da propria unidade. */
@Service
@Transactional
@RequiredArgsConstructor
public class UsuarioService {

    private static final Set<Papel> GERENTE_PODE_CRIAR = Set.of(Papel.RECEPCAO, Papel.BARBEIRO);

    private final UsuarioRepository repo;
    private final UnidadeService unidades;
    private final BarbeiroRepository barbeiros;
    private final ClienteRepository clientes;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final com.redebarbeariasapi.repository.RecuperacaoSenhaRepository recuperacoes;

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listar() {
        UsuarioLogado eu = Sessao.atual();
        List<Usuario> lista = eu.papel() == Papel.ADMIN ? repo.findAllByOrderByUsername()
                : repo.findAllByOrderByUsername().stream().filter(u -> daUnidade(u, eu.unidadeId())).toList();
        return lista.stream().map(OperacaoMapper::usuario).toList();
    }

    public UsuarioResponseDTO criar(UsuarioRequestDTO dto) {
        if (Textos.vazio(dto.password())) throw new ValidacaoException("Defina uma senha (mínimo 6 caracteres).");
        if (repo.existsByUsernameIgnoreCase(dto.username())) throw new BusinessException("Esse usuário já existe.");
        Usuario u = new Usuario();
        u.setUsername(dto.username().trim().toLowerCase());
        aplicar(dto, u);
        u.setPassword(encoder.encode(dto.password()));
        repo.save(u);
        auditoria.registrar("CRIAR", "Usuario", u.getId(), u.getUsername() + " (" + u.getPapel() + ")");
        return OperacaoMapper.usuario(u);
    }

    public UsuarioResponseDTO atualizar(Long id, UsuarioRequestDTO dto) {
        Usuario u = repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Usuário", id));
        exigirGerenciavel(u);
        UsuarioLogado eu = Sessao.atual();
        if (u.getId().equals(eu.id()) && (dto.papel() != u.getPapel() || Boolean.FALSE.equals(dto.ativo()))) {
            throw new BusinessException("Você não pode mudar o próprio papel nem se desativar.");
        }
        repo.findByUsernameIgnoreCase(dto.username()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Esse usuário já existe.");
        });
        Papel antes = u.getPapel();
        u.setUsername(dto.username().trim().toLowerCase());
        aplicar(dto, u);
        if (antes == Papel.ADMIN && (u.getPapel() != Papel.ADMIN || !u.isAtivo())) garantirOutroAdmin(id);
        if (!Textos.vazio(dto.password())) u.setPassword(encoder.encode(dto.password()));
        auditoria.registrar("EDITAR", "Usuario", id, u.getUsername() + " (" + antes + " -> " + u.getPapel() + ")"
                + (Textos.vazio(dto.password()) ? "" : " + senha redefinida"));
        return OperacaoMapper.usuario(u);
    }

    public void excluir(Long id) {
        Usuario u = repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Usuário", id));
        exigirGerenciavel(u);
        if (u.getId().equals(Sessao.atual().id())) throw new BusinessException("Você não pode excluir a própria conta.");
        if (u.getPapel() == Papel.ADMIN) garantirOutroAdmin(id);
        recuperacoes.apagarDoUsuario(id);
        repo.delete(u);
        auditoria.registrar("EXCLUIR", "Usuario", id, u.getUsername());
    }

    private void garantirOutroAdmin(Long ignorar) {
        boolean existe = repo.findAllByOrderByUsername().stream()
                .anyMatch(x -> !x.getId().equals(ignorar) && x.getPapel() == Papel.ADMIN && x.isAtivo());
        if (!existe) throw new BusinessException("O sistema precisa de pelo menos um administrador ativo.");
    }

    private void aplicar(UsuarioRequestDTO dto, Usuario u) {
        UsuarioLogado eu = Sessao.atual();
        if (eu.papel() == Papel.GERENTE && !GERENTE_PODE_CRIAR.contains(dto.papel())) {
            throw new AccessDeniedException("Gerente só cria contas de recepção e barbeiro.");
        }
        u.setNome(dto.nome());
        u.setPapel(dto.papel());
        u.setUnidade(null);
        u.setBarbeiro(null);
        u.setCliente(null);
        switch (dto.papel()) {
            case GERENTE, RECEPCAO -> {
                if (dto.unidadeId() == null) throw new ValidacaoException("Escolha a unidade desse usuário.");
                Sessao.exigirUnidade(dto.unidadeId());
                u.setUnidade(unidades.obter(dto.unidadeId()));
            }
            case BARBEIRO -> {
                if (dto.barbeiroId() == null) throw new ValidacaoException("Vincule a conta a um barbeiro cadastrado.");
                var b = barbeiros.findById(dto.barbeiroId()).orElseThrow(() -> ResourceNotFoundException.de("Barbeiro", dto.barbeiroId()));
                Sessao.exigirUnidade(b.getUnidade().getId());
                u.setBarbeiro(b);
            }
            case CLIENTE -> {
                if (dto.clienteId() == null) throw new ValidacaoException("Vincule a conta a um cliente cadastrado.");
                u.setCliente(clientes.findById(dto.clienteId()).orElseThrow(() -> ResourceNotFoundException.de("Cliente", dto.clienteId())));
            }
            case ADMIN -> { }
        }
        if (dto.ativo() != null) u.setAtivo(dto.ativo());
        String email = dto.email() == null ? "" : dto.email().trim();
        if (!email.isEmpty() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ValidacaoException("E-mail inválido.");
        u.setEmail(email.isEmpty() ? null : email.toLowerCase());
        u.setTelefone(Textos.vazio(dto.telefone()) ? null : Textos.telefone(dto.telefone()));
    }

    private void exigirGerenciavel(Usuario u) {
        UsuarioLogado eu = Sessao.atual();
        if (eu.papel() == Papel.ADMIN) return;
        if (!GERENTE_PODE_CRIAR.contains(u.getPapel()) || !daUnidade(u, eu.unidadeId())) {
            throw new AccessDeniedException("Fora da sua gestão");
        }
    }

    private static boolean daUnidade(Usuario u, Long unidadeId) {
        if (unidadeId == null) return false;
        if (u.getUnidade() != null) return unidadeId.equals(u.getUnidade().getId());
        return u.getBarbeiro() != null && unidadeId.equals(u.getBarbeiro().getUnidade().getId());
    }
}
