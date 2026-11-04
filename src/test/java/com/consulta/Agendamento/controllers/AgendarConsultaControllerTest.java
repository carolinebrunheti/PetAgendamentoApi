package com.consulta.Agendamento.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.consulta.Agendamento.config.security.SecurityConfig;
import com.consulta.Agendamento.dtos.AgendarConsultaDTO;
import com.consulta.Agendamento.dtos.AgendarConsultaResponse;
import com.consulta.Agendamento.entities.AgendarConsultaEntity;
import com.consulta.Agendamento.exceptions.ConflitoException;
import com.consulta.Agendamento.exceptions.RecursoNaoEncontradoException;
import com.consulta.Agendamento.exceptions.RegraNegocioException;
import com.consulta.Agendamento.services.AgendarConsultaService;
import com.consulta.Agendamento.services.enums.StatusConsulta;

@WebMvcTest(AgendarConsultaController.class)
@Import(SecurityConfig.class) // traz as regras de autorizacao reais + o JwtDecoder para dentro do slice
@TestPropertySource(properties = "jwt.secret=chave-de-teste-com-no-minimo-32-caracteres")
class AgendarConsultaControllerTest {

    private static final LocalDateTime DIA_HORA = LocalDateTime.of(2099, 1, 10, 10, 0);

    private static final String BODY_VALIDO = """
            {"tutorId": 1, "petId": 2, "veterinarioId": 3, "diaHora": "2099-01-10T10:00:00"}
            """;

    private static final AgendarConsultaDTO REQUEST = new AgendarConsultaDTO(1, 2, 3, DIA_HORA);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgendarConsultaService agendarConsultaService;

    private AgendarConsultaResponse response(Integer id, StatusConsulta status) {
        return new AgendarConsultaResponse(id, 1, 2, 3, DIA_HORA, status);
    }

    // token como o petServices emite: claims "id" e "role", mais a permissao usada pelo hasRole
    private static RequestPostProcessor token(int id, String role) {
        return jwt().jwt(j -> j.claim("id", id).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Nested
    @WithMockUser(roles = "ADMIN")
    class AgendarNovaConsulta {

        @Test
        void deveRetornar201ComConsultaCriada() throws Exception {
            when(agendarConsultaService.agendarNovaConsulta(REQUEST)).thenReturn(response(10, StatusConsulta.AGENDADA));

            mockMvc.perform(post("/agendarConsulta").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.tutorId").value(1))
                    .andExpect(jsonPath("$.petId").value(2))
                    .andExpect(jsonPath("$.veterinarioId").value(3))
                    .andExpect(jsonPath("$.diaHora").value("2099-01-10T10:00:00"))
                    .andExpect(jsonPath("$.status").value("AGENDADA"));
        }

        @Test
        void deveRetornar400QuandoCamposObrigatoriosAusentes() throws Exception {
            mockMvc.perform(post("/agendarConsulta").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value("Dados inválidos na requisição"))
                    .andExpect(jsonPath("$.detalhes", hasSize(4)));

            verifyNoInteractions(agendarConsultaService);
        }

        @Test
        void deveRetornar400QuandoDataNoPassado() throws Exception {
            String body = """
                    {"tutorId": 1, "petId": 2, "veterinarioId": 3, "diaHora": "2000-01-10T10:00:00"}
                    """;

            mockMvc.perform(post("/agendarConsulta").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detalhes", hasSize(1)))
                    .andExpect(jsonPath("$.detalhes", hasItem(startsWith("diaHora:"))));

            verifyNoInteractions(agendarConsultaService);
        }

        @Test
        void deveRetornar400QuandoJsonMalFormado() throws Exception {
            mockMvc.perform(post("/agendarConsulta").contentType(MediaType.APPLICATION_JSON).content("{tutorId:"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value("JSON inválido ou com formato incorreto"));

            verifyNoInteractions(agendarConsultaService);
        }

        @Test
        void deveRetornar409QuandoHorarioOcupado() throws Exception {
            when(agendarConsultaService.agendarNovaConsulta(REQUEST))
                    .thenThrow(new ConflitoException("Veterinário já possui consulta neste horário"));

            mockMvc.perform(post("/agendarConsulta").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.mensagem").value("Veterinário já possui consulta neste horário"))
                    .andExpect(jsonPath("$.path").value("/agendarConsulta"));
        }

        @Test
        void deveRetornar400QuandoRegraDeNegocioViolada() throws Exception {
            when(agendarConsultaService.agendarNovaConsulta(REQUEST))
                    .thenThrow(new RegraNegocioException("O pet não pertence a este tutor"));

            mockMvc.perform(post("/agendarConsulta").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value("O pet não pertence a este tutor"));
        }

        @Test
        void deveRetornar404QuandoTutorNaoEncontrado() throws Exception {
            when(agendarConsultaService.agendarNovaConsulta(REQUEST))
                    .thenThrow(new RecursoNaoEncontradoException("Tutor não encontrado"));

            mockMvc.perform(post("/agendarConsulta").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensagem").value("Tutor não encontrado"));
        }
    }

    @Nested
    class BuscarConsultas {

        @Test
        void deveRetornar200ComTodasAsConsultas() throws Exception {
            when(agendarConsultaService.buscarTodasConsultas(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                    response(10, StatusConsulta.AGENDADA),
                    response(11, StatusConsulta.CANCELADA))));

            mockMvc.perform(get("/agendarConsulta").with(token(1, "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].id").value(10))
                    .andExpect(jsonPath("$.content[1].status").value("CANCELADA"))
                    .andExpect(jsonPath("$.page.totalElements").value(2));
        }

        @Test
        void devePaginarCom10ItensOrdenadosPorIdPorPadrao() throws Exception {
            when(agendarConsultaService.buscarTodasConsultas(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/agendarConsulta").with(token(1, "ADMIN")))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(agendarConsultaService).buscarTodasConsultas(captor.capture());
            assertThat(captor.getValue().getPageNumber()).isZero();
            assertThat(captor.getValue().getPageSize()).isEqualTo(10);
            assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("id"));
        }

        @Test
        void deveRespeitarPaginaPedidaELimitarTamanhoEm50() throws Exception {
            when(agendarConsultaService.buscarTodasConsultas(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/agendarConsulta").param("page", "3").param("size", "1000").with(token(1, "ADMIN")))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(agendarConsultaService).buscarTodasConsultas(captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
            assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        }

        @Test
        void deveRetornar400QuandoCampoDeOrdenacaoNaoExiste() throws Exception {
            when(agendarConsultaService.buscarTodasConsultas(any(Pageable.class))).thenThrow(new PropertyReferenceException(
                    "naoExiste", TypeInformation.of(AgendarConsultaEntity.class), List.of()));

            mockMvc.perform(get("/agendarConsulta").param("sort", "naoExiste").with(token(1, "ADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value("Campo de ordenação inválido: naoExiste"));
        }

        @Test
        void deveRetornar200ComListaVazia() throws Exception {
            when(agendarConsultaService.buscarTodasConsultas(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/agendarConsulta").with(token(1, "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        void deveRetornar200ComConsultaPorId() throws Exception {
            when(agendarConsultaService.buscarConsulta(10)).thenReturn(response(10, StatusConsulta.AGENDADA));

            mockMvc.perform(get("/agendarConsulta/10").with(token(1, "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.status").value("AGENDADA"));
        }

        @Test
        void deveRetornar404QuandoConsultaNaoExiste() throws Exception {
            when(agendarConsultaService.buscarConsulta(99))
                    .thenThrow(new RecursoNaoEncontradoException("Consulta não encontrada"));

            mockMvc.perform(get("/agendarConsulta/99").with(token(1, "ADMIN")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.mensagem").value("Consulta não encontrada"))
                    .andExpect(jsonPath("$.path").value("/agendarConsulta/99"));
        }

        @Test
        void deveRetornar500QuandoErroInesperado() throws Exception {
            when(agendarConsultaService.buscarTodasConsultas(any(Pageable.class))).thenThrow(new IllegalStateException("falha"));

            mockMvc.perform(get("/agendarConsulta").with(token(1, "ADMIN")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.mensagem").value("Erro interno no servidor"));
        }
    }

    @Nested
    @WithMockUser(roles = "ADMIN")
    class CancelarConsulta {

        @Test
        void deveRetornar200ComConsultaCancelada() throws Exception {
            when(agendarConsultaService.cancelarConsulta(10)).thenReturn(response(10, StatusConsulta.CANCELADA));

            mockMvc.perform(patch("/agendarConsulta/10/cancelar"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.status").value("CANCELADA"));
        }

        @Test
        void deveRetornar400QuandoConsultaNaoEstaAgendada() throws Exception {
            when(agendarConsultaService.cancelarConsulta(10))
                    .thenThrow(new RegraNegocioException("Só é possível cancelar consultas agendadas"));

            mockMvc.perform(patch("/agendarConsulta/10/cancelar"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value("Só é possível cancelar consultas agendadas"));
        }

        @Test
        void deveRetornar404QuandoConsultaNaoExiste() throws Exception {
            when(agendarConsultaService.cancelarConsulta(99))
                    .thenThrow(new RecursoNaoEncontradoException("Consulta não encontrada"));

            mockMvc.perform(patch("/agendarConsulta/99/cancelar"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @WithMockUser(roles = "ADMIN")
    class AtualizarConsulta {

        @Test
        void deveRetornar200ComConsultaRemarcada() throws Exception {
            when(agendarConsultaService.atualizarConsulta(10, REQUEST)).thenReturn(response(10, StatusConsulta.AGENDADA));

            mockMvc.perform(put("/agendarConsulta/10").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.diaHora").value("2099-01-10T10:00:00"));

            verify(agendarConsultaService).atualizarConsulta(eq(10), any(AgendarConsultaDTO.class));
        }

        @Test
        void deveRetornar400QuandoBodyInvalido() throws Exception {
            mockMvc.perform(put("/agendarConsulta/10").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detalhes", hasSize(4)));

            verifyNoInteractions(agendarConsultaService);
        }

        @Test
        void deveRetornar409QuandoNovoHorarioOcupado() throws Exception {
            when(agendarConsultaService.atualizarConsulta(10, REQUEST))
                    .thenThrow(new ConflitoException("Veterinário já possui consulta neste horário"));

            mockMvc.perform(put("/agendarConsulta/10").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                    .andExpect(status().isConflict());
        }

        @Test
        void deveRetornar404QuandoConsultaNaoExiste() throws Exception {
            when(agendarConsultaService.atualizarConsulta(99, REQUEST))
                    .thenThrow(new RecursoNaoEncontradoException("Consulta não encontrada"));

            mockMvc.perform(put("/agendarConsulta/99").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensagem").value("Consulta não encontrada"));
        }
    }

    @Nested
    class Seguranca {

        @Test
        void deveRetornar401SemToken() throws Exception {
            mockMvc.perform(get("/agendarConsulta"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));

            verifyNoInteractions(agendarConsultaService);
        }

        @Test
        @WithMockUser(roles = "TUTOR")
        void deveRetornar403QuandoTutorTentaAgendar() throws Exception {
            mockMvc.perform(post("/agendarConsulta").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));

            verifyNoInteractions(agendarConsultaService);
        }

        @Test
        void tutorListaSoAsProprias() throws Exception {
            when(agendarConsultaService.buscarConsultasDoTutor(eq(1), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(response(10, StatusConsulta.AGENDADA))));

            mockMvc.perform(get("/agendarConsulta").with(token(1, "TUTOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].tutorId").value(1));

            verify(agendarConsultaService, never()).buscarTodasConsultas(any());
        }

        @Test
        void tutorPodeVerASuaConsulta() throws Exception {
            when(agendarConsultaService.buscarConsulta(10)).thenReturn(response(10, StatusConsulta.AGENDADA));

            mockMvc.perform(get("/agendarConsulta/10").with(token(1, "TUTOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));
        }

        @Test
        void deveRetornar403QuandoTutorBuscaConsultaDeOutroTutor() throws Exception {
            // a consulta 10 e do tutor 1; quem pede e o tutor 2
            when(agendarConsultaService.buscarConsulta(10)).thenReturn(response(10, StatusConsulta.AGENDADA));

            mockMvc.perform(get("/agendarConsulta/10").with(token(2, "TUTOR")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
    }
}
