package com.redebarbeariasapi.model;

/**
 * Ciclo do sinal: PENDENTE (esperando o Pix) -> PAGO -> ABATIDO no dia do atendimento.
 * Cancelou com antecedencia: A_DEVOLVER -> DEVOLVIDO. Faltou ou cancelou em cima da hora: RETIDO.
 * Nao pagou no prazo: EXPIRADO.
 */
public enum SituacaoSinal {
    PENDENTE, PAGO, ABATIDO, A_DEVOLVER, DEVOLVIDO, RETIDO, EXPIRADO;

    public boolean recebido() {
        return this == PAGO || this == ABATIDO || this == A_DEVOLVER || this == DEVOLVIDO || this == RETIDO;
    }
}
