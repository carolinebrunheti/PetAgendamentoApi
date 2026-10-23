package com.consulta.Agendamento.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.consulta.Agendamento.dtos.AgendarConsultaDTO;
import com.consulta.Agendamento.dtos.AgendarConsultaResponse;
import com.consulta.Agendamento.entities.AgendarConsultaEntity;
import com.consulta.Agendamento.entities.PetEntity;
import com.consulta.Agendamento.entities.TutorEntity;
import com.consulta.Agendamento.entities.VeterinarioEntity;
import com.consulta.Agendamento.exceptions.ConflitoException;
import com.consulta.Agendamento.exceptions.RecursoNaoEncontradoException;
import com.consulta.Agendamento.exceptions.RegraNegocioException;
import com.consulta.Agendamento.repositories.AgendarConsultaRepository;
import com.consulta.Agendamento.repositories.PetRepository;
import com.consulta.Agendamento.repositories.TutorRepository;
import com.consulta.Agendamento.repositories.VeterinarioRepository;
import com.consulta.Agendamento.services.enums.AgendarConsultaEvent;
import com.consulta.Agendamento.services.enums.Especie;
import com.consulta.Agendamento.services.enums.StatusConsulta;
import com.consulta.Agendamento.services.enums.TipoEvento;
import com.consulta.Agendamento.services.producers.AgendarConsultaProducer;

@ExtendWith(MockitoExtension.class)
class AgendarConsultaServiceTest {

    private static final LocalDateTime DIA_HORA = LocalDateTime.of(2099, 1, 10, 10, 0);

    @Mock
    private AgendarConsultaRepository agendarConsultaRepository;
    @Mock
    private TutorRepository tutorRepository;
    @Mock
    private PetRepository petRepository;
    @Mock
    private VeterinarioRepository veterinarioRepository;
    @Mock
    private AgendarConsultaProducer agendarConsultaProducer;

    @InjectMocks
    private AgendarConsultaService service;

    @Captor
    private ArgumentCaptor<AgendarConsultaEntity> consultaCaptor;
    @Captor
    private ArgumentCaptor<AgendarConsultaEvent> eventoCaptor;

    private TutorEntity tutor;
    private PetEntity pet;
    private VeterinarioEntity veterinario;
    private AgendarConsultaDTO request;

    @BeforeEach
    void setUp() {
        tutor = new TutorEntity(1, "Ana", "ana@email.com", true);
        pet = new PetEntity(2, "Rex", Especie.CACHORRO, 1, true);
        veterinario = new VeterinarioEntity(3, "Dr. João", "joao@email.com", true);
        request = new AgendarConsultaDTO(1, 2, 3, DIA_HORA);
    }

    private AgendarConsultaEntity consulta(Integer id, StatusConsulta status) {
        return new AgendarConsultaEntity(id, 1, 2, 3, DIA_HORA, status);
    }

    private void mockarDadosValidos() {
        when(tutorRepository.findById(1)).thenReturn(Optional.of(tutor));
        when(petRepository.findById(2)).thenReturn(Optional.of(pet));
        when(veterinarioRepository.findById(3)).thenReturn(Optional.of(veterinario));
    }

    @Nested
    class BuscarTodasConsultas {

        @Test
        void deveRetornarTodasAsConsultasConvertidas() {
            when(agendarConsultaRepository.findAll()).thenReturn(List.of(
                    consulta(10, StatusConsulta.AGENDADA),
                    consulta(11, StatusConsulta.CANCELADA)));

            List<AgendarConsultaResponse> resultado = service.buscarTodasConsultas();

            assertThat(resultado).containsExactly(
                    new AgendarConsultaResponse(10, 1, 2, 3, DIA_HORA, StatusConsulta.AGENDADA),
                    new AgendarConsultaResponse(11, 1, 2, 3, DIA_HORA, StatusConsulta.CANCELADA));
        }

        @Test
        void deveRetornarListaVaziaQuandoNaoHaConsultas() {
            when(agendarConsultaRepository.findAll()).thenReturn(List.of());

            assertThat(service.buscarTodasConsultas()).isEmpty();
        }
    }

    @Nested
    class BuscarConsulta {

        @Test
        void deveRetornarConsultaQuandoExiste() {
            when(agendarConsultaRepository.findById(10)).thenReturn(Optional.of(consulta(10, StatusConsulta.AGENDADA)));

            AgendarConsultaResponse resultado = service.buscarConsulta(10);

            assertThat(resultado).isEqualTo(
                    new AgendarConsultaResponse(10, 1, 2, 3, DIA_HORA, StatusConsulta.AGENDADA));
        }

        @Test
        void deveLancarExcecaoQuandoConsultaNaoExiste() {
            when(agendarConsultaRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.buscarConsulta(99))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Consulta não encontrada");
        }
    }

    @Nested
    class AgendarNovaConsulta {

        @Test
        void deveAgendarConsultaEPublicarEventoCriado() {
            mockarDadosValidos();
            when(agendarConsultaRepository.existsByVeterinarioIdAndDiaHoraAndStatus(3, DIA_HORA, StatusConsulta.AGENDADA))
                    .thenReturn(false);
            when(agendarConsultaRepository.save(any(AgendarConsultaEntity.class)))
                    .thenAnswer(inv -> {
                        AgendarConsultaEntity e = inv.getArgument(0);
                        e.setId(10);
                        return e;
                    });

            AgendarConsultaResponse resultado = service.agendarNovaConsulta(request);

            assertThat(resultado).isEqualTo(
                    new AgendarConsultaResponse(10, 1, 2, 3, DIA_HORA, StatusConsulta.AGENDADA));

            verify(agendarConsultaRepository).save(consultaCaptor.capture());
            AgendarConsultaEntity salva = consultaCaptor.getValue();
            assertThat(salva.getTutorId()).isEqualTo(1);
            assertThat(salva.getPetId()).isEqualTo(2);
            assertThat(salva.getVeterinarioId()).isEqualTo(3);
            assertThat(salva.getDiaHora()).isEqualTo(DIA_HORA);
            assertThat(salva.getStatus()).isEqualTo(StatusConsulta.AGENDADA);

            verify(agendarConsultaProducer).publicar(eventoCaptor.capture());
            AgendarConsultaEvent evento = eventoCaptor.getValue();
            assertThat(evento.tipoEvento()).isEqualTo(TipoEvento.CRIADO);
            assertThat(evento.consultaId()).isEqualTo(10);
            assertThat(evento.tutorNome()).isEqualTo("Ana");
            assertThat(evento.tutorEmail()).isEqualTo("ana@email.com");
            assertThat(evento.petNome()).isEqualTo("Rex");
            assertThat(evento.veterinarioNome()).isEqualTo("Dr. João");
            assertThat(evento.diaHora()).isEqualTo(DIA_HORA);
            assertThat(evento.ocorridoEm()).isNotNull();
        }

        @Test
        void deveLancarConflitoQuandoVeterinarioJaTemConsultaNoHorario() {
            mockarDadosValidos();
            when(agendarConsultaRepository.existsByVeterinarioIdAndDiaHoraAndStatus(3, DIA_HORA, StatusConsulta.AGENDADA))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.agendarNovaConsulta(request))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Veterinário já possui consulta neste horário");

            verify(agendarConsultaRepository, never()).save(any());
            verifyNoInteractions(agendarConsultaProducer);
        }

        @Test
        void deveLancarExcecaoQuandoTutorNaoExiste() {
            when(tutorRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.agendarNovaConsulta(request))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Tutor não encontrado");

            verifyNoInteractions(agendarConsultaRepository, agendarConsultaProducer);
        }

        @Test
        void deveLancarExcecaoQuandoTutorEstaInativo() {
            tutor.setAtivo(false);
            when(tutorRepository.findById(1)).thenReturn(Optional.of(tutor));

            assertThatThrownBy(() -> service.agendarNovaConsulta(request))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Tutor não encontrado");
        }

        @Test
        void deveLancarExcecaoQuandoPetNaoExiste() {
            when(tutorRepository.findById(1)).thenReturn(Optional.of(tutor));
            when(petRepository.findById(2)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.agendarNovaConsulta(request))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Pet não encontrado");
        }

        @Test
        void deveLancarExcecaoQuandoPetEstaInativo() {
            pet.setAtivo(false);
            when(tutorRepository.findById(1)).thenReturn(Optional.of(tutor));
            when(petRepository.findById(2)).thenReturn(Optional.of(pet));

            assertThatThrownBy(() -> service.agendarNovaConsulta(request))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Pet não encontrado");
        }

        @Test
        void deveLancarExcecaoQuandoPetNaoPertenceAoTutor() {
            pet.setTutorId(50);
            when(tutorRepository.findById(1)).thenReturn(Optional.of(tutor));
            when(petRepository.findById(2)).thenReturn(Optional.of(pet));

            assertThatThrownBy(() -> service.agendarNovaConsulta(request))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("O pet não pertence a este tutor");

            verifyNoInteractions(veterinarioRepository, agendarConsultaRepository, agendarConsultaProducer);
        }

        @Test
        void deveLancarExcecaoQuandoVeterinarioNaoExiste() {
            when(tutorRepository.findById(1)).thenReturn(Optional.of(tutor));
            when(petRepository.findById(2)).thenReturn(Optional.of(pet));
            when(veterinarioRepository.findById(3)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.agendarNovaConsulta(request))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Veterinário não encontrado");
        }

        @Test
        void deveLancarExcecaoQuandoVeterinarioEstaInativo() {
            veterinario.setAtivo(false);
            mockarDadosValidos();

            assertThatThrownBy(() -> service.agendarNovaConsulta(request))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Veterinário não encontrado");
        }
    }

    @Nested
    class AtualizarConsulta {

        private final LocalDateTime novoHorario = DIA_HORA.plusDays(1);
        private final AgendarConsultaDTO novoRequest = new AgendarConsultaDTO(1, 2, 3, novoHorario);

        @Test
        void deveRemarcarConsultaEPublicarEventoAtualizado() {
            AgendarConsultaEntity existente = consulta(10, StatusConsulta.AGENDADA);
            when(agendarConsultaRepository.findById(10)).thenReturn(Optional.of(existente));
            mockarDadosValidos();
            when(agendarConsultaRepository.existsByVeterinarioIdAndDiaHoraAndStatusAndIdNot(
                    3, novoHorario, StatusConsulta.AGENDADA, 10)).thenReturn(false);
            when(agendarConsultaRepository.save(existente)).thenReturn(existente);

            AgendarConsultaResponse resultado = service.atualizarConsulta(10, novoRequest);

            assertThat(resultado).isEqualTo(
                    new AgendarConsultaResponse(10, 1, 2, 3, novoHorario, StatusConsulta.AGENDADA));

            verify(agendarConsultaProducer).publicar(eventoCaptor.capture());
            assertThat(eventoCaptor.getValue().tipoEvento()).isEqualTo(TipoEvento.ATUALIZADO);
            assertThat(eventoCaptor.getValue().consultaId()).isEqualTo(10);
            assertThat(eventoCaptor.getValue().diaHora()).isEqualTo(novoHorario);
        }

        @Test
        void deveLancarExcecaoQuandoConsultaNaoExiste() {
            when(agendarConsultaRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.atualizarConsulta(99, novoRequest))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Consulta não encontrada");

            verifyNoInteractions(tutorRepository, agendarConsultaProducer);
        }

        @Test
        void deveLancarExcecaoQuandoConsultaNaoEstaAgendada() {
            when(agendarConsultaRepository.findById(10)).thenReturn(Optional.of(consulta(10, StatusConsulta.CANCELADA)));

            assertThatThrownBy(() -> service.atualizarConsulta(10, novoRequest))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Só é possível remarcar consultas agendadas");

            verify(agendarConsultaRepository, never()).save(any());
            verifyNoInteractions(agendarConsultaProducer);
        }

        @Test
        void deveLancarConflitoQuandoNovoHorarioOcupado() {
            AgendarConsultaEntity existente = consulta(10, StatusConsulta.AGENDADA);
            when(agendarConsultaRepository.findById(10)).thenReturn(Optional.of(existente));
            mockarDadosValidos();
            when(agendarConsultaRepository.existsByVeterinarioIdAndDiaHoraAndStatusAndIdNot(
                    3, novoHorario, StatusConsulta.AGENDADA, 10)).thenReturn(true);

            assertThatThrownBy(() -> service.atualizarConsulta(10, novoRequest))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Veterinário já possui consulta neste horário");

            assertThat(existente.getDiaHora()).isEqualTo(DIA_HORA);
            verify(agendarConsultaRepository, never()).save(any());
            verifyNoInteractions(agendarConsultaProducer);
        }
    }

    @Nested
    class CancelarConsulta {

        @Test
        void deveCancelarConsultaEPublicarEventoCancelada() {
            AgendarConsultaEntity existente = consulta(10, StatusConsulta.AGENDADA);
            when(agendarConsultaRepository.findById(10)).thenReturn(Optional.of(existente));
            when(agendarConsultaRepository.save(existente)).thenReturn(existente);
            mockarDadosValidos();

            AgendarConsultaResponse resultado = service.cancelarConsulta(10);

            assertThat(resultado.status()).isEqualTo(StatusConsulta.CANCELADA);
            assertThat(existente.getStatus()).isEqualTo(StatusConsulta.CANCELADA);

            verify(agendarConsultaProducer).publicar(eventoCaptor.capture());
            assertThat(eventoCaptor.getValue().tipoEvento()).isEqualTo(TipoEvento.CANCELADA);
            assertThat(eventoCaptor.getValue().consultaId()).isEqualTo(10);
            assertThat(eventoCaptor.getValue().tutorEmail()).isEqualTo("ana@email.com");
        }

        @Test
        void deveLancarExcecaoQuandoConsultaNaoExiste() {
            when(agendarConsultaRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelarConsulta(99))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Consulta não encontrada");
        }

        @Test
        void deveLancarExcecaoQuandoConsultaJaFoiCancelada() {
            when(agendarConsultaRepository.findById(10)).thenReturn(Optional.of(consulta(10, StatusConsulta.CANCELADA)));

            assertThatThrownBy(() -> service.cancelarConsulta(10))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Só é possível cancelar consultas agendadas");

            verify(agendarConsultaRepository, never()).save(any());
            verifyNoInteractions(agendarConsultaProducer);
        }

        @Test
        void deveLancarExcecaoQuandoConsultaJaFoiRealizada() {
            when(agendarConsultaRepository.findById(10)).thenReturn(Optional.of(consulta(10, StatusConsulta.REALIZADA)));

            assertThatThrownBy(() -> service.cancelarConsulta(10))
                    .isInstanceOf(RegraNegocioException.class);
        }
    }
}
