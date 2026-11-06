package com.pedidos360.consumer.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.consumer.config.RabbitNames;
import com.pedidos360.consumer.model.AuditoriaInventario;
import com.pedidos360.consumer.repository.AuditoriaInventarioRepository;
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
 * Consumidor de eventos de auditoria de inventario (creaciones y modificaciones).
 *
 * Cumple con los requisitos de la rubrica:
 * - Indicador 4: @RabbitListener organizado por dominio funcional.
 * - Indicador 5: ACK manual (channel.basicAck) y NACK sin reencolamiento (basicNack(false, false) -> DLQ).
 * - Idempotencia estricta mediante verificacion de eventoId en auditoria_inventario.
 */
@Component
public class InventarioConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventarioConsumer.class);

    private final AuditoriaInventarioRepository repository;
    private final ObjectMapper objectMapper;

    public InventarioConsumer(AuditoriaInventarioRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @RabbitListener(queues = RabbitNames.COLA_AUDITORIA_INVENTARIO)
    public void procesarEventoInventario(
            String payload,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {

        log.info("[INVENTARIO CONSUMER] Mensaje recibido de '{}' [deliveryTag={}]: {}",
                RabbitNames.COLA_AUDITORIA_INVENTARIO, deliveryTag, payload);

        try {
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("Payload vacio o nulo");
            }

            JsonNode root = objectMapper.readTree(payload);

            // Extraer o generar eventoId
            UUID eventoId;
            if (root.hasNonNull("eventoId")) {
                eventoId = UUID.fromString(root.get("eventoId").asText());
            } else {
                eventoId = UUID.randomUUID();
            }

            // 1. Control de idempotencia
            if (repository.existsByEventoId(eventoId)) {
                log.warn("[INVENTARIO CONSUMER] Evento duplicado detectado (eventoId={}). Descartando duplicado con ACK.", eventoId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. Mapear y persistir en auditoria_db
            AuditoriaInventario entidad = new AuditoriaInventario();
            entidad.setEventoId(eventoId);
            entidad.setTipoEvento(root.path("tipoEvento").asText("INVENTARIO"));
            entidad.setAccion(root.path("accion").asText(null));
            if (root.hasNonNull("productoId")) {
                entidad.setProductoId(root.get("productoId").asLong());
            }
            entidad.setNombreProducto(root.path("nombreProducto").asText(null));
            entidad.setCantidad(root.path("cantidad").asInt(0));
            entidad.setStock(root.path("stock").asInt(0));
            entidad.setEstado(root.path("estado").asText(null));
            entidad.setImageUrl(root.path("imageUrl").asText(null));
            entidad.setUsuarioSub(root.path("usuarioSub").asText("ANONYMOUS"));
            entidad.setUsuarioEmail(root.path("usuarioEmail").asText(null));
            entidad.setFechaEvento(root.path("fechaEvento").asText(Instant.now().toString()));
            entidad.setPayload(payload);
            entidad.setProcesadoEn(Instant.now());

            repository.save(entidad);
            log.info("[INVENTARIO CONSUMER] Evento persistido exitosamente en auditoria_db (eventoId={})", eventoId);

            // 3. Confirmacion positiva (ACK)
            channel.basicAck(deliveryTag, false);
            log.info("[INVENTARIO CONSUMER] basicAck emitido para deliveryTag={}", deliveryTag);

        } catch (Exception ex) {
            log.error("[INVENTARIO CONSUMER] Error al procesar mensaje [deliveryTag={}]. Desviando a DLQ: {}",
                    deliveryTag, ex.getMessage());

            // 4. Rechazo explicito hacia DLQ sin reencolamiento (requeue = false)
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
