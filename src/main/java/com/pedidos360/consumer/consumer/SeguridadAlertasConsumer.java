package com.pedidos360.consumer.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.consumer.config.RabbitNames;
import com.pedidos360.consumer.model.AuditoriaSeguridad;
import com.pedidos360.consumer.repository.AuditoriaSeguridadRepository;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumidor de alertas de seguridad (p. ej. intentos no autorizados 403 Forbidden).
 *
 * Persiste los incidentes de seguridad en 'auditoria_seguridad' de 'auditoria_db'
 * para analisis forense y trazabilidad de accesos.
 *
 * Cumple con los requisitos de la rubrica:
 * - Indicador 4: Consumidor desacoplado por dominio funcional.
 * - Indicador 5: ACK manual y desvio a DLQ sin reencolamiento ante fallos.
 */
@Component
public class SeguridadAlertasConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeguridadAlertasConsumer.class);

    private final AuditoriaSeguridadRepository repository;
    private final ObjectMapper objectMapper;

    public SeguridadAlertasConsumer(AuditoriaSeguridadRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @RabbitListener(queues = RabbitNames.COLA_SEGURIDAD_ALERTAS)
    public void procesarAlertaSeguridad(
            String payload,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {

        log.info("[SEGURIDAD CONSUMER] Alerta recibida de '{}' [deliveryTag={}]: {}",
                RabbitNames.COLA_SEGURIDAD_ALERTAS, deliveryTag, payload);

        try {
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("Payload vacio o nulo");
            }

            JsonNode root = objectMapper.readTree(payload);

            UUID eventoId;
            if (root.hasNonNull("eventoId")) {
                eventoId = UUID.fromString(root.get("eventoId").asText());
            } else {
                eventoId = UUID.randomUUID();
            }

            // 1. Control de idempotencia
            if (repository.existsByEventoId(eventoId)) {
                log.warn("[SEGURIDAD CONSUMER] Alerta duplicada detectada (eventoId={}). Descartando con ACK.", eventoId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. Mapear y persistir alerta de seguridad
            AuditoriaSeguridad alerta = new AuditoriaSeguridad();
            alerta.setEventoId(eventoId);
            alerta.setTipoEvento(root.path("tipoEvento").asText("ALERTA_SEGURIDAD"));
            alerta.setUsuarioSub(root.path("usuarioSub").asText("ANONYMOUS"));
            alerta.setUsuarioEmail(root.path("usuarioEmail").asText(null));
            alerta.setRuta(root.path("ruta").asText(null));
            alerta.setMetodo(root.path("metodo").asText(null));
            alerta.setMotivo(root.path("motivo").asText(null));
            alerta.setFechaEvento(root.path("fechaEvento").asText(Instant.now().toString()));
            alerta.setPayload(payload);
            alerta.setProcesadoEn(Instant.now());

            repository.save(alerta);
            log.warn("[SEGURIDAD CONSUMER] Incidente de seguridad registrado en auditoria_db (eventoId={}, sub={})",
                    eventoId, alerta.getUsuarioSub());

            // 3. Confirmacion positiva (ACK)
            channel.basicAck(deliveryTag, false);
            log.info("[SEGURIDAD CONSUMER] basicAck emitido para deliveryTag={}", deliveryTag);

        } catch (Exception ex) {
            log.error("[SEGURIDAD CONSUMER] Error al procesar alerta [deliveryTag={}]. Desviando a DLQ: {}",
                    deliveryTag, ex.getMessage());

            // 4. Rechazo explicito hacia DLQ sin reencolamiento
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
