package com.consulta.Agendamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;

import com.consulta.Agendamento.repositories.PetRepository;
import com.consulta.Agendamento.repositories.TutorRepository;

/**
 * Mensagens problematicas nos topicos de cadastro, contra Kafka e Postgres reais:
 * - DLT: mensagem que nao da para processar vai para <topico>-dlt com o conteudo original
 *   e o consumidor continua lendo as proximas;
 * - idempotencia: o Kafka entrega "pelo menos uma vez", entao um evento antigo pode chegar
 *   de novo depois de um mais novo, e isso nao pode reativar quem foi removido.
 * Mesma configuracao do AgendamentoApplicationTests: o Spring reaproveita o contexto e os conteineres.
 */
@SpringBootTest(properties = "jwt.secret=" + AgendamentoApplicationTests.SEGREDO)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MensagensKafkaIntegrationTest {

    private static final String USUARIOS = "usuarios-eventos";
    private static final String PETS = "pets-eventos";
    private static final LocalDateTime NOVE_HORAS = LocalDateTime.of(2026, 11, 1, 9, 0);
    private static final LocalDateTime DEZ_HORAS = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Autowired private KafkaContainer kafka;
    @Autowired private TutorRepository tutorRepository;
    @Autowired private PetRepository petRepository;

    @Test
    void mensagemQueNaoEJsonVaiParaDltComConteudoOriginalEOConsumidorSegue() {
        enviarBruto(USUARIOS, "TUTOR-801", "isto nao e json");
        enviarBruto(USUARIOS, "TUTOR-801", tutor("CRIADO", 802, NOVE_HORAS));

        // a mensagem valida que veio depois foi processada: a ruim nao travou a particao
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(tutorRepository.findById(802)).isPresent());

        ConsumerRecord<String, byte[]> naDlt = lerDlt(USUARIOS + "-dlt", "TUTOR-801");
        assertThat(new String(naDlt.value(), StandardCharsets.UTF_8)).isEqualTo("isto nao e json");
        assertThat(cabecalho(naDlt, "kafka_dlt-exception-fqcn")).contains("DeserializationException");
    }

    @Test
    void criadoAntigoReentregueNaoReativaTutorRemovido() {
        enviarBruto(USUARIOS, "TUTOR-803", tutor("CRIADO", 803, NOVE_HORAS));
        enviarBruto(USUARIOS, "TUTOR-803", tutor("REMOVIDO", 803, DEZ_HORAS));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(tutorRepository.findById(803)).hasValueSatisfying(t -> assertThat(t.getAtivo()).isFalse()));

        // o CRIADO das 9h chega de novo (ex.: o consumidor caiu antes de confirmar o offset)
        enviarBruto(USUARIOS, "TUTOR-803", tutor("CRIADO", 803, NOVE_HORAS));
        enviarBruto(USUARIOS, "TUTOR-803", tutor("CRIADO", 804, NOVE_HORAS)); // marcador, mesma particao
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(tutorRepository.findById(804)).isPresent());

        assertThat(tutorRepository.findById(803).orElseThrow().getAtivo())
                .as("um evento mais antigo nao pode reativar um tutor removido")
                .isFalse();
    }

    @Test
    void criadoAntigoReentregueNaoReativaPetRemovido() {
        enviarBruto(PETS, "PET-805", pet("CRIADO", 805, NOVE_HORAS));
        enviarBruto(PETS, "PET-805", pet("REMOVIDO", 805, DEZ_HORAS));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(petRepository.findById(805)).hasValueSatisfying(p -> assertThat(p.getAtivo()).isFalse()));

        enviarBruto(PETS, "PET-805", pet("CRIADO", 805, NOVE_HORAS));
        enviarBruto(PETS, "PET-805", pet("CRIADO", 806, NOVE_HORAS)); // marcador, mesma particao
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(petRepository.findById(806)).isPresent());

        assertThat(petRepository.findById(805).orElseThrow().getAtivo())
                .as("um evento mais antigo nao pode reativar um pet removido")
                .isFalse();
    }

    @Test
    void atualizacaoMaisNovaEAplicada() {
        enviarBruto(USUARIOS, "TUTOR-807", tutor("CRIADO", 807, NOVE_HORAS));
        enviarBruto(USUARIOS, "TUTOR-807", tutor("ATUALIZADO", 807, DEZ_HORAS).replace("Tutor 807", "Tutor Novo"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(tutorRepository.findById(807))
                        .hasValueSatisfying(t -> assertThat(t.getNome()).isEqualTo("Tutor Novo")));
    }

    private String tutor(String tipo, int id, LocalDateTime ocorridoEm) {
        return """
                {"tipoEvento":"%s","id":%d,"nome":"Tutor %d","email":"tutor%d@teste.dev","role":"TUTOR","ocorridoEm":"%s"}"""
                .formatted(tipo, id, id, id, ocorridoEm);
    }

    private String pet(String tipo, int id, LocalDateTime ocorridoEm) {
        return """
                {"tipoEvento":"%s","id":%d,"nome":"Pet %d","raca":"SRD","dataNascimento":"2020-01-01",\
                "especie":"CACHORRO","donoId":1,"ocorridoEm":"%s"}"""
                .formatted(tipo, id, id, ocorridoEm);
    }

    // envia os bytes exatamente como estao, sem passar pelo serializador JSON da aplicacao
    private void enviarBruto(String topico, String chave, String valor) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topico, chave, valor.getBytes(StandardCharsets.UTF_8))).get();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao publicar no Kafka de teste", e);
        }
    }

    private ConsumerRecord<String, byte[]> lerDlt(String dlt, String chave) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "teste-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(dlt));
            long limite = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < limite) {
                for (ConsumerRecord<String, byte[]> r : consumer.poll(Duration.ofMillis(500))) {
                    if (chave.equals(r.key())) {
                        return r;
                    }
                }
            }
        }
        throw new AssertionError("Mensagem " + chave + " nao apareceu na " + dlt);
    }

    private String cabecalho(ConsumerRecord<?, ?> r, String nome) {
        Header h = r.headers().lastHeader(nome);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
