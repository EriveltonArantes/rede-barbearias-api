package com.redebarbeariasapi.exception;

/** Muitas requisicoes do mesmo IP nas rotas publicas/login. Vira HTTP 429. */
public class LimiteRequisicoesException extends RuntimeException {
    public LimiteRequisicoesException() {
        super("Muitas tentativas em pouco tempo. Aguarde alguns minutos e tente de novo.");
    }
}
