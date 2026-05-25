package dev.themajorones.ats.dto.webhook;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.databind.JsonNode;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReCallWebhookRequest {
	private String deliveryId;
	private String event;
	private String repository;
	private JsonNode payload;
	private Long timestamp;
}
