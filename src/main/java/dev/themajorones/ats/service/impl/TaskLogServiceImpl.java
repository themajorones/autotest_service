package dev.themajorones.ats.service.impl;

import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.themajorones.ats.repository.TaskLogRepository;
import dev.themajorones.ats.service.TaskLogService;
import dev.themajorones.models.constants.RabbitMqConstant;
import dev.themajorones.models.constants.TaskLogConstant;
import dev.themajorones.models.dto.TaskCommandEnvelope;
import dev.themajorones.models.entity.TaskLog;
import dev.themajorones.models.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class TaskLogServiceImpl implements TaskLogService {

    private final TaskLogRepository taskLogRepository;
    private final RabbitOperations rabbitOperations;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    @Override
    public List<TaskLog> listTaskLogs() {
        return taskLogRepository.findTop100ByOrderByIdDesc();
    }

    @Transactional
    @Override
    public TaskLog retryTaskLog(Integer id) {
        TaskLog taskLog = taskLogRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Task log not found"));
        if (
            TaskLogConstant.Status.PENDING.equals(taskLog.getStatus()) ||
            TaskLogConstant.Status.RUNNING.equals(taskLog.getStatus())
        ) {
            throw new IllegalStateException("Task log is already in progress");
        }

        taskLog
            .setStatus(TaskLogConstant.Status.PENDING)
            .setStartedAt(null)
            .setEndedAt(null)
            .setResult(null);
        taskLogRepository.save(taskLog);

        TaskCommandEnvelope envelope = new TaskCommandEnvelope()
            .setTaskLogId(taskLog.getId())
            .setType(taskLog.getType())
            .setContent(taskLog.getContent());

        rabbitOperations.convertAndSend(
            RabbitMqConstant.DIRECT_EXCHANGE,
            routingKeyFor(taskLog.getType()),
            JsonUtils.writeJson(objectMapper, envelope, "Unable to serialize JSON")
        );

        taskLog.setStatus(TaskLogConstant.Status.QUEUED);
        return taskLogRepository.save(taskLog);
    }

    @Transactional
    @Override
    public void clearTaskLogs() {
        taskLogRepository.deleteAllInBatch();
    }

    private String routingKeyFor(String type) {
        if (TaskLogConstant.Type.CREATE_ANDROID.equals(type)) {
            return RabbitMqConstant.Queue.Android.ROUTING_KEY;
        }
        if (TaskLogConstant.Type.INSTALL_APK.equals(type)) {
            return RabbitMqConstant.Queue.Artifact.ROUTING_KEY;
        }
        throw new IllegalArgumentException("Unsupported task log type: " + type);
    }
}
