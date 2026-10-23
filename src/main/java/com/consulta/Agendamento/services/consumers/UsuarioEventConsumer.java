package com.consulta.Agendamento.services.consumers;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.consulta.Agendamento.entities.TutorEntity;
import com.consulta.Agendamento.entities.VeterinarioEntity;
import com.consulta.Agendamento.repositories.TutorRepository;
import com.consulta.Agendamento.repositories.VeterinarioRepository;
import com.consulta.Agendamento.services.enums.TipoEvento;
import com.consulta.Agendamento.services.enums.UsuarioEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j 
@Component 
@RequiredArgsConstructor 
public class UsuarioEventConsumer {
    
    private final TutorRepository tutorRepository;
    private final VeterinarioRepository veterinarioRepository;

    @KafkaListener(
        topics = "usuarios-eventos",
        properties = "spring.json.value.default.type=com.consulta.Agendamento.services.enums.UsuarioEvent"
    )
    public void consumir(UsuarioEvent evento){
         log.info("Recebido {} de {} {}", evento.tipoEvento(), evento.role(), evento.id());

         switch (evento.role()){
            case TUTOR -> processarTutor(evento);
            case VETERINARIO -> processarVeterinario(evento);
            default -> log.info("Evento de {} ignorado", evento.role());
         }
    }

    private void processarTutor(UsuarioEvent evento){
        if(evento.tipoEvento() == TipoEvento.REMOVIDO){
            tutorRepository.findById(evento.id()).ifPresent(tutor -> {
                tutor.setAtivo(false);
                tutorRepository.save(tutor);
            });
            return;
        }
        tutorRepository.save(new TutorEntity(evento.id(), evento.nome(), evento.email(), true));
    }

    private void processarVeterinario(UsuarioEvent evento){
        if(evento.tipoEvento() == TipoEvento.REMOVIDO){
            veterinarioRepository.findById(evento.id()).ifPresent(vet ->{
                vet.setAtivo(false);
                veterinarioRepository.save(vet);
            });
            return;
        }
        veterinarioRepository.save(new VeterinarioEntity(evento.id(),evento.nome(), evento.email(),true));
    }
}
