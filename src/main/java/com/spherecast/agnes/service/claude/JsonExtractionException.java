package com.spherecast.agnes.service.claude;

public class JsonExtractionException extends RuntimeException {

    public JsonExtractionException(String message) {
        super(message);
    }

    public JsonExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
