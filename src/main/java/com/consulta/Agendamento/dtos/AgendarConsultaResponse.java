package com.consulta.Agendamento.dtos;

import java.time.LocalDateTime;

import com.consulta.Agendamento.services.enums.StatusConsulta;

public record AgendarConsultaResponse(
    Integer id,
    Integer tutorId,
    Integer petId,
    Integer veterinarioId,
    LocalDateTime diaHora,
    StatusConsulta status
) {

}
