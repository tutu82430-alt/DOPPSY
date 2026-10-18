package com.doppsy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enterprise RabbitMQ AMQP configuration for Doppsy asynchronous messaging.
 *
 * <p>Drives the Smart Waitlist Engine by orchestrating cancellation broadcasts
 * across an AMQP TopicExchange and durable queue architecture.</p>
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_SLOT_EVENTS = "doppsy.slot.exchange";
    public static final String QUEUE_SLOT_CANCELLED = "doppsy.slot.cancelled.queue";
    public static final String ROUTING_KEY_SLOT_CANCELLED = "slot.cancelled";

    @Value("${doppsy.rabbitmq.exchange:" + EXCHANGE_SLOT_EVENTS + "}")
    private String slotExchangeName;

    @Value("${doppsy.rabbitmq.queue:" + QUEUE_SLOT_CANCELLED + "}")
    private String slotQueueName;

    @Value("${doppsy.rabbitmq.routing-key:" + ROUTING_KEY_SLOT_CANCELLED + "}")
    private String slotRoutingKey;

    /**
     * Declares the Topic Exchange for slot lifecycle events.
     */
    @Bean
    public TopicExchange slotExchange() {
        return new TopicExchange(slotExchangeName, true, false);
    }

    /**
     * Declares the durable queue for cancelled slot notifications.
     */
    @Bean
    public Queue slotCancelledQueue() {
        return QueueBuilder.durable(slotQueueName)
            .build();
    }

    /**
     * Binds the cancelled queue to the slot exchange using the routing key pattern.
     */
    @Bean
    public Binding slotCancelledBinding(Queue slotCancelledQueue, TopicExchange slotExchange) {
        return BindingBuilder.bind(slotCancelledQueue)
            .to(slotExchange)
            .with(slotRoutingKey);
    }

    /**
     * JSON message converter using the pre-configured Spring Jackson ObjectMapper
     * to safely serialize/deserialize Java 8+ dates (LocalDate, LocalDateTime) and UUIDs.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Pre-configured RabbitTemplate with JSON serialization for domain events.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
