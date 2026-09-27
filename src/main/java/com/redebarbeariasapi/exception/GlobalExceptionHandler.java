package com.redebarbeariasapi.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<Map<String, Object>> resposta(HttpStatus status, String erro) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("erro", erro);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> naoEncontrado(ResourceNotFoundException ex) {
        return resposta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> negocio(BusinessException ex) {
        return resposta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ValidacaoException.class)
    public ResponseEntity<Map<String, Object>> validacao(ValidacaoException ex) {
        return resposta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(LimiteRequisicoesException.class)
    public ResponseEntity<Map<String, Object>> limite(LimiteRequisicoesException ex) {
        return resposta(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> rotaInexistente(NoResourceFoundException ex) {
        return resposta(HttpStatus.NOT_FOUND, "Rota não encontrada. Esta é uma API REST — veja a documentação em /swagger-ui.html");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> camposInvalidos(MethodArgumentNotValidException ex) {
        Map<String, String> erros = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            erros.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ResponseEntity<Map<String, Object>> r = resposta(HttpStatus.BAD_REQUEST,
                "Verifique os campos: " + String.join("; ", erros.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).toList()));
        r.getBody().put("erros", erros);
        return r;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> acessoNegado(AccessDeniedException ex) {
        return resposta(HttpStatus.FORBIDDEN, "Acesso negado — seu usuário não tem permissão pra essa ação.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> jsonInvalido(HttpMessageNotReadableException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "JSON inválido ou campo com tipo/formato errado no corpo da requisição.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> tipoErrado(MethodArgumentTypeMismatchException ex) {
        String tipo = ex.getRequiredType() == null ? "outro formato" : ex.getRequiredType().getSimpleName();
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro '" + ex.getName() + "' inválido: esperado " + tipo + ".");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> parametroFaltando(MissingServletRequestParameterException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: " + ex.getParameterName());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> metodo(HttpRequestMethodNotSupportedException ex) {
        return resposta(HttpStatus.METHOD_NOT_ALLOWED, "Método " + ex.getMethod() + " não suportado nesta rota.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> uploadGrande(MaxUploadSizeExceededException ex) {
        return resposta(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo grande demais (máximo 5 MB).");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integridade(DataIntegrityViolationException ex) {
        return resposta(HttpStatus.CONFLICT, "Não é possível concluir: registro duplicado ou vinculado a outro(s). Desative em vez de excluir, ou remova os vínculos antes.");
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.redebarbeariasapi.sistema.AlertaService alertas;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generico(Exception ex, jakarta.servlet.http.HttpServletRequest req) {
        log.error("Erro inesperado", ex);
        if (alertas != null) {
            // erro 500 = bug ou servico fora: o responsavel fica sabendo na hora (1 aviso por tipo de erro/rota por hora)
            StackTraceElement onde = java.util.Arrays.stream(ex.getStackTrace())
                    .filter(s -> s.getClassName().startsWith("com.redebarbeariasapi")).findFirst().orElse(null);
            String rota = req.getMethod() + " " + req.getRequestURI();
            alertas.avisar("500:" + ex.getClass().getName() + ":" + rota.replaceAll("/\\d+", "/{id}"), "🔴 Erro inesperado em " + rota,
                    ex.getClass().getSimpleName() + ": " + ex.getMessage() + (onde == null ? "" : "\nem " + onde));
        }
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado. Tente novamente; se persistir, fale com o suporte.");
    }
}
