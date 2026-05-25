package dev.themajorones.ats.service;
import dev.themajorones.ats.dto.webhook.ReCallWebhookRequest;
import dev.themajorones.ats.dto.webhook.ResultDTO;
import tools.jackson.databind.JsonNode;

public interface WebhookService {

    ResultDTO reCallWebhook(ReCallWebhookRequest request) throws Exception;

    void passWebhookToWebservice(JsonNode payload, String serviceUrl);
}
