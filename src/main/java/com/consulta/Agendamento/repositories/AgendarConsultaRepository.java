package com.consulta.Agendamento.repositories;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.consulta.Agendamento.entities.AgendarConsultaEntity;
import com.consulta.Agendamento.services.enums.StatusConsulta;

@Repository
public interface AgendarConsultaRepository extends JpaRepository<AgendarConsultaEntity, Integer>{
     boolean existsByVeterinarioIdAndDiaHoraAndStatus(Integer veterinarioId, LocalDateTime diaHora, StatusConsulta status);

     boolean existsByVeterinarioIdAndDiaHoraAndStatusAndIdNot(
            Integer veterinarioId, LocalDateTime diaHora, StatusConsulta status, Integer id);

}
