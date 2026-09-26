package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.BloqueioRequestDTO;
import com.redebarbeariasapi.dto.BloqueioResponseDTO;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.mapper.OperacaoMapper;
import com.redebarbeariasapi.model.*;
import com.redebarbeariasapi.repository.AgendamentoRepository;
import com.redebarbeariasapi.repository.BarbeiroRepository;
import com.redebarbeariasapi.repository.BloqueioAgendaRepository;
import com.redebarbeariasapi.security.Sessao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Folgas, ferias, almoco e feriados — some com os horarios da agenda online. */
@Service
@Transactional
@RequiredArgsConstructor
public class BloqueioService {

    private final BloqueioAgendaRepository repo;
    private final BarbeiroRepository barbeiros;
    private final AgendamentoRepository agendamentos;
    private final UnidadeService unidades;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<BloqueioResponseDTO> listar(Long unidadeId, Long barbeiroId) {
        Long un = Sessao.unidadeEscopo(unidadeId);
        if (Sessao.eh(Papel.BARBEIRO)) barbeiroId = Sessao.atual().barbeiroId();
        return repo.futuros(LocalDateTime.now().minusDays(1), un, barbeiroId).stream().map(OperacaoMapper::bloqueio).toList();
    }

    public BloqueioResponseDTO criar(BloqueioRequestDTO dto) {
        Sessao.exigirUnidade(dto.unidadeId());
        if (!dto.fim().isAfter(dto.inicio())) throw new ValidacaoException("O fim precisa ser depois do início.");
        BloqueioAgenda b = new BloqueioAgenda();
        b.setUnidade(unidades.obter(dto.unidadeId()));
        if (dto.barbeiroId() != null) {
            Barbeiro barb = barbeiros.findById(dto.barbeiroId()).orElseThrow(() -> ResourceNotFoundException.de("Barbeiro", dto.barbeiroId()));
            if (!barb.getUnidade().getId().equals(dto.unidadeId())) throw new ValidacaoException("Esse barbeiro não é dessa unidade.");
            Sessao.exigirBarbeiro(barb.getId());
            b.setBarbeiro(barb);
        } else if (Sessao.eh(Papel.BARBEIRO)) {
            throw new ValidacaoException("Barbeiro só pode bloquear a própria agenda.");
        }
        // nao deixa bloquear por cima de cliente ja marcado sem a equipe resolver antes
        List<Long> alvo = b.getBarbeiro() != null ? List.of(b.getBarbeiro().getId())
                : barbeiros.findByUnidadeIdOrderByNome(dto.unidadeId()).stream().map(Barbeiro::getId).toList();
        if (!alvo.isEmpty()) {
            long marcados = agendamentos.ocupados(alvo, dto.inicio(), dto.fim(),
                    java.util.Set.of(StatusAgendamento.AGENDADO, StatusAgendamento.CONFIRMADO)).size();
            if (marcados > 0) {
                throw new BusinessException("Existem " + marcados + " cliente(s) marcados nesse período. Remarque ou cancele antes de bloquear.");
            }
        }
        b.setInicio(dto.inicio());
        b.setFim(dto.fim());
        b.setMotivo(dto.motivo().trim());
        repo.save(b);
        auditoria.registrar("BLOQUEAR", "Agenda", b.getId(), b.getMotivo());
        return OperacaoMapper.bloqueio(b);
    }

    public void excluir(Long id) {
        BloqueioAgenda b = repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Bloqueio", id));
        Sessao.exigirUnidade(b.getUnidade().getId());
        if (b.getBarbeiro() != null) Sessao.exigirBarbeiro(b.getBarbeiro().getId());
        else if (Sessao.eh(Papel.BARBEIRO)) throw new ValidacaoException("Bloqueio da unidade só a gerência remove.");
        repo.delete(b);
        auditoria.registrar("DESBLOQUEAR", "Agenda", id, b.getMotivo());
    }
}
