package com.dreamspace.worker;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "spring.main.web-application-type=none",
        "spring.ai.openai.api-key=test-only-key",
        "spring.ai.openai.chat.options.model=fixture-model"
})
class OpenAiCompatibleChatModelTest {
    private static final WireMockServer wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @Autowired
    private ChatModel chatModel;

    @BeforeAll
    static void startServer() {
        wireMock.start();
        configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Test
    void callsOpenAiCompatibleChatCompletions() {
        stubFor(post(anyUrl())
                .withRequestBody(matchingJsonPath("$.model", equalTo("fixture-model")))
                .withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("ping")))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"fixture-response\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"fixture-model\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"pong\"},\"finish_reason\":\"stop\"}]}")));

        var response = chatModel.call(new Prompt("ping"));

        assertThat(response.getResult().getOutput().getText()).isEqualTo("pong");
        assertThat(wireMock.getAllServeEvents())
                .singleElement()
                .extracting(event -> event.getRequest().getUrl())
                .asString()
                .endsWith("/chat/completions");
    }
}
