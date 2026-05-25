package dev.themajorones.ats.dto.webhook;

import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import dev.themajorones.ats.dto.ResultException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ResultDTO {
     private Object message;
    private String reason;
    private boolean status = false;
    private Object data;
    private Integer count;


    public ResultDTO(Object message, String reason) {
        if (message instanceof String) {
            message = List.of(new ResultException(message.toString(), reason));
        }
        this.message = message;
        this.reason = reason;
    }

    public ResultDTO(Object message, String reason, boolean status) {
        this.message = message;
        this.reason = reason;
        this.status = status;
    }

    public ResultDTO(Object message, String reason, boolean status, Object data) {
        this.message = message;
        this.reason = reason;
        this.status = status;
        this.data = data;
    }

    public Object getMessage() {
        if (message instanceof String) {
            message = List.of(new ResultException(message.toString(), reason));
        }
        return message;
    }


    @Override
    public String toString() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.registerModule(new JavaTimeModule());
            return objectMapper.writeValueAsString(this.message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
