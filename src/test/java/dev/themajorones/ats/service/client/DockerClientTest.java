package dev.themajorones.ats.service.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.themajorones.models.dto.CreateAndroidRequest;
import dev.themajorones.models.client.DockerClient;

class DockerClientTest {

    @Test
    void startContainerSendsBodylessPostRequest() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        DockerClient dockerClient = new DockerClient(restClientBuilder);

        server.expect(requestTo("http://docker.example/containers/container-1/start"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(""))
            .andRespond(withSuccess());

        dockerClient.startContainer("http://docker.example", "container-1");

        server.verify();
    }

    @Test
    void pullImageConsumesJsonResponseWithoutStringConversion() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        DockerClient dockerClient = new DockerClient(restClientBuilder);

        server.expect(requestTo("http://docker.example/images/create?fromImage=redroid%2Fredroid%3A12.0.0_64only-latest"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"status\":\"Downloading\"}", MediaType.APPLICATION_JSON));

        dockerClient.pullImage("http://docker.example", "redroid/redroid:12.0.0_64only-latest");

        server.verify();
    }

    @Test
    void createAndroidContainerParsesJsonResponseFromBytes() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        DockerClient dockerClient = new DockerClient(restClientBuilder);

        server.expect(requestTo("http://docker.example/containers/create?name=tmos-android-99"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"Id\":\"container-1\"}", MediaType.APPLICATION_JSON));

        String containerId = dockerClient.createAndroidContainer(
            "http://docker.example",
            "99",
            new CreateAndroidRequest()
                .setImage("redroid/redroid:12.0.0_64only-latest")
                .setAccelerationMode("guest")
        );

        assertEquals("container-1", containerId);
        server.verify();
    }
}
