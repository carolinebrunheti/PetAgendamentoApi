package com.consulta.Agendamento.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping
    public ResponseEntity<List<AgendarConsultaResponse>> buscarTodasConsultas(){
        return ResponseEntity.ok(agendarConsultaService.buscarTodasConsultas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgendarConsultaResponse> buscarConsulta(@PathVariable Integer id){
        return ResponseEntity.ok(agendarConsultaService.buscarConsulta(id));
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
