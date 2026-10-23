package com.consulta.Agendamento.services;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import com.consulta.Agendamento.services.enums.StatusConsulta;
import com.consulta.Agendamento.services.enums.TipoEvento;
import com.consulta.Agendamento.services.producers.AgendarConsultaProducer;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgendarConsultaService {

    private final AgendarConsultaRepository agendarConsultaRepository;
    private final TutorRepository tutorRepository;
    private final PetRepository petRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final AgendarConsultaProducer agendarConsultaProducer;

    // métodos públicos (os que o controller chama)

    public List<AgendarConsultaResponse> buscarTodasConsultas() {
        return agendarConsultaRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AgendarConsultaResponse> buscarConsultasDoTutor(Integer tutorId) {
        return agendarConsultaRepository.findByTutorId(tutorId).stream()
                .map(this::toResponse)
                .toList();
    }

    public AgendarConsultaResponse buscarConsulta(Integer id) {
        return agendarConsultaRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Consulta não encontrada"));
    }

    @Transactional
    public AgendarConsultaResponse agendarNovaConsulta(AgendarConsultaDTO request) {
        DadosConsulta dados = validarDados(request);

        boolean horarioOcupado = agendarConsultaRepository.existsByVeterinarioIdAndDiaHoraAndStatus(
                dados.veterinario().getId(), request.diaHora(), StatusConsulta.AGENDADA);
        if (horarioOcupado) {
            throw new ConflitoException("Veterinário já possui consulta neste horário");
        }

        AgendarConsultaEntity consulta = new AgendarConsultaEntity();
        consulta.setTutorId(dados.tutor().getId());
        consulta.setPetId(dados.pet().getId());
        consulta.setVeterinarioId(dados.veterinario().getId());
        consulta.setDiaHora(request.diaHora());
        consulta.setStatus(StatusConsulta.AGENDADA);

        AgendarConsultaEntity salva = agendarConsultaRepository.save(consulta);
        publicarEvento(TipoEvento.CRIADO, salva, dados);

        return toResponse(salva);
    }

    @Transactional
    public AgendarConsultaResponse atualizarConsulta(Integer id, AgendarConsultaDTO request) {
        AgendarConsultaEntity consulta = buscarAgendada(id, "Só é possível remarcar consultas agendadas");
        DadosConsulta dados = validarDados(request);

        boolean horarioOcupado = agendarConsultaRepository.existsByVeterinarioIdAndDiaHoraAndStatusAndIdNot(
                dados.veterinario().getId(), request.diaHora(), StatusConsulta.AGENDADA, id);
        if (horarioOcupado) {
            throw new ConflitoException("Veterinário já possui consulta neste horário");
        }

        consulta.setTutorId(dados.tutor().getId());
        consulta.setPetId(dados.pet().getId());
        consulta.setVeterinarioId(dados.veterinario().getId());
        consulta.setDiaHora(request.diaHora());

        AgendarConsultaEntity salva = agendarConsultaRepository.save(consulta);
        publicarEvento(TipoEvento.ATUALIZADO, salva, dados);

        return toResponse(salva);
    }

    @Transactional
    public AgendarConsultaResponse cancelarConsulta(Integer id) {
        AgendarConsultaEntity consulta = buscarAgendada(id, "Só é possível cancelar consultas agendadas");

        consulta.setStatus(StatusConsulta.CANCELADA);
        AgendarConsultaEntity salva = agendarConsultaRepository.save(consulta);

        DadosConsulta dados = new DadosConsulta(
                tutorRepository.findById(salva.getTutorId()).orElseThrow(),
                petRepository.findById(salva.getPetId()).orElseThrow(),
                veterinarioRepository.findById(salva.getVeterinarioId()).orElseThrow());
        publicarEvento(TipoEvento.CANCELADA, salva, dados);

        return toResponse(salva);
    }

    // métodos privados (ajudantes internos)

    private record DadosConsulta(TutorEntity tutor, PetEntity pet, VeterinarioEntity veterinario) {}

    private DadosConsulta validarDados(AgendarConsultaDTO request) {
        TutorEntity tutor = tutorRepository.findById(request.tutorId())
                .filter(TutorEntity::getAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tutor não encontrado"));

        PetEntity pet = petRepository.findById(request.petId())
                .filter(PetEntity::getAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pet não encontrado"));

        if (!pet.getTutorId().equals(tutor.getId())) {
            throw new RegraNegocioException("O pet não pertence a este tutor");
        }

        VeterinarioEntity veterinario = veterinarioRepository.findById(request.veterinarioId())
                .filter(VeterinarioEntity::getAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veterinário não encontrado"));

        return new DadosConsulta(tutor, pet, veterinario);
    }

    private AgendarConsultaEntity buscarAgendada(Integer id, String mensagemSeNaoAgendada) {
        AgendarConsultaEntity consulta = agendarConsultaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Consulta não encontrada"));

        if (consulta.getStatus() != StatusConsulta.AGENDADA) {
            throw new RegraNegocioException(mensagemSeNaoAgendada);
        }
        return consulta;
    }

    private void publicarEvento(TipoEvento tipo, AgendarConsultaEntity consulta, DadosConsulta dados) {
        agendarConsultaProducer.publicar(new AgendarConsultaEvent(
                tipo, consulta.getId(),
                dados.tutor().getNome(), dados.tutor().getEmail(),
                dados.pet().getNome(), dados.veterinario().getNome(),
                consulta.getDiaHora(), LocalDateTime.now()));
    }

    private AgendarConsultaResponse toResponse(AgendarConsultaEntity e) {
        return new AgendarConsultaResponse(e.getId(), e.getTutorId(), e.getPetId(),
                e.getVeterinarioId(), e.getDiaHora(), e.getStatus());
    }
}