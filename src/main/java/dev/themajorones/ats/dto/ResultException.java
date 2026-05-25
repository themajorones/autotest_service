package dev.themajorones.ats.dto;

import java.io.Serializable;

public class ResultException implements Serializable{
     private String code;
    private String message;

    public ResultException() {}

    public ResultException(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
