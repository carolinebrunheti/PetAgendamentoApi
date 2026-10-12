package com.consulta.Agendamento.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration 
public class KafkaTopicConfig {

    public static final String AGENDAR_CONSULTA = "agendar_consulta";

//partikcao, replicas e bbuild
    @Bean
    public NewTopic agendarConsultaTopico(){
        return TopicBuilder.name(AGENDAR_CONSULTA)
        .partitions(3)
        .replicas(1)
        .build();
    }
}
