package com.consulta.Agendamento.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.consulta.Agendamento.entities.TutorEntity;

@Repository 
public interface TutorRepository extends JpaRepository<TutorEntity, Integer> {

    
}
