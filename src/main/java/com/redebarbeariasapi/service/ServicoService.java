package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.ServicoRequestDTO;
import com.redebarbeariasapi.dto.ServicoResponseDTO;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.mapper.ServicoMapper;
import com.redebarbeariasapi.model.Servico;
import com.redebarbeariasapi.repository.AgendamentoRepository;
import com.redebarbeariasapi.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ServicoService {

    private final ServicoRepository repo;
    private final AgendamentoRepository agendamentos;
    private final AuditoriaService auditoria;

    @Transactional(readOnly = true)
    public List<ServicoResponseDTO> listar(boolean apenasAtivos) {
        List<Servico> lista = apenasAtivos ? repo.findByAtivoTrueOrderByCategoriaAscPrecoAsc() : repo.findAllByOrderByCategoriaAscPrecoAsc();
        return lista.stream().map(ServicoMapper::toResponse).toList();
    }

    public Servico obter(Long id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Serviço", id));
    }

    @Transactional(readOnly = true)
    public ServicoResponseDTO buscar(Long id) {
        return ServicoMapper.toResponse(obter(id));
    }

    public ServicoResponseDTO criar(ServicoRequestDTO dto) {
        Servico s = new Servico();
        ServicoMapper.aplicar(dto, s);
        repo.save(s);
        auditoria.registrar("CRIAR", "Servico", s.getId(), s.getNome() + " R$ " + s.getPreco());
        return ServicoMapper.toResponse(s);
    }

    public ServicoResponseDTO atualizar(Long id, ServicoRequestDTO dto) {
        Servico s = obter(id);
        String antes = s.getNome() + " R$ " + s.getPreco();
        ServicoMapper.aplicar(dto, s);
        auditoria.registrar("EDITAR", "Servico", id, antes + " -> " + s.getNome() + " R$ " + s.getPreco());
        return ServicoMapper.toResponse(s);
    }

    public void excluir(Long id) {
        Servico s = obter(id);
        if (agendamentos.countByServicoId(id) > 0) {
            throw new BusinessException("Esse serviço já foi usado em atendimentos. Desative-o em vez de excluir.");
        }
        repo.delete(s);
        auditoria.registrar("EXCLUIR", "Servico", id, s.getNome());
    }
}
