package com.consulta.Agendamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;

import com.consulta.Agendamento.repositories.AgendarConsultaRepository;
import com.consulta.Agendamento.repositories.PetRepository;
import com.consulta.Agendamento.repositories.TutorRepository;
import com.consulta.Agendamento.repositories.VeterinarioRepository;
import com.consulta.Agendamento.services.enums.Especie;
import com.consulta.Agendamento.services.enums.PetEvent;
import com.consulta.Agendamento.services.enums.Role;
import com.consulta.Agendamento.services.enums.StatusConsulta;
import com.consulta.Agendamento.services.enums.TipoEvento;
import com.consulta.Agendamento.services.enums.UsuarioEvent;

/**
 * Teste de integracao: a aplicacao inteira sobe contra Postgres e Kafka reais (Testcontainers).
 * Cobre o caminho completo do servico: eventos de cadastro chegam pelo Kafka e viram
 * tutor/pet/veterinario no banco; o POST /agendarConsulta com um JWT real grava a consulta
 * e publica o evento no topico agendar_consulta (que o historico consome).
 */
@SpringBootTest(properties = "jwt.secret=" + AgendamentoApplicationTests.SEGREDO)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AgendamentoApplicationTests {

    static final String SEGREDO = "chave-de-teste-com-no-minimo-32-caracteres";

    @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired private KafkaContainer kafka;
    @Autowired private MockMvc mockMvc;
    @Autowired private TutorRepository tutorRepository;
    @Autowired private PetRepository petRepository;
    @Autowired private VeterinarioRepository veterinarioRepository;
    @Autowired private AgendarConsultaRepository consultaRepository;

    @Test
    void contextLoads() {
        // subir o contexto ja prova que o Flyway criou as tabelas e o Hibernate as validou
        assertThat(consultaRepository.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void cadastrosChegamPeloKafkaEAgendamentoPublicaEventoParaOHistorico() throws Exception {
        cadastrar(501, 601, 701);
        LocalDateTime diaHora = horarioFuturo(1);

        String resposta = mockMvc.perform(post("/agendarConsulta")
                        .header("Authorization", "Bearer " + token(Role.ATENDENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(501, 701, 601, diaHora)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andReturn().getResponse().getContentAsString();
        int consultaId = Integer.parseInt(resposta.replaceAll(".*\"id\":(\\d+).*", "$1"));

        assertThat(consultaRepository.findById(consultaId))
                .hasValueSatisfying(c -> assertThat(c.getStatus()).isEqualTo(StatusConsulta.AGENDADA));

        // o evento que o historico vai consumir saiu de verdade no Kafka
        ConsumerRecord<String, String> evento = lerEvento("AGENDAR-" + consultaId);
        assertThat(evento.value())
                .contains("\"tipoEvento\":\"CRIADO\"")
                .contains("\"consultaId\":" + consultaId)
                .contains("\"tutorNome\":\"Tutor 501\"")
                .contains("\"petNome\":\"Pet 701\"");
    }

    @Test
    void mesmoVeterinarioNoMesmoHorarioDa409() throws Exception {
        cadastrar(502, 602, 702);
        LocalDateTime diaHora = horarioFuturo(2);
        String corpo = corpo(502, 702, 602, diaHora);

        mockMvc.perform(post("/agendarConsulta").header("Authorization", "Bearer " + token(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());

        // a checagem de horario ocupado e uma consulta no Postgres de verdade
        mockMvc.perform(post("/agendarConsulta").header("Authorization", "Bearer " + token(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());
    }

    @Test
    void petRemovidoNoCadastroNaoPodeSerAgendado() throws Exception {
        cadastrar(503, 603, 703);
        enviar("pets-eventos", 703, new PetEvent(TipoEvento.REMOVIDO, 703, "Pet 703", "SRD",
                LocalDate.of(2020, 1, 1), Especie.CACHORRO, 503, LocalDateTime.now()));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(petRepository.findById(703)).hasValueSatisfying(p -> assertThat(p.getAtivo()).isFalse()));

        mockMvc.perform(post("/agendarConsulta").header("Authorization", "Bearer " + token(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(503, 703, 603, horarioFuturo(3))))
                .andExpect(status().isNotFound());
    }

    @Test
    void tutorNaoPodeAgendar() throws Exception {
        mockMvc.perform(post("/agendarConsulta").header("Authorization", "Bearer " + token(Role.TUTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(1, 1, 1, horarioFuturo(4))))
                .andExpect(status().isForbidden());
    }

    // publica os eventos que o petServices mandaria e espera os consumidores gravarem
    private void cadastrar(int tutorId, int vetId, int petId) {
        enviar("usuarios-eventos", tutorId, new UsuarioEvent(TipoEvento.CRIADO, tutorId, "Tutor " + tutorId,
                "tutor" + tutorId + "@teste.dev", Role.TUTOR, LocalDateTime.now()));
        enviar("usuarios-eventos", vetId, new UsuarioEvent(TipoEvento.CRIADO, vetId, "Vet " + vetId,
                "vet" + vetId + "@teste.dev", Role.VETERINARIO, LocalDateTime.now()));
        enviar("pets-eventos", petId, new PetEvent(TipoEvento.CRIADO, petId, "Pet " + petId, "SRD",
                LocalDate.of(2020, 1, 1), Especie.CACHORRO, tutorId, LocalDateTime.now()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(tutorRepository.findById(tutorId)).isPresent();
            assertThat(veterinarioRepository.findById(vetId)).isPresent();
            assertThat(petRepository.findById(petId)).isPresent();
        });
    }

    private void enviar(String topico, int chave, Object evento) {
        try {
            kafkaTemplate.send(topico, String.valueOf(chave), evento).get();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao publicar no Kafka de teste", e);
        }
    }

    // le o topico agendar_consulta desde o inicio ate achar a mensagem com essa chave
    private ConsumerRecord<String, String> lerEvento(String chave) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "teste-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("agendar_consulta"));
            long limite = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < limite) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    if (chave.equals(r.key())) {
                        return r;
                    }
                }
            }
        }
        throw new AssertionError("Evento " + chave + " nao apareceu no topico agendar_consulta");
    }

    private LocalDateTime horarioFuturo(int dias) {
        return LocalDateTime.now().plusDays(dias).truncatedTo(ChronoUnit.HOURS);
    }

    private String corpo(int tutorId, int petId, int vetId, LocalDateTime diaHora) {
        return """
                {"tutorId": %d, "petId": %d, "veterinarioId": %d, "diaHora": "%s"}
                """.formatted(tutorId, petId, vetId, diaHora);
    }

    // gera um token igual ao do petServices: HS256 com as claims "id" e "role"
    private String token(Role role) {
        var chave = new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var claims = JwtClaimsSet.builder()
                .subject("teste@petservices.dev")
                .claim("id", 1)
                .claim("role", role.name())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return NimbusJwtEncoder.withSecretKey(chave).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
