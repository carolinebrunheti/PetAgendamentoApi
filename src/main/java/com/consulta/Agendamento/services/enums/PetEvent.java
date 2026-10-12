package com.consulta.Agendamento.services.enums;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PetEvent(
        TipoEvento tipoEvento,
        Integer id,
        String nome,
        String raca,
        LocalDate dataNascimento,
        Especie especie,
        Integer donoId,
        LocalDateTime ocorridoEm
) {}