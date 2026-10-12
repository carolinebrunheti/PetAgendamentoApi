package com.consulta.Agendamento.services.consumers;

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

        if(evento.tipoEvento() ==  TipoEvento.DELETADO){
            petRepository.findById(evento.id()).ifPresent(pet -> {
                pet.setAtivo(false);
                petRepository.save(pet);
            });
            return;
        }
        petRepository.save(new PetEntity(evento.id(), evento.nome(), evento.especie(), evento.donoId(), true));
    }
    
}
