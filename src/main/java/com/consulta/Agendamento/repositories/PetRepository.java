package com.consulta.Agendamento.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.consulta.Agendamento.entities.PetEntity;

@Repository 
public interface PetRepository extends JpaRepository<PetEntity, Integer> {

    
}
