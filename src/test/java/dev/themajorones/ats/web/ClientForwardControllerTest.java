package dev.themajorones.ats.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ClientForwardControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ClientForwardController()).build();

    @Test
    void forwardsSpaRoutesButNotWebSocketEndpoint() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/tests"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/ws"))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/assets/index-test.js"))
            .andExpect(status().isNotFound());
    }
}
