package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.UnidadeRequestDTO;
import com.redebarbeariasapi.dto.UnidadeResponseDTO;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.exception.ValidacaoException;
import com.redebarbeariasapi.mapper.UnidadeMapper;
import com.redebarbeariasapi.model.Unidade;
import com.redebarbeariasapi.repository.AgendamentoRepository;
import com.redebarbeariasapi.repository.BarbeiroRepository;
import com.redebarbeariasapi.repository.UnidadeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class UnidadeService {

    private final UnidadeRepository repo;
    private final BarbeiroRepository barbeiros;
    private final AgendamentoRepository agendamentos;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<UnidadeResponseDTO> listar(boolean apenasAtivas) {
        List<Unidade> lista = apenasAtivas ? repo.findByAtivaTrueOrderByNome() : repo.findAllByOrderByNome();
        return lista.stream().map(u -> UnidadeMapper.toResponse(u, barbeiros.countByUnidadeId(u.getId()))).toList();
    }

    public Unidade obter(Long id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Unidade", id));
    }

    @Transactional(readOnly = true)
    public UnidadeResponseDTO buscar(Long id) {
        return UnidadeMapper.toResponse(obter(id), barbeiros.countByUnidadeId(id));
    }

    public UnidadeResponseDTO criar(UnidadeRequestDTO dto) {
        validar(dto);
        Unidade u = new Unidade();
        UnidadeMapper.aplicar(dto, u);
        repo.save(u);
        auditoria.registrar("CRIAR", "Unidade", u.getId(), u.getNome());
        return UnidadeMapper.toResponse(u, 0);
    }

    public UnidadeResponseDTO atualizar(Long id, UnidadeRequestDTO dto) {
        validar(dto);
        Unidade u = obter(id);
        UnidadeMapper.aplicar(dto, u);
        auditoria.registrar("EDITAR", "Unidade", id, u.getNome());
        return UnidadeMapper.toResponse(u, barbeiros.countByUnidadeId(id));
    }

    public void excluir(Long id) {
        Unidade u = obter(id);
        if (barbeiros.countByUnidadeId(id) > 0 || agendamentos.countByUnidadeId(id) > 0) {
            throw new BusinessException("A unidade tem barbeiros/atendimentos no histórico. Desative-a em vez de excluir.");
        }
        repo.delete(u);
        auditoria.registrar("EXCLUIR", "Unidade", id, u.getNome());
    }

    private void validar(UnidadeRequestDTO dto) {
        if (!dto.horaFechamento().isAfter(dto.horaAbertura())) {
            throw new ValidacaoException("O horário de fechamento precisa ser depois da abertura.");
        }
    }
}
