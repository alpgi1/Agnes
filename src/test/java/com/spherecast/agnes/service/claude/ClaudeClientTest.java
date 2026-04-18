package com.spherecast.agnes.service.claude;

import com.spherecast.agnes.config.ClaudeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClaudeClientTest {

    private static final String SUCCESS_BODY = """
            {
              "id": "msg_01",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-6",
              "content": [{"type": "text", "text": "hello world"}],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 5, "output_tokens": 2}
            }
            """;

    private static final String JSON_FENCED_BODY = """
            {
              "id": "msg_02",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-6",
              "content": [{"type": "text", "text": "```json\\n{\\"capital\\":\\"Paris\\"}\\n```"}],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 8, "output_tokens": 5}
            }
            """;

    private ClaudeConfig config(String apiKey) {
        return new ClaudeConfig(apiKey, "claude-sonnet-4-6", 4096,
                "https://api.anthropic.com", "2023-06-01");
    }

    private ClaudeClient clientWithMock(ClaudeConfig cfg, MockRestServiceServer[] serverHolder) {
        RestClient.Builder builder = RestClient.builder();
        serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
        return new ClaudeClient(cfg, new JsonExtractor(new ObjectMapper()), builder);
    }

    @Test
    void returnsTextOnSuccess() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        ClaudeClient client = clientWithMock(config("sk-ant-test"), holder);
        holder[0].expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("x-api-key", "sk-ant-test"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        String response = client.ask("sys", "hi");

        assertThat(response).isEqualTo("hello world");
        holder[0].verify();
    }

    @Test
    void retriesOn429ThenSucceeds() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        ClaudeClient client = clientWithMock(config("sk-ant-test"), holder);
        holder[0].expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":\"rate\"}").contentType(MediaType.APPLICATION_JSON));
        holder[0].expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":\"rate\"}").contentType(MediaType.APPLICATION_JSON));
        holder[0].expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        String response = client.ask(null, "hi");

        assertThat(response).isEqualTo("hello world");
        holder[0].verify();
    }

    @Test
    void throwsAfterThree429s() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        ClaudeClient client = clientWithMock(config("sk-ant-test"), holder);
        for (int i = 0; i < 3; i++) {
            holder[0].expect(requestTo("https://api.anthropic.com/v1/messages"))
                    .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                            .body("{\"error\":\"rate\"}").contentType(MediaType.APPLICATION_JSON));
        }

        assertThatThrownBy(() -> client.ask(null, "hi"))
                .isInstanceOf(ClaudeApiException.class)
                .satisfies(ex -> assertThat(((ClaudeApiException) ex).getStatusCode()).isEqualTo(429));
        holder[0].verify();
    }

    @Test
    void doesNotRetryOn400() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        ClaudeClient client = clientWithMock(config("sk-ant-test"), holder);
        holder[0].expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"bad\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.ask(null, "hi"))
                .isInstanceOf(ClaudeApiException.class)
                .satisfies(ex -> assertThat(((ClaudeApiException) ex).getStatusCode()).isEqualTo(400));
        holder[0].verify();
    }

    @Test
    void throwsWhenApiKeyIsPlaceholder() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        ClaudeClient client = clientWithMock(config("PLACEHOLDER"), holder);

        assertThatThrownBy(() -> client.ask(null, "hi"))
                .isInstanceOf(ClaudeApiException.class)
                .satisfies(ex -> assertThat(((ClaudeApiException) ex).getStatusCode()).isEqualTo(-1));
        // no HTTP call should have happened
        holder[0].verify();
    }

    @Test
    void throwsWhenApiKeyIsBlank() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        ClaudeClient client = clientWithMock(config(""), holder);

        assertThatThrownBy(() -> client.ask(null, "hi"))
                .isInstanceOf(ClaudeApiException.class);
        holder[0].verify();
    }

    @Test
    void askJsonParsesFencedJsonFromResponse() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        ClaudeClient client = clientWithMock(config("sk-ant-test"), holder);
        holder[0].expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withSuccess(JSON_FENCED_BODY, MediaType.APPLICATION_JSON));

        record Country(String capital) {
        }
        Country c = client.askJson("You describe countries.", "describe france", Country.class);

        assertThat(c.capital()).isEqualTo("Paris");
        holder[0].verify();
    }
}
