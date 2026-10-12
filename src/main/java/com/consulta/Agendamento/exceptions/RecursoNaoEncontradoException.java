package com.consulta.Agendamento.exceptions;

public class RecursoNaoEncontradoException extends RuntimeException {
    
    public RecursoNaoEncontradoException(String mensagem){
        super(mensagem);
    }
}
