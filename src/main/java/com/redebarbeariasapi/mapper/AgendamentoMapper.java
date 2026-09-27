package com.redebarbeariasapi.mapper;

import com.redebarbeariasapi.dto.AgendamentoPublicoResponseDTO;
import com.redebarbeariasapi.dto.AgendamentoResponseDTO;
import com.redebarbeariasapi.model.Agendamento;

public final class AgendamentoMapper {
    private AgendamentoMapper() {}

    public static AgendamentoResponseDTO toResponse(Agendamento a, Integer nota) {
        return new AgendamentoResponseDTO(a.getId(), a.getCodigo(),
                a.getUnidade().getId(), a.getUnidade().getNome(),
                a.getBarbeiro().getId(), a.getBarbeiro().getNome(),
                a.getCliente().getId(), a.getCliente().getNome(), a.getCliente().getTelefone(),
                a.getServico().getId(), a.getServico().getNome(), a.getServico().getDuracaoMinutos(),
                a.getInicio(), a.getFim(), a.getStatus(), a.getOrigem(),
                a.getValor(), a.getDesconto(), a.valorAPagar(), a.getValorFinal(),
                a.getCupomCodigo(), a.getFormaPagamento(), a.isPago(), a.getPagoEm(),
                a.getComissaoValor(), a.getObservacao(), a.getMotivoCancelamento(),
                a.isLembreteEnviado(), nota, a.getCriadoEm(),
                a.getSinalValor(), a.getSinalSituacao(), a.getSinalExpiraEm(), a.getSinalPagoEm(), a.getSinalGatewayId() != null);
    }

    public static AgendamentoPublicoResponseDTO toPublico(Agendamento a, boolean podeCancelar, boolean podeAvaliar, Integer nota) {
        return new AgendamentoPublicoResponseDTO(a.getCodigo(), a.getStatus(),
                ClienteMapper.primeiroNome(a.getCliente().getNome()),
                a.getUnidade().getNome(), a.getUnidade().getEndereco(), a.getUnidade().getWhatsapp(),
                a.getBarbeiro().getNome(), a.getServico().getNome(), a.getServico().getDuracaoMinutos(),
                a.getInicio(), a.getFim(), a.getValor(), a.getDesconto(), a.valorAPagar(),
                podeCancelar, podeAvaliar, nota,
                a.getSinalValor(), a.getSinalSituacao(), a.getSinalExpiraEm());
    }
}
