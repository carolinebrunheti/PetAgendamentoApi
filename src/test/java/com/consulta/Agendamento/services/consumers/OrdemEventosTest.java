package com.consulta.Agendamento.services.consumers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class OrdemEventosTest {

    private static final LocalDateTime GRAVADO = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Test
    void eventoMaisAntigoJaFoiSuperado() {
        assertThat(OrdemEventos.jaAplicado(GRAVADO, GRAVADO.minusHours(1))).isTrue();
    }

    @Test
    void eventoRepetidoJaFoiAplicado() {
        assertThat(OrdemEventos.jaAplicado(GRAVADO, GRAVADO)).isTrue();
    }

    @Test
    void eventoMaisNovoDeveSerAplicado() {
        assertThat(OrdemEventos.jaAplicado(GRAVADO, GRAVADO.plusSeconds(1))).isFalse();
    }

    @Test
    void registroAntigoSemDataAceitaQualquerEvento() {
        // linhas gravadas antes da coluna atualizado_em existir
        assertThat(OrdemEventos.jaAplicado(null, GRAVADO)).isFalse();
    }

    @Test
    void eventoSemDataEAplicado() {
        assertThat(OrdemEventos.jaAplicado(GRAVADO, null)).isFalse();
    }
}
