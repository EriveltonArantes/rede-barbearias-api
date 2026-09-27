package com.redebarbeariasapi;

import com.redebarbeariasapi.model.Papel;
import com.redebarbeariasapi.model.Unidade;
import com.redebarbeariasapi.model.Usuario;
import com.redebarbeariasapi.repository.UnidadeRepository;
import com.redebarbeariasapi.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Os testes rodam sem o seed de demonstracao: cria a conta da equipe que o teste precisa. */
final class Equipe {

    private Equipe() {
    }

    static void garantir(UsuarioRepository usuarios, UnidadeRepository unidades, PasswordEncoder encoder,
                         String username, String senha, Papel papel) {
        if (usuarios.findByUsernameIgnoreCase(username).isPresent()) return;
        Unidade un = unidades.findAll().stream().findFirst().orElseGet(() -> {
            Unidade u = new Unidade();
            u.setNome("Barbearia Teste — Centro");
            u.setEndereco("Rua Teste, 1");
            return unidades.save(u);
        });
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setPassword(encoder.encode(senha));
        u.setNome(username);
        u.setPapel(papel);
        if (papel == Papel.GERENTE || papel == Papel.RECEPCAO) u.setUnidade(un);
        usuarios.save(u);
    }
}
