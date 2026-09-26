package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.*;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.mapper.OperacaoMapper;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.*;
import com.redebarbeariasapi.security.Sessao;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

/**
 * Clube de assinatura: o cliente paga um valor fixo por mes e usa N atendimentos
 * dos servicos do plano. Receita recorrente previsivel pra barbearia.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ClubeService {

    private static final Logger log = LoggerFactory.getLogger(ClubeService.class);
    /** Dias de tolerancia depois do vencimento antes de suspender automaticamente. */
    private static final int CARENCIA_DIAS = 5;

    private final PlanoRepository planos;
    private final AssinaturaRepository assinaturas;
    private final PagamentoAssinaturaRepository pagamentos;
    private final ServicoRepository servicos;
    private final ClienteRepository clientes;
    private final UnidadeService unidades;
    private final AuditoriaService auditoria;

    // ---------------------------------------------------------------- planos

    @Transactional(readOnly = true)
    public List<PlanoResponseDTO> listarPlanos(boolean apenasAtivos) {
        List<Plano> lista = apenasAtivos ? planos.findByAtivoTrueOrderByPrecoMensal() : planos.findAllByOrderByPrecoMensal();
        return lista.stream().map(p -> OperacaoMapper.plano(p, assinaturas.countByPlanoIdAndStatus(p.getId(), StatusAssinatura.ATIVA))).toList();
    }

    public PlanoResponseDTO criarPlano(PlanoRequestDTO dto) {
        Plano p = new Plano();
        aplicar(dto, p);
        planos.save(p);
        auditoria.registrar("CRIAR", "Plano", p.getId(), p.getNome() + " R$ " + p.getPrecoMensal());
        return OperacaoMapper.plano(p, 0);
    }

    public PlanoResponseDTO atualizarPlano(Long id, PlanoRequestDTO dto) {
        Plano p = planos.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Plano", id));
        aplicar(dto, p);
        auditoria.registrar("EDITAR", "Plano", id, p.getNome() + " R$ " + p.getPrecoMensal());
        return OperacaoMapper.plano(p, assinaturas.countByPlanoIdAndStatus(id, StatusAssinatura.ATIVA));
    }

    public void excluirPlano(Long id) {
        Plano p = planos.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Plano", id));
        if (assinaturas.countByPlanoId(id) > 0) throw new BusinessException("Plano com assinantes não pode ser excluído. Desative-o.");
        planos.delete(p);
        auditoria.registrar("EXCLUIR", "Plano", id, p.getNome());
    }

    private void aplicar(PlanoRequestDTO dto, Plano p) {
        p.setNome(dto.nome().trim());
        p.setDescricao(dto.descricao());
        p.setPrecoMensal(dto.precoMensal());
        p.setUsosPorMes(dto.usosPorMes());
        p.setServicos(new HashSet<>(servicos.findAllById(dto.servicoIds())));
        if (p.getServicos().size() != dto.servicoIds().size()) throw new ValidacaoException("Algum serviço escolhido não existe.");
        if (dto.ativo() != null) p.setAtivo(dto.ativo());
    }

    // ---------------------------------------------------------------- assinaturas

    @Transactional(readOnly = true)
    public List<AssinaturaResponseDTO> listarAssinaturas() {
        return assinaturas.findAllByOrderByStatusAscValidaAteAsc().stream().map(OperacaoMapper::assinatura).toList();
    }

    public AssinaturaResponseDTO assinar(AssinaturaRequestDTO dto) {
        Sessao.exigirUnidade(dto.unidadeId());
        Cliente c = clientes.findById(dto.clienteId()).orElseThrow(() -> ResourceNotFoundException.de("Cliente", dto.clienteId()));
        Plano p = planos.findById(dto.planoId()).orElseThrow(() -> ResourceNotFoundException.de("Plano", dto.planoId()));
        if (!p.isAtivo()) throw new BusinessException("Esse plano não está mais disponível.");
        boolean jaTem = assinaturas.findByClienteIdOrderByInicioDesc(c.getId()).stream()
                .anyMatch(a -> a.getStatus() != StatusAssinatura.CANCELADA);
        if (jaTem) throw new BusinessException(c.getNome() + " já tem uma assinatura. Cancele a atual antes de trocar de plano.");
        LocalDate hoje = LocalDate.now();
        Assinatura a = new Assinatura();
        a.setCliente(c);
        a.setPlano(p);
        a.setInicio(hoje);
        a.setCicloInicio(hoje);
        a.setValidaAte(hoje.plusMonths(1).minusDays(1));
        assinaturas.save(a);
        registrarPagamento(a, dto.unidadeId(), dto.formaPagamento());
        auditoria.registrar("ASSINAR", "Assinatura", a.getId(), c.getNome() + " — " + p.getNome());
        return OperacaoMapper.assinatura(a);
    }

    /** Paga mais um mes: novo ciclo com os usos zerados. Reativa assinatura suspensa. */
    public AssinaturaResponseDTO renovar(Long id, RenovacaoRequestDTO dto) {
        Sessao.exigirUnidade(dto.unidadeId());
        Assinatura a = assinaturas.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Assinatura", id));
        if (a.getStatus() == StatusAssinatura.CANCELADA) throw new BusinessException("Assinatura cancelada. Crie uma nova.");
        LocalDate hoje = LocalDate.now();
        LocalDate inicioCiclo = a.getValidaAte().isBefore(hoje) ? hoje : a.getValidaAte().plusDays(1);
        a.setCicloInicio(inicioCiclo);
        a.setValidaAte(inicioCiclo.plusMonths(1).minusDays(1));
        a.setUsosNoCiclo(0);
        a.setStatus(StatusAssinatura.ATIVA);
        registrarPagamento(a, dto.unidadeId(), dto.formaPagamento());
        auditoria.registrar("RENOVAR", "Assinatura", id, a.getCliente().getNome() + " até " + a.getValidaAte());
        return OperacaoMapper.assinatura(a);
    }

    public AssinaturaResponseDTO cancelar(Long id) {
        Assinatura a = assinaturas.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Assinatura", id));
        if (a.getStatus() == StatusAssinatura.CANCELADA) throw new BusinessException("Já está cancelada.");
        a.setStatus(StatusAssinatura.CANCELADA);
        a.setCanceladaEm(LocalDate.now());
        auditoria.registrar("CANCELAR", "Assinatura", id, a.getCliente().getNome());
        return OperacaoMapper.assinatura(a);
    }

    private void registrarPagamento(Assinatura a, Long unidadeId, FormaPagamento forma) {
        if (forma == FormaPagamento.ASSINATURA || forma == FormaPagamento.CORTESIA) {
            throw new ValidacaoException("Mensalidade precisa de pagamento real (dinheiro, Pix ou cartão).");
        }
        PagamentoAssinatura pg = new PagamentoAssinatura();
        pg.setAssinatura(a);
        pg.setUnidade(unidades.obter(unidadeId));
        pg.setValor(a.getPlano().getPrecoMensal());
        pg.setFormaPagamento(forma);
        pg.setDataHora(LocalDateTime.now());
        pagamentos.save(pg);
    }

    /** Todo dia 03:00: suspende quem passou da carencia sem renovar. */
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    public void suspenderInadimplentes() {
        LocalDate limite = LocalDate.now().minusDays(CARENCIA_DIAS);
        int n = 0;
        for (Assinatura a : assinaturas.findByStatus(StatusAssinatura.ATIVA)) {
            if (a.getValidaAte().isBefore(limite)) {
                a.setStatus(StatusAssinatura.SUSPENSA);
                n++;
            }
        }
        if (n > 0) log.info("{} assinatura(s) suspensas por falta de pagamento", n);
    }
}
