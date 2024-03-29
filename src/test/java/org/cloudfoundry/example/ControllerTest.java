package org.cloudfoundry.example;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.cloudfoundry.example.Controller.FORWARDED_URL;
import static org.cloudfoundry.example.Controller.PROXY_METADATA;
import static org.cloudfoundry.example.Controller.PROXY_SIGNATURE;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.HOST;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.HEAD;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.util.UriComponentsBuilder;

import okhttp3.MultipartBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

@RunWith(SpringRunner.class)
@SpringBootTest
public final class ControllerTest {

	private static final String BODY_VALUE = "test-body";

	private static final String PROXY_METADATA_VALUE = "test-proxy-metadata";

	private static final String PROXY_SIGNATURE_VALUE = "test-proxy-signature";

	@Rule
	public final MockWebServer mockWebServer = new MockWebServer();

	private WebTestClient webTestClient;

	@Test
	@WithMockUser(roles = "ADMIN")
	public void deleteRequest() {
		String forwardedUrl = getForwardedUrl("/project_metadata/spring-framework");
		prepareResponse(response -> response.setResponseCode(OK.value()));

		this.webTestClient.delete().uri("http://localhost/project_metadata/spring-framework")
				.header(FORWARDED_URL, forwardedUrl).header(PROXY_METADATA, PROXY_METADATA_VALUE)
				.header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE).exchange().expectStatus().isOk();

		expectRequest(request -> {
			assertThat(request.getMethod()).isEqualTo(DELETE.name());
			assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
			assertThat(request.getHeader(FORWARDED_URL)).isNull();
			assertThat(request.getHeader(HOST)).isEqualTo(getForwardedHost());
			assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
			assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
		});
	}

	@Test
	public void getProjectMetadataRequest() {
		String forwardedUrl = getForwardedUrl("/project_metadata/spring-framework");
		prepareResponse(response -> response.setResponseCode(OK.value()).setHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE)
				.setBody(BODY_VALUE));

		this.webTestClient.get().uri("http://localhost/project_metadata/spring-framework")
				.header(FORWARDED_URL, forwardedUrl).header(PROXY_METADATA, PROXY_METADATA_VALUE)
				.header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE).exchange().expectStatus().isOk()
				.expectBody(String.class).isEqualTo(BODY_VALUE);

		expectRequest(request -> {
			assertThat(request.getMethod()).isEqualTo(GET.name());
			assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
			assertThat(request.getHeader(FORWARDED_URL)).isNull();
			assertThat(request.getHeader(HOST)).isEqualTo(getForwardedHost());
			assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
			assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
		});
	}

	@Test
	public void getRequest() {
		String forwardedUrl = getForwardedUrl("/original/get");
		prepareResponse(response -> response.setResponseCode(OK.value()).setHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE)
				.setBody(BODY_VALUE));

		this.webTestClient.get().uri("http://localhost/route-service/get").header(FORWARDED_URL, forwardedUrl)
				.header(PROXY_METADATA, PROXY_METADATA_VALUE).header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE).exchange()
				.expectStatus().isOk().expectBody(String.class).isEqualTo(BODY_VALUE);

		expectRequest(request -> {
			assertThat(request.getMethod()).isEqualTo(GET.name());
			assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
			assertThat(request.getHeader(FORWARDED_URL)).isNull();
			assertThat(request.getHeader(HOST)).isEqualTo(getForwardedHost());
			assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
			assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
		});
	}

	@Test
	public void headRequest() {
		String forwardedUrl = getForwardedUrl("/original/head");
		prepareResponse(response -> response.setResponseCode(OK.value()));

		this.webTestClient.head().uri("http://localhost/route-service/head").header(FORWARDED_URL, forwardedUrl)
				.header(PROXY_METADATA, PROXY_METADATA_VALUE).header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE).exchange()
				.expectStatus().isOk();

		expectRequest(request -> {
			assertThat(request.getMethod()).isEqualTo(HEAD.name());
			assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
			assertThat(request.getHeader(FORWARDED_URL)).isNull();
			assertThat(request.getHeader(HOST)).isEqualTo(getForwardedHost());
			assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
			assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
		});
	}

	@Test
	public void incompleteRequest() {
		this.webTestClient.head().uri("http://localhost/project_metadata/spring-framework").exchange().expectStatus()
				.isNotFound();
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	public void multipart() throws IOException {
		String forwardedUrl = getForwardedUrl("/project_metadata/spring-framework");

		Buffer body = new Buffer();
		new MultipartBody.Builder().addFormDataPart("body-key", "body-value").build().writeTo(body);

		prepareResponse(response -> response.setResponseCode(OK.value()).setBody(body));

		this.webTestClient.post().uri("http://localhost/project_metadata/spring-framework")
				.header(FORWARDED_URL, forwardedUrl).header(PROXY_METADATA, PROXY_METADATA_VALUE)
				.header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE)
				.body(BodyInserters.fromMultipartData("body-key", "body-value")).exchange().expectStatus().isOk()
				.expectBody(String.class)
				.consumeWith(result -> assertThat(result.getResponseBody()).contains("body-value"));

		expectRequest(request -> {
			assertThat(request.getMethod()).isEqualTo(POST.name());
			assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
			assertThat(request.getHeader(CONTENT_TYPE)).startsWith(MULTIPART_FORM_DATA_VALUE);
			assertThat(request.getHeader(FORWARDED_URL)).isNull();
			assertThat(request.getHeader(HOST)).isEqualTo(getForwardedHost());
			assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
			assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
			assertThat(request.getBody().readString(UTF_8)).contains("body-value");
		});
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	public void patchRequest() {
		String forwardedUrl = getForwardedUrl("/project_metadata/spring-framework");
		prepareResponse(response -> response.setResponseCode(OK.value()).setHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE)
				.setBody(BODY_VALUE));

		this.webTestClient.patch().uri("http://localhost/project_metadata/spring-framework")
				.header(FORWARDED_URL, forwardedUrl).header(PROXY_METADATA, PROXY_METADATA_VALUE)
				.header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE).syncBody(BODY_VALUE).exchange().expectStatus().isOk()
				.expectBody(String.class).isEqualTo(BODY_VALUE);

		expectRequest(request -> {
			assertThat(request.getMethod()).isEqualTo(PATCH.name());
			assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
			assertThat(request.getHeader(FORWARDED_URL)).isNull();
			assertThat(request.getHeader(HOST)).isEqualTo(getForwardedHost());
			assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
			assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
			assertThat(request.getBody().readString(UTF_8)).isEqualTo(BODY_VALUE);
		});
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	public void postRequest() {
		String forwardedUrl = getForwardedUrl("/project_metadata/spring-framework");
		prepareResponse(response -> response.setResponseCode(OK.value()).setHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE)
				.setBody(BODY_VALUE));

		this.webTestClient.post().uri("http://localhost/project_metadata/spring-framework")
				.header(FORWARDED_URL, forwardedUrl).header(PROXY_METADATA, PROXY_METADATA_VALUE)
				.header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE).syncBody(BODY_VALUE).exchange().expectStatus().isOk()
				.expectBody(String.class).isEqualTo(BODY_VALUE);

		expectRequest(request -> {
			assertThat(request.getMethod()).isEqualTo(POST.name());
			assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
			assertThat(request.getHeader(FORWARDED_URL)).isNull();
			assertThat(request.getHeader(HOST)).isEqualTo(getForwardedHost());
			assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
			assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
			assertThat(request.getBody().readString(UTF_8)).isEqualTo(BODY_VALUE);
		});
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	public void putRequest() {
		String forwardedUrl = getForwardedUrl("/project_metadata/spring-framework");
		prepareResponse(response -> response.setResponseCode(OK.value()).setHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE)
				.setBody(BODY_VALUE));

		this.webTestClient.put().uri("http://localhost/project_metadata/spring-framework")
				.header(FORWARDED_URL, forwardedUrl).header(PROXY_METADATA, PROXY_METADATA_VALUE)
				.header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE).syncBody(BODY_VALUE).exchange().expectStatus().isOk()
				.expectBody(String.class).isEqualTo(BODY_VALUE);

		expectRequest(request -> {
			assertThat(request.getMethod()).isEqualTo(PUT.name());
			assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
			assertThat(request.getHeader(FORWARDED_URL)).isNull();
			assertThat(request.getHeader(HOST)).isEqualTo(getForwardedHost());
			assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
			assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
			assertThat(request.getBody().readString(UTF_8)).isEqualTo(BODY_VALUE);
		});
	}

	@Autowired
	void setWebApplicationContext(ApplicationContext applicationContext) {
		this.webTestClient = WebTestClient.bindToApplicationContext(applicationContext).configureClient()
				.responseTimeout(Duration.ofMinutes(10)).build();
	}

	private void expectRequest(Consumer<RecordedRequest> consumer) {
		try {
			assertThat(this.mockWebServer.getRequestCount()).isEqualTo(1);
			consumer.accept(this.mockWebServer.takeRequest());
		}
		catch (InterruptedException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String getForwardedHost() {
		return String.format("%s:%d", this.mockWebServer.getHostName(), this.mockWebServer.getPort());
	}

	private String getForwardedUrl(String path) {
		return UriComponentsBuilder.newInstance().scheme("http").host(this.mockWebServer.getHostName())
				.port(this.mockWebServer.getPort()).path(path).toUriString();
	}

	private void prepareResponse(Consumer<MockResponse> consumer) {
		MockResponse response = new MockResponse();
		consumer.accept(response);
		this.mockWebServer.enqueue(response);
	}

}
