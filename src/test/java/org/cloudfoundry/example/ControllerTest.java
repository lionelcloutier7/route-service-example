package org.cloudfoundry.example;

import okhttp3.MultipartBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.cloudfoundry.example.Controller.FORWARDED_URL;
import static org.cloudfoundry.example.Controller.PROXY_METADATA;
import static org.cloudfoundry.example.Controller.PROXY_SIGNATURE;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.HEAD;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

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
    public void deleteRequest() {
        String forwardedUrl = getForwardedUrl("/original/delete");
        prepareResponse(response -> response
            .setResponseCode(OK.value()));

        this.webTestClient
            .delete().uri("http://localhost/route-service/delete")
            .header(FORWARDED_URL, forwardedUrl)
            .header(PROXY_METADATA, PROXY_METADATA_VALUE)
            .header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE)
            .exchange()
            .expectStatus().isOk();

        expectRequest(request -> {
            assertThat(request.getMethod()).isEqualTo(DELETE.name());
            assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
            assertThat(request.getHeader(FORWARDED_URL)).isNull();
            assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
            assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
        });
    }

    @Test
    public void getRequest() {
        String forwardedUrl = getForwardedUrl("/original/get");
        prepareResponse(response -> response
            .setResponseCode(OK.value())
            .setHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE)
            .setBody(BODY_VALUE));

        this.webTestClient
            .get().uri("http://localhost/route-service/get")
            .header(FORWARDED_URL, forwardedUrl)
            .header(PROXY_METADATA, PROXY_METADATA_VALUE)
            .header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo(BODY_VALUE);

        expectRequest(request -> {
            assertThat(request.getMethod()).isEqualTo(GET.name());
            assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
            assertThat(request.getHeader(FORWARDED_URL)).isNull();
            assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
            assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
        });
    }

    @Test
    public void headRequest() {
        String forwardedUrl = getForwardedUrl("/original/head");
        prepareResponse(response -> response
            .setResponseCode(OK.value()));

        this.webTestClient
            .head().uri("http://localhost/route-service/head")
            .header(FORWARDED_URL, forwardedUrl)
            .header(PROXY_METADATA, PROXY_METADATA_VALUE)
            .header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE)
            .exchange()
            .expectStatus().isOk();

        expectRequest(request -> {
            assertThat(request.getMethod()).isEqualTo(HEAD.name());
            assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
            assertThat(request.getHeader(FORWARDED_URL)).isNull();
            assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
            assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
        });
    }

    @Test
    public void incompleteRequest() {
        this.webTestClient
            .head().uri("http://localhost/route-service/incomplete")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    public void multipart() throws IOException {
        String forwardedUrl = getForwardedUrl("/original/multipart");

        Buffer body = new Buffer();
        new MultipartBody.Builder().addFormDataPart("body-key", "body-value").build().writeTo(body);

        prepareResponse(response -> response
            .setResponseCode(OK.value())
            .setBody(body));

        this.webTestClient
            .post().uri("http://localhost/route-service/multipart")
            .header(FORWARDED_URL, forwardedUrl)
            .header(PROXY_METADATA, PROXY_METADATA_VALUE)
            .header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE)
            .body(BodyInserters.fromMultipartData("body-key", "body-value"))
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).consumeWith(result -> assertThat(result.getResponseBody()).contains("body-value"));

        expectRequest(request -> {
            assertThat(request.getMethod()).isEqualTo(POST.name());
            assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
            assertThat(request.getHeader(CONTENT_TYPE)).startsWith(MULTIPART_FORM_DATA_VALUE);
            assertThat(request.getHeader(FORWARDED_URL)).isNull();
            assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
            assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
            assertThat(request.getBody().readString(UTF_8)).contains("body-value");
        });
    }

    @Test
    public void patchRequest() {
        String forwardedUrl = getForwardedUrl("/original/patch");
        prepareResponse(response -> response
            .setResponseCode(OK.value())
            .setHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE)
            .setBody(BODY_VALUE));

        this.webTestClient
            .patch().uri("http://localhost/route-service/patch")
            .header(FORWARDED_URL, forwardedUrl)
            .header(PROXY_METADATA, PROXY_METADATA_VALUE)
            .header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE)
            .syncBody(BODY_VALUE)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo(BODY_VALUE);

        expectRequest(request -> {
            assertThat(request.getMethod()).isEqualTo(PATCH.name());
            assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
            assertThat(request.getHeader(FORWARDED_URL)).isNull();
            assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
            assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
            assertThat(request.getBody().readString(UTF_8)).isEqualTo(BODY_VALUE);
        });
    }

    @Test
    public void postRequest() {
        String forwardedUrl = getForwardedUrl("/original/post");
        prepareResponse(response -> response
            .setResponseCode(OK.value())
            .setHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE)
            .setBody(BODY_VALUE));

        this.webTestClient
            .post().uri("http://localhost/route-service/post")
            .header(FORWARDED_URL, forwardedUrl)
            .header(PROXY_METADATA, PROXY_METADATA_VALUE)
            .header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE)
            .syncBody(BODY_VALUE)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo(BODY_VALUE);

        expectRequest(request -> {
            assertThat(request.getMethod()).isEqualTo(POST.name());
            assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
            assertThat(request.getHeader(FORWARDED_URL)).isNull();
            assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
            assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
            assertThat(request.getBody().readString(UTF_8)).isEqualTo(BODY_VALUE);
        });
    }

    @Test
    public void putRequest() {
        String forwardedUrl = getForwardedUrl("/original/put");
        prepareResponse(response -> response
            .setResponseCode(OK.value())
            .setHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE)
            .setBody(BODY_VALUE));

        this.webTestClient
            .put().uri("http://localhost/route-service/put")
            .header(FORWARDED_URL, forwardedUrl)
            .header(PROXY_METADATA, PROXY_METADATA_VALUE)
            .header(PROXY_SIGNATURE, PROXY_SIGNATURE_VALUE)
            .syncBody(BODY_VALUE)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo(BODY_VALUE);

        expectRequest(request -> {
            assertThat(request.getMethod()).isEqualTo(PUT.name());
            assertThat(request.getRequestUrl().toString()).isEqualTo(forwardedUrl);
            assertThat(request.getHeader(FORWARDED_URL)).isNull();
            assertThat(request.getHeader(PROXY_METADATA)).isEqualTo(PROXY_METADATA_VALUE);
            assertThat(request.getHeader(PROXY_SIGNATURE)).isEqualTo(PROXY_SIGNATURE_VALUE);
            assertThat(request.getBody().readString(UTF_8)).isEqualTo(BODY_VALUE);
        });
    }

    @Autowired
    void setWebApplicationContext(ApplicationContext applicationContext) {
        this.webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    private void expectRequest(Consumer<RecordedRequest> consumer) {
        try {
            assertThat(this.mockWebServer.getRequestCount()).isEqualTo(1);
            consumer.accept(this.mockWebServer.takeRequest());
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String getForwardedUrl(String path) {
        return UriComponentsBuilder.newInstance()
            .scheme("http")
            .host(this.mockWebServer.getHostName())
            .port(this.mockWebServer.getPort())
            .path(path)
            .toUriString();
    }

    private void prepareResponse(Consumer<MockResponse> consumer) {
        MockResponse response = new MockResponse();
        consumer.accept(response);
        this.mockWebServer.enqueue(response);
    }

}
