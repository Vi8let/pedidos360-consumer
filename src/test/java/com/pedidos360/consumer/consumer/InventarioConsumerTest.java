package com.pedidos360.consumer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.consumer.model.AuditoriaInventario;
import com.pedidos360.consumer.repository.AuditoriaInventarioRepository;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventarioConsumerTest {

    @Mock
    private AuditoriaInventarioRepository repository;

    @Mock
    private Channel channel;

    private InventarioConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new InventarioConsumer(repository, objectMapper);
    }

    @Test
    @DisplayName("Debe procesar evento de inventario exitosamente y emitir basicAck")
    void procesarEventoInventarioExitoso() throws IOException {
        UUID eventoId = UUID.randomUUID();
        String json = """
            {
                "eventoId": "%s",
                "tipoEvento": "inventario.producto.creado",
                "accion": "CREACION",
                "productoId": 10,
                "nombreProducto": "Notebook Dell",
                "cantidad": 5,
                "stock": 20,
                "estado": "ACTIVO",
                "usuarioSub": "user-sub-123"
            }
            """.formatted(eventoId);

        when(repository.existsByEventoId(eventoId)).thenReturn(false);

        consumer.procesarEventoInventario(json, channel, 101L);

        verify(repository).save(any(AuditoriaInventario.class));
        verify(channel).basicAck(101L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Debe descartar evento duplicado sin persistir y emitir basicAck (Idempotencia)")
    void procesarEventoInventarioDuplicadoIdempotente() throws IOException {
        UUID eventoId = UUID.randomUUID();
        String json = """
            {
                "eventoId": "%s",
                "tipoEvento": "inventario.producto.creado",
                "productoId": 10
            }
            """.formatted(eventoId);

        when(repository.existsByEventoId(eventoId)).thenReturn(true);

        consumer.procesarEventoInventario(json, channel, 102L);

        verify(repository, never()).save(any(AuditoriaInventario.class));
        verify(channel).basicAck(102L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Debe emitir basicNack(requeue=false) ante payload corrupto o nulo (desvío a DLQ)")
    void procesarEventoInventarioPayloadInvalidoEnviaDlq() throws IOException {
        consumer.procesarEventoInventario("JSON-CORRUPTO-NO-PARSEABLE", channel, 103L);

        verify(repository, never()).save(any());
        verify(channel).basicNack(103L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}

