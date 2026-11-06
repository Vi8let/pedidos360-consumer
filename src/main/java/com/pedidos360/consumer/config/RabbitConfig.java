package com.pedidos360.consumer.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de RabbitMQ para el microservicio consumidor.
 * Declara las colas, exchanges y bindings espejados, y configura el listener con ACK manual.
 */
@Configuration
public class RabbitConfig {

    // =========================================================================
    // 1. EXCHANGES PRINCIPALES Y DLX
    // =========================================================================

    @Bean
    public TopicExchange pedidosAuditoriaTopicExchange() {
        return new TopicExchange(RabbitNames.TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange pedidosSeguridadDirectExchange() {
        return new DirectExchange(RabbitNames.DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange pedidosDlxExchange() {
        return new DirectExchange(RabbitNames.DLX_EXCHANGE, true, false);
    }

    // =========================================================================
    // 2. COLAS PRINCIPALES CON ARGUMENTOS DLX
    // =========================================================================

    @Bean
    public Queue colaAuditoriaInventario() {
        return QueueBuilder.durable(RabbitNames.QUEUE_INVENTARIO)
                .deadLetterExchange(RabbitNames.DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.RK_DLQ_INVENTARIO)
                .build();
    }

    @Bean
    public Queue colaAuditoriaEliminaciones() {
        return QueueBuilder.durable(RabbitNames.QUEUE_ELIMINACIONES)
                .deadLetterExchange(RabbitNames.DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.RK_DLQ_ELIMINACIONES)
                .build();
    }

    @Bean
    public Queue colaSeguridadAlertas() {
        return QueueBuilder.durable(RabbitNames.QUEUE_SEGURIDAD)
                .deadLetterExchange(RabbitNames.DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.RK_DLQ_SEGURIDAD)
                .build();
    }

    // =========================================================================
    // 3. COLAS DEAD LETTER (DLQ)
    // =========================================================================

    @Bean
    public Queue colaAuditoriaInventarioDlq() {
        return QueueBuilder.durable(RabbitNames.DLQ_INVENTARIO).build();
    }

    @Bean
    public Queue colaAuditoriaEliminacionesDlq() {
        return QueueBuilder.durable(RabbitNames.DLQ_ELIMINACIONES).build();
    }

    @Bean
    public Queue colaSeguridadAlertasDlq() {
        return QueueBuilder.durable(RabbitNames.DLQ_SEGURIDAD).build();
    }

    // =========================================================================
    // 4. BINDINGS
    // =========================================================================

    @Bean
    public Binding bindingInventario(Queue colaAuditoriaInventario, TopicExchange pedidosAuditoriaTopicExchange) {
        return BindingBuilder.bind(colaAuditoriaInventario)
                .to(pedidosAuditoriaTopicExchange)
                .with(RabbitNames.RK_INVENTARIO_PATTERN);
    }

    @Bean
    public Binding bindingEliminaciones(Queue colaAuditoriaEliminaciones, TopicExchange pedidosAuditoriaTopicExchange) {
        return BindingBuilder.bind(colaAuditoriaEliminaciones)
                .to(pedidosAuditoriaTopicExchange)
                .with(RabbitNames.RK_ELIMINACIONES_PATTERN);
    }

    @Bean
    public Binding bindingSeguridad(Queue colaSeguridadAlertas, DirectExchange pedidosSeguridadDirectExchange) {
        return BindingBuilder.bind(colaSeguridadAlertas)
                .to(pedidosSeguridadDirectExchange)
                .with(RabbitNames.RK_SEGURIDAD);
    }

    @Bean
    public Binding bindingDlqInventario(Queue colaAuditoriaInventarioDlq, DirectExchange pedidosDlxExchange) {
        return BindingBuilder.bind(colaAuditoriaInventarioDlq)
                .to(pedidosDlxExchange)
                .with(RabbitNames.RK_DLQ_INVENTARIO);
    }

    @Bean
    public Binding bindingDlqEliminaciones(Queue colaAuditoriaEliminacionesDlq, DirectExchange pedidosDlxExchange) {
        return BindingBuilder.bind(colaAuditoriaEliminacionesDlq)
                .to(pedidosDlxExchange)
                .with(RabbitNames.RK_DLQ_ELIMINACIONES);
    }

    @Bean
    public Binding bindingDlqSeguridad(Queue colaSeguridadAlertasDlq, DirectExchange pedidosDlxExchange) {
        return BindingBuilder.bind(colaSeguridadAlertasDlq)
                .to(pedidosDlxExchange)
                .with(RabbitNames.RK_DLQ_SEGURIDAD);
    }

    // =========================================================================
    // 5. CONVERTIDOR DE MENSAJES Y CONTAINER FACTORY (MANUAL ACK)
    // =========================================================================

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false); // NACK envía a DLQ sin reencolar
        return factory;
    }
}

