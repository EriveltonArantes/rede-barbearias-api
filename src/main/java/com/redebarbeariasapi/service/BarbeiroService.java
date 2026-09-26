package com.redebarbeariasapi.service;

import com.redebarbeariasapi.dto.BarbeiroPublicoDTO;
import com.redebarbeariasapi.dto.BarbeiroRequestDTO;
import com.redebarbeariasapi.dto.BarbeiroResponseDTO;
import com.redebarbeariasapi.exception.BusinessException;
import com.redebarbeariasapi.exception.ResourceNotFoundException;
import com.redebarbeariasapi.mapper.BarbeiroMapper;
import com.redebarbeariasapi.model.Barbeiro;
import com.redebarbeariasapi.repository.AgendamentoRepository;
import com.redebarbeariasapi.repository.AvaliacaoRepository;
import com.redebarbeariasapi.repository.BarbeiroRepository;
import com.redebarbeariasapi.security.Sessao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class BarbeiroService {

    private final BarbeiroRepository repo;
    private final AvaliacaoRepository avaliacoes;
    private final AgendamentoRepository agendamentos;
    private final UnidadeService unidades;
    private final AuditoriaService auditoria;

    /** Nota media e total de avaliacoes por barbeiro: {id -> [media, total]}. */
    public Map<Long, double[]> notas() {
        Map<Long, double[]> m = new HashMap<>();
        for (Object[] linha : avaliacoes.mediasPorBarbeiro()) {
            double media = BigDecimal.valueOf(((Number) linha[1]).doubleValue()).setScale(1, RoundingMode.HALF_UP).doubleValue();
            m.put((Long) linha[0], new double[]{media, ((Number) linha[2]).doubleValue()});
        }
        return m;
    }

    @Transactional(readOnly = true)
    public List<BarbeiroResponseDTO> listar(Long unidadeId, boolean apenasAtivos) {
        Long un = Sessao.unidadeEscopo(unidadeId);
        List<Barbeiro> lista = un == null
                ? (apenasAtivos ? repo.findByAtivoTrueOrderByNome() : repo.findAllByOrderByNome())
                : (apenasAtivos ? repo.findByUnidadeIdAndAtivoTrueOrderByNome(un) : repo.findByUnidadeIdOrderByNome(un));
        Map<Long, double[]> n = notas();
        return lista.stream().map(b -> resposta(b, n)).toList();
    }

    @Transactional(readOnly = true)
    public List<BarbeiroPublicoDTO> listarPublico(Long unidadeId) {
        List<Barbeiro> lista = unidadeId == null ? repo.findByAtivoTrueOrderByNome() : repo.findByUnidadeIdAndAtivoTrueOrderByNome(unidadeId);
        Map<Long, double[]> n = notas();
        return lista.stream().filter(b -> b.getUnidade().isAtiva()).map(b -> {
            double[] x = n.get(b.getId());
            return BarbeiroMapper.toPublico(b, x == null ? null : x[0], x == null ? 0 : (long) x[1]);
        }).toList();
    }

    public Barbeiro obter(Long id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.de("Barbeiro", id));
    }

    @Transactional(readOnly = true)
    public BarbeiroResponseDTO buscar(Long id) {
        Barbeiro b = obter(id);
        Sessao.exigirUnidade(b.getUnidade().getId());
        return resposta(b, notas());
    }

    public BarbeiroResponseDTO criar(BarbeiroRequestDTO dto) {
        Sessao.exigirUnidade(dto.unidadeId());
        Barbeiro b = new Barbeiro();
        b.setUnidade(unidades.obter(dto.unidadeId()));
        BarbeiroMapper.aplicar(dto, b);
        repo.save(b);
        auditoria.registrar("CRIAR", "Barbeiro", b.getId(), b.getNome() + " em " + b.getUnidade().getNome());
        return resposta(b, Map.of());
    }

    public BarbeiroResponseDTO atualizar(Long id, BarbeiroRequestDTO dto) {
        Barbeiro b = obter(id);
        Sessao.exigirUnidade(b.getUnidade().getId());
        Sessao.exigirUnidade(dto.unidadeId());
        String antes = "comissão " + b.getComissaoServico() + "%/" + b.getComissaoProduto() + "%";
        b.setUnidade(unidades.obter(dto.unidadeId()));
        BarbeiroMapper.aplicar(dto, b);
        auditoria.registrar("EDITAR", "Barbeiro", id, b.getNome() + " (antes: " + antes + "; agora: comissão "
                + b.getComissaoServico() + "%/" + b.getComissaoProduto() + "%)");
        return resposta(b, notas());
    }

    public void excluir(Long id) {
        Barbeiro b = obter(id);
        if (agendamentos.countByBarbeiroId(id) > 0) {
            throw new BusinessException(b.getNome() + " tem atendimentos no histórico. Desative o cadastro em vez de excluir.");
        }
        repo.delete(b);
        auditoria.registrar("EXCLUIR", "Barbeiro", id, b.getNome());
    }

    private BarbeiroResponseDTO resposta(Barbeiro b, Map<Long, double[]> n) {
        double[] x = n.get(b.getId());
        return BarbeiroMapper.toResponse(b, x == null ? null : x[0], x == null ? 0 : (long) x[1]);
    }
}
