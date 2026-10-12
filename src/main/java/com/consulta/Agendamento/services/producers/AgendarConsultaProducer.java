package com.consulta.Agendamento.services.producers;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.consulta.Agendamento.config.KafkaTopicConfig;
import com.consulta.Agendamento.services.enums.AgendarConsultaEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j 
@RequiredArgsConstructor 
@Component 
public class AgendarConsultaProducer {
    
    private final KafkaTemplate<String, AgendarConsultaEvent> kafkaTemplate;

    public void publicar(AgendarConsultaEvent evento){
        String chave = "AGENDAR-" + evento.consultaId();

        kafkaTemplate.send(KafkaTopicConfig.AGENDAR_CONSULTA, chave, evento)
            .whenComplete((resultado, erro) -> {
                if(erro != null){
                    log.error("Falha ao publicar evento {}: {}", chave, erro.getMessage());
                }else {
                    log.info("Evento {} {} publicado na particao {}",
                        evento.tipoEvento(), chave,
                        resultado.getRecordMetadata().partition()
                    );
                }
            });
    }

}
