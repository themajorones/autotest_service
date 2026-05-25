package dev.themajorones.ats.dto.connection;

import lombok.Data;

@Data
public class OllamaConnectionRequest {

    private String name;
    private String baseUrl;
    private Boolean enabled;
    private String model;
}
