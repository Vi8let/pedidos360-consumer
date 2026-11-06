package com.pedidos360.consumer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.consumer.model.AuditoriaSeguridad;
import com.pedidos360.consumer.repository.AuditoriaSeguridadRepository;
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
class SeguridadAlertasConsumerTest {

    @Mock
    private AuditoriaSeguridadRepository repository;

    @Mock
    private Channel channel;

    private SeguridadAlertasConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new SeguridadAlertasConsumer(repository, objectMapper);
    }

    @Test
    @DisplayName("Debe procesar alerta de seguridad y emitir basicAck")
    void procesarAlertaSeguridadExitoso() throws IOException {
        UUID eventoId = UUID.randomUUID();
        String json = """
            {
                "eventoId": "%s",
                "tipoEvento": "ALERTA_ACCESO_DENEGADO",
                "usuarioSub": "attacker-sub-403",
                "ruta": "/api/pedidos",
                "metodo": "POST",
                "motivo": "Usuario no posee rol ROLE_Admin"
            }
            """.formatted(eventoId);

        when(repository.existsByEventoId(eventoId)).thenReturn(false);

        consumer.procesarAlertaSeguridad(json, channel, 301L);

        verify(repository).save(any(AuditoriaSeguridad.class));
        verify(channel).basicAck(301L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Debe descartar alerta duplicada sin persistir y emitir basicAck (Idempotencia)")
    void procesarAlertaSeguridadDuplicadaIdempotente() throws IOException {
        UUID eventoId = UUID.randomUUID();
        String json = """
            {
                "eventoId": "%s",
                "tipoEvento": "ALERTA_ACCESO_DENEGADO",
                "usuarioSub": "user-sub"
            }
            """.formatted(eventoId);

        when(repository.existsByEventoId(eventoId)).thenReturn(true);

        consumer.procesarAlertaSeguridad(json, channel, 302L);

        verify(repository, never()).save(any(AuditoriaSeguridad.class));
        verify(channel).basicAck(302L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Debe emitir basicNack(requeue=false) ante payload corrupto (desvio a DLQ)")
    void procesarAlertaSeguridadPayloadInvalidoEnviaDlq() throws IOException {
        consumer.procesarAlertaSeguridad("corrupt-payload", channel, 303L);

        verify(repository, never()).save(any());
        verify(channel).basicNack(303L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}

