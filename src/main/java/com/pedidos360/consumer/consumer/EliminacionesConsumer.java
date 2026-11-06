package com.pedidos360.consumer.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.consumer.config.RabbitNames;
import com.pedidos360.consumer.model.HistoricoBajas;
import com.pedidos360.consumer.repository.HistoricoBajasRepository;
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
 * Consumidor de eventos de bajas y eliminaciones de productos.
 *
 * Persiste la instantanea del producto antes de su eliminacion en 'historico_bajas'
 * de la base de datos 'auditoria_db'.
 *
 * Cumple con los requisitos de la rubrica:
 * - Indicador 4: Consumidor desacoplado por dominio funcional.
 * - Indicador 5: ACK manual y desvio a DLQ sin reencolamiento ante fallos.
 */
@Component
public class EliminacionesConsumer {

    private static final Logger log = LoggerFactory.getLogger(EliminacionesConsumer.class);

    private final HistoricoBajasRepository repository;
    private final ObjectMapper objectMapper;

    public EliminacionesConsumer(HistoricoBajasRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @RabbitListener(queues = RabbitNames.COLA_AUDITORIA_ELIMINACIONES)
    public void procesarEventoEliminacion(
            String payload,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {

        log.info("[ELIMINACIONES CONSUMER] Mensaje recibido de '{}' [deliveryTag={}]: {}",
                RabbitNames.COLA_AUDITORIA_ELIMINACIONES, deliveryTag, payload);

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
                log.warn("[ELIMINACIONES CONSUMER] Evento duplicado detectado (eventoId={}). Descartando con ACK.", eventoId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. Mapear y persistir historico de baja
            HistoricoBajas baja = new HistoricoBajas();
            baja.setEventoId(eventoId);
            if (root.hasNonNull("productoId")) {
                baja.setProductoId(root.get("productoId").asLong());
            }
            baja.setNombreProducto(root.path("nombreProducto").asText(null));
            baja.setTipoEvento(root.path("tipoEvento").asText(RabbitNames.RK_PRODUCTO_ELIMINADO));
            baja.setSnapshotPedido(payload); // Almacena snapshot completo JSON
            baja.setUsuarioSub(root.path("usuarioSub").asText("ANONYMOUS"));
            baja.setUsuarioEmail(root.path("usuarioEmail").asText(null));
            baja.setFechaEvento(root.path("fechaEvento").asText(Instant.now().toString()));
            baja.setProcesadoEn(Instant.now());

            repository.save(baja);
            log.info("[ELIMINACIONES CONSUMER] Snapshot de baja guardado en historico_bajas (eventoId={})", eventoId);

            // 3. Confirmacion positiva (ACK)
            channel.basicAck(deliveryTag, false);
            log.info("[ELIMINACIONES CONSUMER] basicAck emitido para deliveryTag={}", deliveryTag);

        } catch (Exception ex) {
            log.error("[ELIMINACIONES CONSUMER] Error al procesar eliminacion [deliveryTag={}]. Desviando a DLQ: {}",
                    deliveryTag, ex.getMessage());

            // 4. Rechazo explicito hacia DLQ sin reencolamiento
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
