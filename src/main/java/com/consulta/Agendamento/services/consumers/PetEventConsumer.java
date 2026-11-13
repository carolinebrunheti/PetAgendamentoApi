package com.consulta.Agendamento.services.consumers;

import java.util.Optional;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.consulta.Agendamento.entities.PetEntity;
import com.consulta.Agendamento.repositories.PetRepository;
import com.consulta.Agendamento.services.enums.PetEvent;
import com.consulta.Agendamento.services.enums.TipoEvento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j 
@Component
@RequiredArgsConstructor
public class PetEventConsumer {

    private final PetRepository petRepository;

    @KafkaListener (
        topics = "pets-eventos",
        properties = "spring.json.value.default.type=com.consulta.Agendamento.services.enums.PetEvent"
    )
    public void consumir(PetEvent evento){
        log.info("Recebido {} do pet {}", evento.tipoEvento(), evento.id());

        Optional<PetEntity> atual = petRepository.findById(evento.id());
        if (atual.isPresent() && OrdemEventos.jaAplicado(atual.get().getAtualizadoEm(), evento.ocorridoEm())) {
            log.info("Ignorado {} do pet {}: evento de {} nao e mais novo que {}",
                    evento.tipoEvento(), evento.id(), evento.ocorridoEm(), atual.get().getAtualizadoEm());
            return;
        }
        if(evento.tipoEvento() == TipoEvento.REMOVIDO){
            atual.ifPresent(pet -> {
                pet.setAtivo(false);
                pet.setAtualizadoEm(evento.ocorridoEm());
                petRepository.save(pet);
            });
            return;
        }
        PetEntity pet = new PetEntity(evento.id(), evento.nome(), evento.especie(), evento.donoId(), true);
        pet.setAtualizadoEm(evento.ocorridoEm());
        petRepository.save(pet);
    }
    
}
