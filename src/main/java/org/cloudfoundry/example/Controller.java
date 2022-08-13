/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
final class Controller {

    static final String FORWARDED_URL = "X-CF-Forwarded-Url";

    static final String PROXY_METADATA = "X-CF-Proxy-Metadata";

    static final String PROXY_SIGNATURE = "X-CF-Proxy-Signature";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final WebClient webClient;

    Controller(WebClient webClient) {
        this.webClient = webClient;
    }

    @RequestMapping(headers = {FORWARDED_URL, PROXY_METADATA, PROXY_SIGNATURE})
    Mono<ResponseEntity<Flux<DataBuffer>>> service(ServerHttpRequest request) {

        this.logger.info("Incoming Request: {}", formatRequest(request.getMethod(), request.getURI().toString(), request.getHeaders()));

        String forwardedUrl = getForwardedUrl(request.getHeaders());
        HttpHeaders forwardedHttpHeaders = getForwardedHeaders(request.getHeaders());

        this.logger.info("Outgoing Request: {}", formatRequest(request.getMethod(), forwardedUrl, forwardedHttpHeaders));

        return this.webClient
            .method(request.getMethod())
            .uri(forwardedUrl)
            .headers(headers -> headers.putAll(forwardedHttpHeaders))
            .body((outputMessage, context) -> outputMessage.writeWith(request.getBody()))
            .exchange()
            .map(response -> ResponseEntity
                .status(response.statusCode())
                .headers(response.headers().asHttpHeaders())
                .body(response.bodyToFlux(DataBuffer.class)));
    }

    private static String formatRequest(HttpMethod method, String uri, HttpHeaders headers) {
        return String.format("%s %s, %s", method, uri, headers);
    }

    private static String getForwardedUrl(HttpHeaders httpHeaders) {
        String forwardedUrl = httpHeaders.getFirst(FORWARDED_URL);

        if (forwardedUrl == null) {
            throw new IllegalStateException(String.format("No %s header present", FORWARDED_URL));
        }

        return forwardedUrl;
    }

    private HttpHeaders getForwardedHeaders(HttpHeaders headers) {
        return headers.entrySet().stream()
            .filter(entry -> !entry.getKey().equalsIgnoreCase(FORWARDED_URL))
            .collect(HttpHeaders::new, (httpHeaders, entry) -> httpHeaders.addAll(entry.getKey(), entry.getValue()), HttpHeaders::putAll);
    }

}
