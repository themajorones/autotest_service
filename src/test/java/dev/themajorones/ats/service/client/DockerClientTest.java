package dev.themajorones.ats.service.client;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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
}
