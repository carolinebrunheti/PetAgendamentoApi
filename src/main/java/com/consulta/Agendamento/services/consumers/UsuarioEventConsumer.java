package com.consulta.Agendamento.services.consumers;

import java.util.Optional;

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
        Optional<TutorEntity> atual = tutorRepository.findById(evento.id());
        if (atual.isPresent() && OrdemEventos.jaAplicado(atual.get().getAtualizadoEm(), evento.ocorridoEm())) {
            log.info("Ignorado {} do tutor {}: evento de {} nao e mais novo que {}",
                    evento.tipoEvento(), evento.id(), evento.ocorridoEm(), atual.get().getAtualizadoEm());
            return;
        }
        if(evento.tipoEvento() == TipoEvento.REMOVIDO){
            atual.ifPresent(tutor -> {
                tutor.setAtivo(false);
                tutor.setAtualizadoEm(evento.ocorridoEm());
                tutorRepository.save(tutor);
            });
            return;
        }
        TutorEntity tutor = new TutorEntity(evento.id(), evento.nome(), evento.email(), true);
        tutor.setAtualizadoEm(evento.ocorridoEm());
        tutorRepository.save(tutor);
    }

    private void processarVeterinario(UsuarioEvent evento){
        Optional<VeterinarioEntity> atual = veterinarioRepository.findById(evento.id());
        if (atual.isPresent() && OrdemEventos.jaAplicado(atual.get().getAtualizadoEm(), evento.ocorridoEm())) {
            log.info("Ignorado {} do veterinario {}: evento de {} nao e mais novo que {}",
                    evento.tipoEvento(), evento.id(), evento.ocorridoEm(), atual.get().getAtualizadoEm());
            return;
        }
        if(evento.tipoEvento() == TipoEvento.REMOVIDO){
            atual.ifPresent(vet -> {
                vet.setAtivo(false);
                vet.setAtualizadoEm(evento.ocorridoEm());
                veterinarioRepository.save(vet);
            });
            return;
        }
        VeterinarioEntity vet = new VeterinarioEntity(evento.id(), evento.nome(), evento.email(), true);
        vet.setAtualizadoEm(evento.ocorridoEm());
        veterinarioRepository.save(vet);
    }
}
