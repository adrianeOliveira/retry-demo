package br.com.adriane.reactivewebclient.retrydemo;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
class RetryDemoApplicationTests {
	private ExternalConnector externalConnector;

	private MockWebServer mockWebServer;

	@BeforeEach
	void setup() throws IOException {
		externalConnector = new ExternalConnector(WebClient.builder()
			.baseUrl("http://localhost:8090")
			.build());
		mockWebServer = new MockWebServer();
		mockWebServer.start(8090);
	}

	@AfterEach
	void tearDown() throws IOException {
		mockWebServer.shutdown();
	}

	@Test
	void givenExternalServiceReturnsError_whenGettingData_thenRetryAndReturnResponse() throws Exception {

		mockWebServer.enqueue(new MockResponse().setResponseCode(SERVICE_UNAVAILABLE.code()));
		mockWebServer.enqueue(new MockResponse().setResponseCode(SERVICE_UNAVAILABLE.code()));
		mockWebServer.enqueue(new MockResponse().setResponseCode(SERVICE_UNAVAILABLE.code()));
		mockWebServer.enqueue(new MockResponse().setBody("stock data"));

		StepVerifier.create(externalConnector.getData("ABC"))
			.expectNextMatches(response -> response.equals("stock data"))
			.verifyComplete();

		verifyNumberOfGetRequests(4);
	}

	@Test
	void givenExternalServiceReturnsError_whenGettingData_thenRetryAndReturnError() throws Exception {

		mockWebServer.enqueue(new MockResponse().setResponseCode(SERVICE_UNAVAILABLE.code()));
		mockWebServer.enqueue(new MockResponse().setResponseCode(SERVICE_UNAVAILABLE.code()));
		mockWebServer.enqueue(new MockResponse().setResponseCode(SERVICE_UNAVAILABLE.code()));
		mockWebServer.enqueue(new MockResponse().setResponseCode(SERVICE_UNAVAILABLE.code()));

		StepVerifier.create(externalConnector.getData("ABC"))
			.expectError()
			.verify();

		verifyNumberOfGetRequests(4);
	}

	private void verifyNumberOfGetRequests(int times) throws Exception {
		for (int i = 0; i < times; i++) {
			RecordedRequest recordedRequest = mockWebServer.takeRequest();
			assertThat(recordedRequest.getMethod()).isEqualTo("GET");
			assertThat(recordedRequest.getPath()).isEqualTo("/data/ABC");
		}
	}
}
