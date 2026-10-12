package com.consulta.Agendamento.entities;
import java.time.LocalDateTime;
import com.consulta.Agendamento.services.enums.StatusConsulta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="tb_agendamento")
@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 

public class AgendarConsultaEntity {
    
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private Integer tutorId;

    @Column(nullable = false)
    private Integer petId;

    @Column(nullable = false)
    private Integer veterinarioId;

    @Column(nullable = false)
    private LocalDateTime diaHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusConsulta status;
}

