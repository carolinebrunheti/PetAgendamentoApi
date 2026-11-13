package com.consulta.Agendamento.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import lombok.extern.slf4j.Slf4j;

/**
 * Mensagem que nao da para processar: tenta mais 3 vezes (2 s entre elas) e depois manda para
 * o topico <topico>-dlt, liberando a particao. Erro de desserializacao (JSON invalido) nao
 * adianta repetir, entao vai direto para a DLT.
 */
@Slf4j
@Configuration
public class KafkaErrorConfig {

    @Bean
    public DefaultErrorHandler errorHandler(ProducerFactory<Object, Object> producerFactory,
            KafkaTemplate<Object, Object> template) {
        // JSON invalido chega aqui como os bytes originais (byte[]). Se fosse pelo template JSON,
        // o Jackson gravaria esses bytes na DLT como texto base64. Por isso: byte[] sai como
        // bytes, qualquer outro objeto sai como JSON (a ordem do mapa importa).
        KafkaTemplate<Object, Object> templateBytes = new KafkaTemplate<>(producerFactory,
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, templateBytes);
        templates.put(Object.class, template);
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(templates);

        return new DefaultErrorHandler((registro, erro) -> {
            // sem este log a mensagem sumia para a DLT em silencio
            log.error("Mensagem enviada para a DLT: topico={} particao={} offset={} chave={} erro={}",
                    registro.topic(), registro.partition(), registro.offset(), registro.key(),
                    erro.getCause() != null ? erro.getCause().toString() : erro.toString());
            dlt.accept(registro, erro);
        }, new FixedBackOff(2000L, 3));
    }
}
