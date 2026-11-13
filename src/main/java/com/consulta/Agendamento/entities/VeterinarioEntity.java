package com.consulta.Agendamento.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tb_veterinario")
@Getter
@Setter
@NoArgsConstructor
public class VeterinarioEntity {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Boolean ativo = true;

    // momento (ocorridoEm) do ultimo evento aplicado; null nos registros anteriores a esta coluna
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    public VeterinarioEntity(Integer id, String nome, String email, Boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.ativo = ativo;
    }
}
