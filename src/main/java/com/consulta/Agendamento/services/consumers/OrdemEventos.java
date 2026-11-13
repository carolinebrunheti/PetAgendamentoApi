package com.consulta.Agendamento.services.consumers;

import java.time.LocalDateTime;

/**
 * O Kafka entrega "pelo menos uma vez": o mesmo evento pode chegar de novo, inclusive depois
 * de um mais novo (ex.: o consumidor caiu antes de confirmar o offset). Um evento so e aplicado
 * se for mais novo do que o ultimo ja gravado.
 */
final class OrdemEventos {

    private OrdemEventos() {
    }

    // true = o registro ja tem um evento igual ou mais novo: ignorar este
    static boolean jaAplicado(LocalDateTime gravadoEm, LocalDateTime ocorridoEm) {
        return gravadoEm != null && ocorridoEm != null && !ocorridoEm.isAfter(gravadoEm);
    }
}
