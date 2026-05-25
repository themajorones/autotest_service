package dev.themajorones.ats.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.themajorones.ats.dto.webhook.ReCallWebhookRequest;
import dev.themajorones.ats.dto.webhook.ResultDTO;
import dev.themajorones.ats.service.WebhookService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Service
public class WebhookServiceImpl implements WebhookService {
    private final RestClient restClient;

    public WebhookServiceImpl(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public ResultDTO reCallWebhook(ReCallWebhookRequest request) throws Exception {
        if (request == null) {
            return new ResultDTO("Webhook recall request is required", "INVALID_REQUEST", false);
        }

        Map<String, Object> replayData = new LinkedHashMap<>();
        replayData.put("deliveryId", request.getDeliveryId());
        replayData.put("event", request.getEvent());
        replayData.put("repository", request.getRepository());
        replayData.put("timestamp", request.getTimestamp());
        replayData.put("payload", request.getPayload());

        return new ResultDTO(
                "GitHub webhook recall prepared",
                "OK",
                true,
                replayData,
                1
        );
    }

    @Override
    public void passWebhookToWebservice(JsonNode payload, String serviceUrl) {
        if (!StringUtils.hasText(serviceUrl)) {
            throw new IllegalStateException("SERVICE_URL must be configured");
        }

        ResponseEntity<Void> response = restClient.post()
                .uri(serviceUrl)
                .headers(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    
                })
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        System.out.println("Forwarded webhook to " + serviceUrl + " with status " + response.getStatusCode());
    }
}
