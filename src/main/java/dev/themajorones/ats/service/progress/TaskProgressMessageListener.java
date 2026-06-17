package dev.themajorones.ats.service.progress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import com.rabbitmq.client.Channel;

import dev.themajorones.models.constants.RabbitMqConstant;
import dev.themajorones.models.dto.TaskProgressEvent;
import tools.jackson.databind.ObjectMapper;

@Service
public class TaskProgressMessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(TaskProgressMessageListener.class);

    private final ObjectMapper objectMapper;
    private final TaskProgressBroadcaster taskProgressBroadcaster;

    public TaskProgressMessageListener(ObjectMapper objectMapper, TaskProgressBroadcaster taskProgressBroadcaster) {
        this.objectMapper = objectMapper;
        this.taskProgressBroadcaster = taskProgressBroadcaster;
    }

    @RabbitListener(queues = RabbitMqConstant.Queue.TaskProgress.NAME, ackMode = "MANUAL")
    public void listen(
        String message,
        Channel channel,
        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        try {
            TaskProgressEvent event = objectMapper.readValue(message, TaskProgressEvent.class);
            taskProgressBroadcaster.broadcast(event);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            LOG.error("Failed to process task progress event: {}", message, ex);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception nackEx) {
                throw new IllegalStateException("Unable to reject task progress message", nackEx);
            }
        }
    }
}
