package com.redebarbeariasapi.service;

import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Direitos do titular (LGPD, art. 18): ver/baixar tudo que a barbearia guarda sobre a pessoa e
 * pedir a exclusao. Exclusao = anonimizar o cadastro: nome, telefone, e-mail, nascimento e
 * conversas somem; o historico de atendimentos continua (sem identificar ninguem) porque o
 * financeiro e a comissao dos barbeiros dependem dele.
 */
@Service
@Transactional
public class PrivacidadeService {

    private final ClienteRepository clientes;
    private final AgendamentoRepository agendamentos;
    private final AvaliacaoRepository avaliacoes;
    private final NotificacaoRepository notificacoes;
    private final ConversaWhatsAppRepository conversas;
    private final ListaEsperaRepository listaEspera;
    private final AssinaturaRepository assinaturas;
    private final VendaRepository vendas;
    private final UsuarioRepository usuarios;
    private final AgendamentoService agenda;
    private final AuditoriaService auditoria;
    private final PasswordEncoder encoder;

    public PrivacidadeService(ClienteRepository clientes, AgendamentoRepository agendamentos, AvaliacaoRepository avaliacoes,
                              NotificacaoRepository notificacoes, ConversaWhatsAppRepository conversas,
                              ListaEsperaRepository listaEspera, AssinaturaRepository assinaturas, VendaRepository vendas,
                              UsuarioRepository usuarios, AgendamentoService agenda, AuditoriaService auditoria,
                              PasswordEncoder encoder) {
        this.clientes = clientes;
        this.agendamentos = agendamentos;
        this.avaliacoes = avaliacoes;
        this.notificacoes = notificacoes;
        this.conversas = conversas;
        this.listaEspera = listaEspera;
        this.assinaturas = assinaturas;
        this.vendas = vendas;
        this.usuarios = usuarios;
        this.agenda = agenda;
        this.auditoria = auditoria;
        this.encoder = encoder;
    }

    /** Tudo que existe sobre o cliente, num formato legivel (vira o arquivo "meus-dados.json"). */
    @Transactional(readOnly = true)
    public Map<String, Object> exportar(Long clienteId) {
        Cliente c = obter(clienteId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("geradoEm", LocalDateTime.now());
        Map<String, Object> cad = new LinkedHashMap<>();
        cad.put("nome", c.getNome());
        cad.put("telefone", c.getTelefone());
        cad.put("email", c.getEmail());
        cad.put("dataNascimento", c.getDataNascimento());
        cad.put("observacoes", c.getObservacoes());
        cad.put("pontosFidelidade", c.getPontos());
        cad.put("aceitaPromocoes", c.isAceitaMarketing());
        cad.put("naoQuerWhatsAppAutomatico", c.isWhatsappBloqueado());
        cad.put("cadastradoEm", c.getCriadoEm());
        cad.put("ultimaVisita", c.getUltimaVisita());
        r.put("cadastro", cad);

        List<Agendamento> ags = agendamentos.findByClienteIdOrderByInicioDesc(clienteId);
        r.put("agendamentos", ags.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo", a.getCodigo());
            m.put("inicio", a.getInicio());
            m.put("unidade", a.getUnidade().getNome());
            m.put("servico", a.getServico().getNome());
            m.put("barbeiro", a.getBarbeiro().getNome());
            m.put("status", a.getStatus());
            m.put("valorPago", a.getValorFinal());
            m.put("formaPagamento", a.getFormaPagamento());
            m.put("sinal", a.getSinalValor() == null ? null : a.getSinalValor() + " (" + a.getSinalSituacao() + ")");
            m.put("observacao", a.getObservacao());
            avaliacoes.findByAgendamentoId(a.getId()).ifPresent(av -> {
                m.put("avaliacaoNota", av.getNota());
                m.put("avaliacaoComentario", av.getComentario());
            });
            return m;
        }).toList());
        r.put("compras", vendas.doCliente(clienteId).stream().map(v -> Map.of(
                "data", String.valueOf(v.getDataHora()), "total", v.getTotal(), "cancelada", v.isCancelada())).toList());
        r.put("assinaturasDoClube", assinaturas.findByClienteIdOrderByInicioDesc(clienteId).stream().map(a -> Map.of(
                "plano", a.getPlano().getNome(), "status", a.getStatus(), "inicio", String.valueOf(a.getInicio()))).toList());
        r.put("mensagensRecebidas", notificacoes.findByClienteIdOrderByDataHoraDesc(clienteId).stream().map(n -> Map.of(
                "quando", String.valueOf(n.getDataHora()), "tipo", n.getTipo(), "canal", n.getCanal(), "situacao", n.getStatus())).toList());
        r.put("conversasWhatsApp", conversasDoTelefone(c.getTelefone()).stream().map(cv -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ultimaMensagem", cv.getUltimaMensagem());
            m.put("recebidaEm", cv.getUltimaRecebidaEm());
            m.put("ultimaAcao", cv.getUltimaAcao());
            return m;
        }).toList());
        r.put("listaDeEspera", listaEspera.findByClienteIdAndStatusIn(clienteId, List.of(ListaEspera.Status.values())).stream().map(e -> Map.of(
                "dia", String.valueOf(e.getData()), "unidade", e.getUnidade().getNome(), "status", e.getStatus())).toList());
        return r;
    }

    /**
     * Anonimiza. Bloqueia se o cliente tem assinatura do clube ativa (tem cobranca recorrente:
     * precisa cancelar antes, pra ninguem ficar pagando por um cadastro que nao existe mais).
     */
    public void anonimizar(Long clienteId, String quemPediu) {
        Cliente c = obter(clienteId);
        if (c.getAnonimizadoEm() != null) return;
        if (!assinaturas.findByClienteIdAndStatus(clienteId, StatusAssinatura.ATIVA).isEmpty()) {
            throw new BusinessException("Há uma assinatura do clube ativa. Cancele a assinatura antes de excluir os dados.");
        }
        String telefoneAntigo = c.getTelefone();
        LocalDateTime agora = LocalDateTime.now();
        for (Agendamento a : agendamentos.findByClienteIdOrderByInicioDesc(clienteId)) {
            if (!a.getStatus().finalizado() && a.getInicio().isAfter(agora)) agenda.cancelarPorExclusaoDeDados(a);
            a.setObservacao(null);
        }
        listaEspera.findByClienteIdAndStatusIn(clienteId, List.of(ListaEspera.Status.AGUARDANDO, ListaEspera.Status.AVISADO))
                .forEach(e -> e.setStatus(ListaEspera.Status.DESISTIU));
        conversas.deleteAll(conversasDoTelefone(telefoneAntigo));
        notificacoes.findByClienteIdOrderByDataHoraDesc(clienteId).forEach(n -> n.setDestino("(removido)"));

        c.setNome("Cliente removido #" + c.getId());
        c.setTelefone("anon-" + c.getId());
        c.setEmail(null);
        c.setDataNascimento(null);
        c.setObservacoes(null);
        c.setAceitaMarketing(false);
        c.setWhatsappBloqueado(true);
        c.setUnidadePreferida(null);
        c.setBarbeiroPreferido(null);
        c.setPontos(0);
        c.setAnonimizadoEm(agora);

        usuarios.findByClienteId(clienteId).ifPresent(u -> {
            u.setUsername("anon-" + c.getId());
            u.setNome(null);
            u.setPassword(encoder.encode(UUID.randomUUID().toString()));
            u.setAtivo(false);
        });
        auditoria.registrar("LGPD_EXCLUSAO", "Cliente", clienteId, "Dados anonimizados a pedido de " + quemPediu);
    }

    private List<ConversaWhatsApp> conversasDoTelefone(String telefone) {
        if (telefone == null) return List.of();
        String d = telefone.replaceAll("\\D", "");
        List<ConversaWhatsApp> r = new ArrayList<>();
        for (String t : Set.of("55" + d, d.length() == 11 ? "55" + d.substring(0, 2) + d.substring(3) : "55" + d)) {
            conversas.findByTelefone(t).ifPresent(r::add);
        }
        return r;
    }

    private Cliente obter(Long id) {
        return clientes.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Cliente", id));
    }
}
