package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.*;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.mapper.AgendamentoMapper;
import com.redebarbeariasapi.mapper.ClienteMapper;
import com.redebarbeariasapi.mapper.OperacaoMapper;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository repo;
    private final AgendamentoRepository agendamentos;
    private final VendaRepository vendas;
    private final AssinaturaRepository assinaturas;
    private final AvaliacaoRepository avaliacoes;
    private final UsuarioRepository usuarios;
    private final UnidadeRepository unidades;
    private final BarbeiroRepository barbeiros;
    private final AuditoriaService auditoria;

    @Value("${app.fidelidade.pontos-resgate:10}")
    private int pontosResgate;

    @Transactional(readOnly = true)
    public List<ClienteResponseDTO> listar(String q) {
        List<Cliente> lista = Textos.vazio(q) ? repo.findAllByOrderByNome() : repo.buscar(q.trim());
        return lista.stream().map(ClienteMapper::toResponse).toList();
    }

    public Cliente obter(Long id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Cliente", id));
    }

    @Transactional(readOnly = true)
    public ClienteResponseDTO buscar(Long id) {
        return ClienteMapper.toResponse(obter(id));
    }

    public ClienteResponseDTO criar(ClienteRequestDTO dto) {
        String tel = Textos.telefone(dto.telefone());
        repo.findByTelefone(tel).ifPresent(c -> {
            throw new BusinessException("Já existe cliente com esse telefone: " + c.getNome() + " (#" + c.getId() + ").");
        });
        Cliente c = new Cliente();
        aplicar(dto, c, tel);
        repo.save(c);
        auditoria.registrar("CRIAR", "Cliente", c.getId(), c.getNome());
        return ClienteMapper.toResponse(c);
    }

    public ClienteResponseDTO atualizar(Long id, ClienteRequestDTO dto) {
        Cliente c = obter(id);
        String tel = Textos.telefone(dto.telefone());
        repo.findByTelefone(tel).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new BusinessException("Esse telefone já pertence a " + o.getNome() + " (#" + o.getId() + ").");
        });
        aplicar(dto, c, tel);
        auditoria.registrar("EDITAR", "Cliente", id, c.getNome());
        return ClienteMapper.toResponse(c);
    }

    public void excluir(Long id) {
        Cliente c = obter(id);
        if (!agendamentos.findByClienteIdOrderByInicioDesc(id).isEmpty() || !vendas.doCliente(id).isEmpty()
                || !assinaturas.findByClienteIdOrderByInicioDesc(id).isEmpty()) {
            throw new BusinessException("Cliente com histórico de atendimentos/compras não pode ser excluído (o histórico financeiro precisa ficar íntegro).");
        }
        usuarios.findByClienteId(id).ifPresent(usuarios::delete);
        repo.delete(c);
        auditoria.registrar("EXCLUIR", "Cliente", id, c.getNome());
    }

    /** Usado no balcao: acha pelo telefone ou cadastra na hora (a equipe pergunta sobre promocoes pessoalmente). */
    public Cliente obterOuCriar(String nome, String telefone, String email) {
        return obterOuCriar(nome, telefone, email, null);
    }

    /**
     * Agendamento online / lista de espera: aceitaMarketing vem da caixinha do formulario (LGPD).
     * Cliente novo so recebe promocao se marcou; cliente antigo que marcou passa a receber;
     * desmarcado nao tira um consentimento dado antes. null = nao perguntado (balcao).
     */
    public Cliente obterOuCriar(String nome, String telefone, String email, Boolean aceitaMarketing) {
        String tel = Textos.telefone(telefone);
        return repo.findByTelefone(tel).map(c -> {
            if (Textos.vazio(c.getEmail()) && !Textos.vazio(email)) c.setEmail(email.trim());
            if (Boolean.TRUE.equals(aceitaMarketing)) c.setAceitaMarketing(true);
            return c;
        }).orElseGet(() -> {
            if (Textos.vazio(nome)) throw new com.redebarbeariasapi.exception.ValidacaoException("Informe o nome do cliente.");
            Cliente c = new Cliente();
            c.setNome(nome.trim());
            c.setTelefone(tel);
            c.setEmail(Textos.vazio(email) ? null : email.trim());
            if (aceitaMarketing != null) c.setAceitaMarketing(aceitaMarketing);
            return repo.save(c);
        });
    }

    @Transactional(readOnly = true)
    public ClienteFichaDTO ficha(Long id) {
        Cliente c = obter(id);
        List<Agendamento> ags = agendamentos.findByClienteIdOrderByInicioDesc(id);
        List<Venda> compras = vendas.doCliente(id).stream().filter(v -> !v.isCancelada()).toList();

        BigDecimal gastoServicos = ags.stream().filter(Agendamento::isPago)
                .map(a -> Textos.zeroSeNulo(a.getValorFinal())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gastoProdutos = compras.stream().map(Venda::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        long atendidos = ags.stream().filter(a -> a.getStatus() == StatusAgendamento.CONCLUIDO).count();
        long faltas = ags.stream().filter(a -> a.getStatus() == StatusAgendamento.NAO_COMPARECEU).count();
        long cancel = ags.stream().filter(a -> a.getStatus() == StatusAgendamento.CANCELADO).count();
        BigDecimal total = gastoServicos.add(gastoProdutos);
        BigDecimal ticket = atendidos == 0 ? BigDecimal.ZERO
                : gastoServicos.divide(BigDecimal.valueOf(atendidos), 2, RoundingMode.HALF_UP);

        String favorito = ags.stream().filter(a -> a.getStatus() == StatusAgendamento.CONCLUIDO)
                .collect(Collectors.groupingBy(a -> a.getServico().getNome(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);

        Assinatura ativa = assinaturas.findByClienteIdOrderByInicioDesc(id).stream()
                .filter(a -> a.getStatus() != StatusAssinatura.CANCELADA).findFirst().orElse(null);

        Map<Long, Integer> notas = AgendamentoService.notasDe(avaliacoes, ags);

        return new ClienteFichaDTO(ClienteMapper.toResponse(c), Textos.dinheiro(total), atendidos, faltas, cancel, ticket,
                pontosResgate, c.getPontos() >= pontosResgate, OperacaoMapper.assinatura(ativa), favorito,
                ags.stream().limit(60).map(a -> AgendamentoMapper.toResponse(a, notas.get(a.getId()))).toList(),
                compras.stream().limit(30).map(OperacaoMapper::venda).toList());
    }

    private void aplicar(ClienteRequestDTO dto, Cliente c, String telefone) {
        c.setNome(dto.nome().trim());
        c.setTelefone(telefone);
        c.setEmail(Textos.vazio(dto.email()) ? null : dto.email().trim());
        c.setDataNascimento(dto.dataNascimento());
        c.setObservacoes(dto.observacoes());
        c.setUnidadePreferida(dto.unidadePreferidaId() == null ? null
                : unidades.findById(dto.unidadePreferidaId()).orElseThrow(() -> ResourceNotFoundException.de("Unidade", dto.unidadePreferidaId())));
        c.setBarbeiroPreferido(dto.barbeiroPreferidoId() == null ? null
                : barbeiros.findById(dto.barbeiroPreferidoId()).orElseThrow(() -> ResourceNotFoundException.de("Barbeiro", dto.barbeiroPreferidoId())));
        if (dto.aceitaMarketing() != null) c.setAceitaMarketing(dto.aceitaMarketing());
    }

    static <T> Map<Long, T> porId(List<T> lista, Function<T, Long> id) {
        return lista.stream().collect(Collectors.toMap(id, x -> x));
    }
}
