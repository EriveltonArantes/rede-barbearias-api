package com.redebarbeariasapi.service;

import com.redebarbeariasapi.model.AuditLog;
import com.redebarbeariasapi.repository.AuditLogRepository;
import com.redebarbeariasapi.security.Sessao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Trilha de quem fez o que (criou, cancelou, estornou, alterou preco...). */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditLogRepository repo;

    public void registrar(String acao, String entidade, Long entidadeId, String detalhe) {
        AuditLog log = new AuditLog();
        log.setUsuario(Sessao.username());
        log.setAcao(acao);
        log.setEntidade(entidade);
        log.setEntidadeId(entidadeId);
        log.setDetalhe(detalhe == null ? null : detalhe.length() > 1000 ? detalhe.substring(0, 1000) : detalhe);
        repo.save(log);
    }

    public List<AuditLog> recentes() {
        return repo.findTop300ByOrderByDataHoraDesc();
    }
}
