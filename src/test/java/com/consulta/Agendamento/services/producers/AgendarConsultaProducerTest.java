package com.consulta.Agendamento.services.producers;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.consulta.Agendamento.config.KafkaTopicConfig;
import com.consulta.Agendamento.services.enums.AgendarConsultaEvent;
import com.consulta.Agendamento.services.enums.TipoEvento;

@ExtendWith(MockitoExtension.class)
class AgendarConsultaProducerTest {

    @Mock
    private KafkaTemplate<String, AgendarConsultaEvent> kafkaTemplate;

    @InjectMocks
    private AgendarConsultaProducer producer;

    private final AgendarConsultaEvent evento = new AgendarConsultaEvent(
            TipoEvento.CRIADO, 10, "Ana", "ana@email.com", "Rex", "Dr. João",
            LocalDateTime.of(2099, 1, 10, 10, 0), LocalDateTime.now());

    @Test
    void devePublicarNoTopicoComChaveDaConsulta() {
        SendResult<String, AgendarConsultaEvent> resultado = new SendResult<>(
                new ProducerRecord<>(KafkaTopicConfig.AGENDAR_CONSULTA, "AGENDAR-10", evento),
                new RecordMetadata(new TopicPartition(KafkaTopicConfig.AGENDAR_CONSULTA, 1), 0, 0, 0, 0, 0));
        when(kafkaTemplate.send(KafkaTopicConfig.AGENDAR_CONSULTA, "AGENDAR-10", evento))
                .thenReturn(CompletableFuture.completedFuture(resultado));

        producer.publicar(evento);

        verify(kafkaTemplate).send(KafkaTopicConfig.AGENDAR_CONSULTA, "AGENDAR-10", evento);
    }

    @Test
    void naoDevePropagarFalhaDeEnvio() {
        when(kafkaTemplate.send(KafkaTopicConfig.AGENDAR_CONSULTA, "AGENDAR-10", evento))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker indisponível")));

        assertThatCode(() -> producer.publicar(evento)).doesNotThrowAnyException();

        verify(kafkaTemplate).send(KafkaTopicConfig.AGENDAR_CONSULTA, "AGENDAR-10", evento);
    }
}
