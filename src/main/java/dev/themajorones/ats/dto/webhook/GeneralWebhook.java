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
public class GeneralWebhook {
    private int code;
    private JsonNode rawData;
    private Long timestamp;
}
