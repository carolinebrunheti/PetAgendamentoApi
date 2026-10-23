package com.consulta.Agendamento.services.consumers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.consulta.Agendamento.entities.TutorEntity;
import com.consulta.Agendamento.entities.VeterinarioEntity;
import com.consulta.Agendamento.repositories.TutorRepository;
import com.consulta.Agendamento.repositories.VeterinarioRepository;
import com.consulta.Agendamento.services.enums.Role;
import com.consulta.Agendamento.services.enums.TipoEvento;
import com.consulta.Agendamento.services.enums.UsuarioEvent;

@ExtendWith(MockitoExtension.class)
class UsuarioEventConsumerTest {

    @Mock
    private TutorRepository tutorRepository;
    @Mock
    private VeterinarioRepository veterinarioRepository;

    @InjectMocks
    private UsuarioEventConsumer consumer;

    @Captor
    private ArgumentCaptor<TutorEntity> tutorCaptor;
    @Captor
    private ArgumentCaptor<VeterinarioEntity> veterinarioCaptor;

    private UsuarioEvent evento(TipoEvento tipo, Role role) {
        return new UsuarioEvent(tipo, 1, "Ana", "ana@email.com", role, LocalDateTime.now());
    }

    @Test
    void deveSalvarTutorAtivoQuandoEventoCriado() {
        consumer.consumir(evento(TipoEvento.CRIADO, Role.TUTOR));

        verify(tutorRepository).save(tutorCaptor.capture());
        TutorEntity salvo = tutorCaptor.getValue();
        assertThat(salvo.getId()).isEqualTo(1);
        assertThat(salvo.getNome()).isEqualTo("Ana");
        assertThat(salvo.getEmail()).isEqualTo("ana@email.com");
        assertThat(salvo.getAtivo()).isTrue();
        verifyNoInteractions(veterinarioRepository);
    }

    @Test
    void deveInativarTutorQuandoEventoRemovido() {
        TutorEntity existente = new TutorEntity(1, "Ana", "ana@email.com", true);
        when(tutorRepository.findById(1)).thenReturn(Optional.of(existente));

        consumer.consumir(evento(TipoEvento.REMOVIDO, Role.TUTOR));

        assertThat(existente.getAtivo()).isFalse();
        verify(tutorRepository).save(existente);
    }

    @Test
    void deveIgnorarDelecaoDeTutorInexistente() {
        when(tutorRepository.findById(1)).thenReturn(Optional.empty());

        consumer.consumir(evento(TipoEvento.REMOVIDO, Role.TUTOR));

        verify(tutorRepository, never()).save(any());
    }

    @Test
    void deveSalvarVeterinarioAtivoQuandoEventoAtualizado() {
        consumer.consumir(evento(TipoEvento.ATUALIZADO, Role.VETERINARIO));

        verify(veterinarioRepository).save(veterinarioCaptor.capture());
        VeterinarioEntity salvo = veterinarioCaptor.getValue();
        assertThat(salvo.getId()).isEqualTo(1);
        assertThat(salvo.getNome()).isEqualTo("Ana");
        assertThat(salvo.getEmail()).isEqualTo("ana@email.com");
        assertThat(salvo.getAtivo()).isTrue();
        verifyNoInteractions(tutorRepository);
    }

    @Test
    void deveInativarVeterinarioQuandoEventoRemovido() {
        VeterinarioEntity existente = new VeterinarioEntity(1, "Ana", "ana@email.com", true);
        when(veterinarioRepository.findById(1)).thenReturn(Optional.of(existente));

        consumer.consumir(evento(TipoEvento.REMOVIDO, Role.VETERINARIO));

        assertThat(existente.getAtivo()).isFalse();
        verify(veterinarioRepository).save(existente);
    }

    @Test
    void deveIgnorarDelecaoDeVeterinarioInexistente() {
        when(veterinarioRepository.findById(1)).thenReturn(Optional.empty());

        consumer.consumir(evento(TipoEvento.REMOVIDO, Role.VETERINARIO));

        verify(veterinarioRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ATENDENTE", "ADMIN"})
    void deveIgnorarEventosDeOutrosPapeis(Role role) {
        consumer.consumir(evento(TipoEvento.CRIADO, role));

        verifyNoInteractions(tutorRepository, veterinarioRepository);
    }
}
