package com.consulta.Agendamento.services.enums;

import java.time.LocalDateTime;

public record UsuarioEvent(
        TipoEvento tipoEvento,
        Integer id,
        String nome,
        String email,
        Role role,
        LocalDateTime ocorridoEm
) {
    
}