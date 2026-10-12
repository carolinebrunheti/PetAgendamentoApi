package com.consulta.Agendamento.dtos;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public record AgendarConsultaDTO(
        @NotNull Integer tutorId,
        @NotNull Integer petId,
        @NotNull Integer veterinarioId,
        @NotNull @Future LocalDateTime diaHora
) {}