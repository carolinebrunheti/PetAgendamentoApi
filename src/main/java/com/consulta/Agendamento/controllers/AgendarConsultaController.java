package com.consulta.Agendamento.controllers;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.consulta.Agendamento.config.security.UsuarioLogado;
import com.consulta.Agendamento.dtos.AgendarConsultaDTO;
import com.consulta.Agendamento.dtos.AgendarConsultaResponse;
import com.consulta.Agendamento.services.AgendarConsultaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/agendarConsulta")

public class AgendarConsultaController {

    private final AgendarConsultaService agendarConsultaService;

    public AgendarConsultaController(AgendarConsultaService agendarConsultaService) {
        this.agendarConsultaService = agendarConsultaService;
    }

    @PostMapping
    public ResponseEntity<AgendarConsultaResponse> agendarNovaConsulta(@RequestBody @Valid AgendarConsultaDTO request){
        return ResponseEntity.status(HttpStatus.CREATED).body(agendarConsultaService.agendarNovaConsulta(request));
    }

    // o tutor so enxerga as proprias consultas; a equipe da clinica ve todas
    @GetMapping
    public ResponseEntity<PagedModel<AgendarConsultaResponse>> buscarTodasConsultas(@AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 10, sort = "id") Pageable pageable){
        UsuarioLogado usuario = UsuarioLogado.de(jwt);
        if (usuario.isTutor()) {
            return ResponseEntity.ok(new PagedModel<>(agendarConsultaService.buscarConsultasDoTutor(usuario.id(), pageable)));
        }
        return ResponseEntity.ok(new PagedModel<>(agendarConsultaService.buscarTodasConsultas(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgendarConsultaResponse> buscarConsulta(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt){
        UsuarioLogado usuario = UsuarioLogado.de(jwt);
        AgendarConsultaResponse consulta = agendarConsultaService.buscarConsulta(id);
        if (usuario.isTutor() && !consulta.tutorId().equals(usuario.id())) {
            throw new AccessDeniedException("Consulta de outro tutor");
        }
        return ResponseEntity.ok(consulta);
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<AgendarConsultaResponse> cancelarConsulta(@PathVariable Integer id) {
        return ResponseEntity.ok(agendarConsultaService.cancelarConsulta(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgendarConsultaResponse> atualConsulta(@RequestBody @Valid AgendarConsultaDTO request, @PathVariable Integer id) {
        return ResponseEntity.ok(agendarConsultaService.atualizarConsulta(id, request));
    }
}
