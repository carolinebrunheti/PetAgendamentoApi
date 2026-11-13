package com.consulta.Agendamento.entities;

import java.time.LocalDateTime;

import com.consulta.Agendamento.services.enums.Especie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tb_pet")
@Getter
@Setter
@NoArgsConstructor
public class PetEntity {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Especie especie;

    @Column(nullable = false)
    private Integer tutorId;

    @Column(nullable = false)
    private Boolean ativo = true;

    // momento (ocorridoEm) do ultimo evento aplicado; null nos registros anteriores a esta coluna
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    public PetEntity(Integer id, String nome, Especie especie, Integer tutorId, Boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.especie = especie;
        this.tutorId = tutorId;
        this.ativo = ativo;
    }
}
