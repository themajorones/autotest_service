package dev.themajorones.ats.web.rest;

import dev.themajorones.ats.service.WebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

@RestController
public class WebhookResource {

    private final WebhookService webhookService;
    private final String serviceUrl;

    public WebhookResource(WebhookService webhookService, @Value("${DEV_HOSTNAME:}") String serviceUrl) {
        this.webhookService = webhookService;
        this.serviceUrl  = serviceUrl;
    }

    @PostMapping("/webhook/github")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody JsonNode payload,
            HttpServletRequest request
    ) { 
        if (StringUtils.hasText(serviceUrl) && !serviceUrl.equalsIgnoreCase(request.getServerName())) {
            webhookService.passWebhookToWebservice(payload, serviceUrl + "/webhook/github");
            System.out.println("Sent to" + serviceUrl +"Succesfully!"); 
        }
        return ResponseEntity.ok().build();
    }
}
