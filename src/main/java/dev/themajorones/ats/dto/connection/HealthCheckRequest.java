package dev.themajorones.ats.dto.connection;

import java.util.List;

import lombok.Data;

@Data
public class HealthCheckRequest {

    private List<Integer> ids;
}
