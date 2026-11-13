package com.consulta.Agendamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import javax.crypto.spec.SecretKeySpec;

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

import com.consulta.Agendamento.repositories.PetRepository;
import com.consulta.Agendamento.repositories.TutorRepository;
import com.consulta.Agendamento.repositories.VeterinarioRepository;
import com.consulta.Agendamento.services.enums.Especie;
import com.consulta.Agendamento.services.enums.PetEvent;
import com.consulta.Agendamento.services.enums.Role;
import com.consulta.Agendamento.services.enums.TipoEvento;
import com.consulta.Agendamento.services.enums.UsuarioEvent;

/**
 * Seguranca com a aplicacao real: tokens invalidos, papeis sem permissao e, principalmente,
 * um tutor tentando ver ou mexer na consulta de outro tutor trocando o id na URL.
 * Mesma configuracao do AgendamentoApplicationTests: o Spring reaproveita o contexto e os conteineres.
 */
@SpringBootTest(properties = "jwt.secret=" + AgendamentoApplicationTests.SEGREDO)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SegurancaIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired private TutorRepository tutorRepository;
    @Autowired private PetRepository petRepository;
    @Autowired private VeterinarioRepository veterinarioRepository;

    @Test
    void tokenExpiradoDa401() throws Exception {
        String token = token("ADMIN", 1, Instant.now().minusSeconds(600), Instant.now().minusSeconds(120));
        mockMvc.perform(get("/agendarConsulta").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSemIdOuComRoleInventadoDa401() throws Exception {
        var chave = new SecretKeySpec(AgendamentoApplicationTests.SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var semClaims = JwtClaimsSet.builder().subject("x@teste.dev")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        String tokenSemClaims = NimbusJwtEncoder.withSecretKey(chave).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), semClaims)).getTokenValue();

        // sem a validacao das claims, GET /agendarConsulta dava 500 (NullPointerException no UsuarioLogado)
        mockMvc.perform(get("/agendarConsulta").header("Authorization", "Bearer " + tokenSemClaims))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/agendarConsulta").header("Authorization", "Bearer " + tokenValido("SUPERUSUARIO", 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tutorSoEnxergaAsPropriasConsultas() throws Exception {
        int consultaDeA = agendar(911, 921, 931, 1);
        int consultaDeB = agendar(912, 922, 932, 2);

        // o tutor A ve a propria consulta...
        mockMvc.perform(get("/agendarConsulta/" + consultaDeA).header("Authorization", "Bearer " + tokenValido("TUTOR", 911)))
                .andExpect(status().isOk());
        // ...mas nao a do tutor B, mesmo sabendo o id
        mockMvc.perform(get("/agendarConsulta/" + consultaDeB).header("Authorization", "Bearer " + tokenValido("TUTOR", 911)))
                .andExpect(status().isForbidden());
        // e na listagem so aparecem as dele
        mockMvc.perform(get("/agendarConsulta").param("size", "50").header("Authorization", "Bearer " + tokenValido("TUTOR", 911)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.tutorId != 911)]").isEmpty())
                .andExpect(jsonPath("$.content[?(@.id == " + consultaDeA + ")]").isNotEmpty());
    }

    @Test
    void tutorNaoCancelaNemRemarca() throws Exception {
        int consulta = agendar(913, 923, 933, 3);
        String tutor = tokenValido("TUTOR", 913);

        mockMvc.perform(patch("/agendarConsulta/" + consulta + "/cancelar").header("Authorization", "Bearer " + tutor))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/agendarConsulta/" + consulta).header("Authorization", "Bearer " + tutor)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(913, 933, 923, horarioFuturo(4))))
                .andExpect(status().isForbidden());
    }

    @Test
    void veterinarioCancelaMasNaoAgenda() throws Exception {
        int consulta = agendar(914, 924, 934, 5);
        String vet = tokenValido("VETERINARIO", 924);

        mockMvc.perform(post("/agendarConsulta").header("Authorization", "Bearer " + vet)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(914, 934, 924, horarioFuturo(6))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/agendarConsulta/" + consulta + "/cancelar").header("Authorization", "Bearer " + vet))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }

    // ---------- ajudantes ----------

    // cadastra tutor/vet/pet pelo Kafka (como o petServices faria) e agenda como ATENDENTE
    private int agendar(int tutorId, int vetId, int petId, int dias) throws Exception {
        enviar("usuarios-eventos", new UsuarioEvent(TipoEvento.CRIADO, tutorId, "Tutor " + tutorId,
                "tutor" + tutorId + "@teste.dev", Role.TUTOR, LocalDateTime.now()));
        enviar("usuarios-eventos", new UsuarioEvent(TipoEvento.CRIADO, vetId, "Vet " + vetId,
                "vet" + vetId + "@teste.dev", Role.VETERINARIO, LocalDateTime.now()));
        enviar("pets-eventos", new PetEvent(TipoEvento.CRIADO, petId, "Pet " + petId, "SRD",
                LocalDate.of(2020, 1, 1), Especie.GATO, tutorId, LocalDateTime.now()));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(tutorRepository.findById(tutorId)).isPresent();
            assertThat(veterinarioRepository.findById(vetId)).isPresent();
            assertThat(petRepository.findById(petId)).isPresent();
        });

        String resposta = mockMvc.perform(post("/agendarConsulta")
                        .header("Authorization", "Bearer " + tokenValido("ATENDENTE", 1))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(tutorId, petId, vetId, horarioFuturo(dias))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Integer.parseInt(resposta.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private void enviar(String topico, Object evento) {
        try {
            kafkaTemplate.send(topico, evento).get();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao publicar no Kafka de teste", e);
        }
    }

    private LocalDateTime horarioFuturo(int dias) {
        return LocalDateTime.now().plusDays(dias).truncatedTo(ChronoUnit.HOURS);
    }

    private String corpo(int tutorId, int petId, int vetId, LocalDateTime diaHora) {
        return """
                {"tutorId": %d, "petId": %d, "veterinarioId": %d, "diaHora": "%s"}
                """.formatted(tutorId, petId, vetId, diaHora);
    }

    private String tokenValido(String role, int id) {
        return token(role, id, Instant.now(), Instant.now().plusSeconds(300));
    }

    private String token(String role, int id, Instant emitidoEm, Instant expiraEm) {
        var chave = new SecretKeySpec(AgendamentoApplicationTests.SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var claims = JwtClaimsSet.builder()
                .subject("teste@petservices.dev")
                .claim("id", id)
                .claim("role", role)
                .issuedAt(emitidoEm)
                .expiresAt(expiraEm)
                .build();
        return NimbusJwtEncoder.withSecretKey(chave).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
