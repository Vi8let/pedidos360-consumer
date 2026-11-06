package com.pedidos360.consumer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.consumer.model.HistoricoBajas;
import com.pedidos360.consumer.repository.HistoricoBajasRepository;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EliminacionesConsumerTest {

    @Mock
    private HistoricoBajasRepository repository;

    @Mock
    private Channel channel;

    private EliminacionesConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new EliminacionesConsumer(repository, objectMapper);
    }

    @Test
    @DisplayName("Debe procesar evento de eliminacion, guardar snapshot y emitir basicAck")
    void procesarEventoEliminacionExitoso() throws IOException {
        UUID eventoId = UUID.randomUUID();
        String json = """
            {
                "eventoId": "%s",
                "tipoEvento": "bajas.producto.eliminado",
                "productoId": 42,
                "nombreProducto": "Monitor Gamer",
                "usuarioSub": "admin-sub-001"
            }
            """.formatted(eventoId);

        when(repository.existsByEventoId(eventoId)).thenReturn(false);

        consumer.procesarEventoEliminacion(json, channel, 201L);

        verify(repository).save(any(HistoricoBajas.class));
        verify(channel).basicAck(201L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Debe descartar baja duplicada sin persistir y emitir basicAck (Idempotencia)")
    void procesarEventoEliminacionDuplicadoIdempotente() throws IOException {
        UUID eventoId = UUID.randomUUID();
        String json = """
            {
                "eventoId": "%s",
                "tipoEvento": "bajas.producto.eliminado",
                "productoId": 42
            }
            """.formatted(eventoId);

        when(repository.existsByEventoId(eventoId)).thenReturn(true);

        consumer.procesarEventoEliminacion(json, channel, 202L);

        verify(repository, never()).save(any(HistoricoBajas.class));
        verify(channel).basicAck(202L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Debe emitir basicNack(requeue=false) ante payload corrupto (desvio a DLQ)")
    void procesarEventoEliminacionPayloadInvalidoEnviaDlq() throws IOException {
        consumer.procesarEventoEliminacion("{invalido: json", channel, 203L);

        verify(repository, never()).save(any());
        verify(channel).basicNack(203L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}

