package com.consulta.Agendamento.services.consumers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.consulta.Agendamento.entities.PetEntity;
import com.consulta.Agendamento.repositories.PetRepository;
import com.consulta.Agendamento.services.enums.Especie;
import com.consulta.Agendamento.services.enums.PetEvent;
import com.consulta.Agendamento.services.enums.TipoEvento;

@ExtendWith(MockitoExtension.class)
class PetEventConsumerTest {

    @Mock
    private PetRepository petRepository;

    @InjectMocks
    private PetEventConsumer consumer;

    @Captor
    private ArgumentCaptor<PetEntity> petCaptor;

    private PetEvent evento(TipoEvento tipo) {
        return new PetEvent(tipo, 2, "Rex", "Labrador", LocalDate.of(2020, 5, 1),
                Especie.CACHORRO, 1, LocalDateTime.now());
    }

    @Test
    void deveSalvarPetAtivoQuandoEventoCriado() {
        consumer.consumir(evento(TipoEvento.CRIADO));

        verify(petRepository).save(petCaptor.capture());
        PetEntity salvo = petCaptor.getValue();
        assertThat(salvo.getId()).isEqualTo(2);
        assertThat(salvo.getNome()).isEqualTo("Rex");
        assertThat(salvo.getEspecie()).isEqualTo(Especie.CACHORRO);
        assertThat(salvo.getTutorId()).isEqualTo(1);
        assertThat(salvo.getAtivo()).isTrue();
    }

    @Test
    void deveSobrescreverPetQuandoEventoAtualizado() {
        consumer.consumir(evento(TipoEvento.ATUALIZADO));

        verify(petRepository).save(petCaptor.capture());
        assertThat(petCaptor.getValue().getId()).isEqualTo(2);
        assertThat(petCaptor.getValue().getAtivo()).isTrue();
    }

    @Test
    void deveInativarPetQuandoEventoRemovido() {
        PetEntity existente = new PetEntity(2, "Rex", Especie.CACHORRO, 1, true);
        when(petRepository.findById(2)).thenReturn(Optional.of(existente));

        consumer.consumir(evento(TipoEvento.REMOVIDO));

        assertThat(existente.getAtivo()).isFalse();
        verify(petRepository).save(existente);
    }

    @Test
    void deveIgnorarDelecaoDePetInexistente() {
        when(petRepository.findById(2)).thenReturn(Optional.empty());

        consumer.consumir(evento(TipoEvento.REMOVIDO));

        verify(petRepository, never()).save(any());
    }
}
