package com.spherecast.agnes.handler;

import java.util.List;

record RouterDto(
        List<String> optimizers,
        ScopeDto scope,
        String reasoning
) {
    record ScopeDto(String type, String value) {}
}
