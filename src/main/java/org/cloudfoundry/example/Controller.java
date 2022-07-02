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

import static org.springframework.http.HttpHeaders.HOST;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
final class Controller {

	static final String FORWARDED_URL = "X-CF-Forwarded-Url";

	static final String FORWARDED_FOR = "X-Forwarded-For";

	static final String FORWARDED_PORT = "X-Forwarded-Port";

	static final String FORWARDED_PROTO = "X-Forwarded-Proto";

	static final String PROXY_METADATA = "X-CF-Proxy-Metadata";

	static final String PROXY_SIGNATURE = "X-CF-Proxy-Signature";

	static final String USER = "X-User";

	@Value("${backend.url.legacy:http://localhost:8081}")
	private String defaultUrl = "http://localhost:8081";

	@Value("${backend.url.shiny:http://localhost:8082}")
	private String shinyUrl = "http://localhost:8082";

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	private final WebClient webClient;

	Controller(WebClient webClient) {
		this.webClient = webClient;
	}

	@RequestMapping(path = { "/**", "/" })
	Mono<ResponseEntity<Flux<DataBuffer>>> service(ServerHttpRequest request, Principal principal) {

		this.logger.info("Incoming Request:  {}",
				formatRequest(request.getMethod(), request.getURI().toString(), request.getHeaders()));

		String[] forwardedUrls = getForwardedUrl(request, principal);
		HttpHeaders forwardedHttpHeaders = getForwardedHeaders(request, principal);

		this.logger.info("Outgoing Request:  {}",
				formatRequest(request.getMethod(), forwardedUrls[0], forwardedHttpHeaders));

		return get(request, forwardedHttpHeaders, forwardedUrls[0]).onErrorResume(Exception.class, error -> {
			this.logger.info("Retrying: {}", forwardedUrls[1]);
			return get(request, forwardedHttpHeaders, forwardedUrls[1]).onErrorResume(ex -> {
				return ex.toString().contains("Connection refused");
			}, ex -> {
				return Mono.just(ResponseEntity.notFound().build());
			});
		});
	}

	private Mono<ResponseEntity<Flux<DataBuffer>>> get(ServerHttpRequest request, HttpHeaders forwardedHttpHeaders,
			String url) {
		return this.webClient.method(request.getMethod()).uri(url)
				.headers(headers -> headers.putAll(forwardedHttpHeaders))
				.body((outputMessage, context) -> outputMessage.writeWith(request.getBody())).exchange()
				.map(this::respond);
	}

	private ResponseEntity<Flux<DataBuffer>> respond(ClientResponse response) {
		this.logger.info("Outgoing Response: {}",
				formatResponse(response.statusCode(), response.headers().asHttpHeaders()));

		return ResponseEntity.status(response.statusCode()).headers(response.headers().asHttpHeaders())
				.body(response.bodyToFlux(DataBuffer.class));
	}

	private static String formatRequest(HttpMethod method, String uri, HttpHeaders headers) {
		return String.format("%s %s, %s", method, uri, headers);
	}

	private String[] getForwardedUrl(ServerHttpRequest request, Principal principal) {
		HttpHeaders httpHeaders = request.getHeaders();
		String forwardedUrl = httpHeaders.getFirst(FORWARDED_URL);
		String legacy = forwardedUrl;
		String shiny = null;

		if (forwardedUrl == null) {
			legacy = defaultUrl + request.getPath();
			shiny = shinyUrl + request.getPath();
		}
		else {
			try {
				URI uri;
				uri = new URI(shinyUrl);
				int port = uri.getPort();
				String host = uri.getHost();
				shiny = UriComponentsBuilder.fromUriString(forwardedUrl).host(host).port(port).build().toUriString();
			}
			catch (URISyntaxException e) {
				throw new IllegalStateException("Cannot parse URI: " + shinyUrl);
			}
		}

		if (principal instanceof Authentication) {
			Authentication authentication = (Authentication) principal;
			if (authentication.isAuthenticated()) {
				return new String[] { shiny, legacy };
			}
		}
		return new String[] { legacy, shiny };
	}

	private String formatResponse(HttpStatus statusCode, HttpHeaders headers) {
		return String.format("%s, %s", statusCode, headers);
	}

	private HttpHeaders getForwardedHeaders(ServerHttpRequest request, Principal principal) {
		HttpHeaders incoming = request.getHeaders();
		HttpHeaders outgoing = incoming.entrySet().stream().filter(
				entry -> !entry.getKey().equalsIgnoreCase(FORWARDED_URL) && !entry.getKey().equalsIgnoreCase(HOST))
				.collect(HttpHeaders::new, (httpHeaders, entry) -> httpHeaders.addAll(entry.getKey(), entry.getValue()),
						HttpHeaders::putAll);
		if (!outgoing.containsKey(FORWARDED_FOR) && incoming.getHost() != null) {
			outgoing.set(FORWARDED_FOR, "127.0.0.1");
			outgoing.set(FORWARDED_PORT, "8080");
			outgoing.set(FORWARDED_PROTO, "http");
		}
		if (principal != null && !StringUtils.isEmpty(principal.getName())) {
			// Downstream service can pick this app for authentication
			outgoing.set(USER, principal.getName());
		}
		return outgoing;
	}

}
