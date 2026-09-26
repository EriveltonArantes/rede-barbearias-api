package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.AvaliacaoResponseDTO;
import com.redebarbeariasapi.dto.RespostaAvaliacaoDTO;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.mapper.OperacaoMapper;
import com.redebarbeariasapi.model.Avaliacao;
import com.redebarbeariasapi.model.Papel;
import com.redebarbeariasapi.repository.AvaliacaoRepository;
import com.redebarbeariasapi.security.Sessao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class AvaliacaoService {

    private final AvaliacaoRepository repo;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<AvaliacaoResponseDTO> listar(Long unidadeId, Long barbeiroId) {
        Long un = Sessao.unidadeEscopo(unidadeId);
        if (Sessao.eh(Papel.BARBEIRO)) barbeiroId = Sessao.atual().barbeiroId();
        return repo.filtrar(un, barbeiroId).stream().map(OperacaoMapper::avaliacao).toList();
    }

    @Transactional(readOnly = true)
    public List<AvaliacaoResponseDTO> destaques() {
        return repo.destaquesPublicos().stream().limit(12).map(OperacaoMapper::avaliacao).toList();
    }

    public AvaliacaoResponseDTO responder(Long id, RespostaAvaliacaoDTO dto) {
        Avaliacao a = repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Avaliação", id));
        Sessao.exigirUnidade(a.getAgendamento().getUnidade().getId());
        if (dto.resposta() != null) a.setResposta(dto.resposta().isBlank() ? null : dto.resposta().trim());
        if (dto.publica() != null) a.setPublica(dto.publica());
        auditoria.registrar("RESPONDER", "Avaliacao", id, a.isPublica() ? "pública" : "oculta");
        return OperacaoMapper.avaliacao(a);
    }
}
