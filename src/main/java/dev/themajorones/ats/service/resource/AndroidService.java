package dev.themajorones.ats.service.resource;

import java.util.List;
import java.util.Map;

import dev.themajorones.models.dto.AndroidDetail;
import dev.themajorones.models.dto.CreateAndroidRequest;

public interface AndroidService {

    List<Map<String, Object>> listAndroid();

    Map<String, Object> updateAndroid(Integer id, CreateAndroidRequest request);

    AndroidDetail getAndroid(Integer id);

    Map<String, Object> createAndroid(CreateAndroidRequest request);

    Map<String, Object> stopAndroid(Integer id);

    void deleteAndroid(Integer id);

    String checkHealth(Integer id);

    List<Map<String, Object>> checkHealth(List<Integer> ids);
}
