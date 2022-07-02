package org.cloudfoundry.example;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginReactiveAuthenticationManager;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.userinfo.DefaultReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.web.server.authentication.OAuth2LoginAuthenticationWebFilter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilter;

import reactor.core.publisher.Mono;

@SpringBootApplication
public class RouteServiceApplication {

	private static final String IS_MEMBER_URL = "https://api.github.com/teams/{team}/members/{user}";

	private static final String USER_URL = "https://api.github.com/user";

	@Value("${github.team.id}")
	private String gitHubTeamId;

	public static void main(String[] args) {
		SpringApplication.run(RouteServiceApplication.class, args);
	}

	@Bean
	WebClient webClient() {
		return WebClient.create();
	}

	@Bean
	@Order(0)
	public SecurityWebFilterChain oauth2SecurityFilterChain(ServerHttpSecurity http, WebClient.Builder builder) {
		SecurityWebFilterChain chain = http.authorizeExchange() //
				.pathMatchers("/admin/**").authenticated() //
				.and() //
				.oauth2Login().authenticationManager(authenticationManager(builder)) //
				.and().build();
		for (WebFilter filter : chain.getWebFilters().collectList().block()) {
			// TODO: replace this with a Spring Boot error handler for
			// AuthenticationException
			if (filter instanceof OAuth2LoginAuthenticationWebFilter) {
				OAuth2LoginAuthenticationWebFilter oauth = (OAuth2LoginAuthenticationWebFilter) filter;
				oauth.setAuthenticationFailureHandler(
						new RedirectServerAuthenticationFailureHandler("/error?auth=true"));
			}
		}
		return chain;
	}

	@Order(10)
	public SecurityWebFilterChain basicSecurityFilterChain(ServerHttpSecurity http, WebClient.Builder builder) {
		SecurityWebFilterChain chain = http.authorizeExchange() //
				.pathMatchers(HttpMethod.HEAD, "/project_metadata/**").permitAll() //
				.pathMatchers(HttpMethod.GET, "/project_metadata/**").permitAll() //
				.pathMatchers("/project_metadata/**").authenticated() //
				.anyExchange().permitAll() //
				.and() //
				.csrf().disable().httpBasic().authenticationManager(basicAuthenticationManager(builder)) //
				.and().build();
		return chain;
	}

	private ReactiveAuthenticationManager basicAuthenticationManager(WebClient.Builder builder) {
		return authentication -> {
			WebClient web = builder.build();
			return web.get().uri(USER_URL).headers(headers -> headers.setBearerAuth(authentication.getName()))
					.exchange().flatMap(response -> response.bodyToMono(Map.class)).flatMap(user -> {
						return web.get().uri(IS_MEMBER_URL, gitHubTeamId, user.get("login"))
								.headers(headers -> headers.setBearerAuth(authentication.getName())).exchange()
								.map(response -> {
									if (!response.statusCode().is2xxSuccessful()) {
										throw new BadCredentialsException("Wrong team");
									}
									return new UsernamePasswordAuthenticationToken(user.get("login"), "");
								});
					});
		};
	}

	private ReactiveAuthenticationManager authenticationManager(WebClient.Builder builder) {
		return new OAuth2LoginReactiveAuthenticationManager(new WebClientReactiveAuthorizationCodeTokenResponseClient(),
				new DefaultReactiveOAuth2UserService() {
					@Override
					public Mono<OAuth2User> loadUser(OAuth2UserRequest userRequest)
							throws OAuth2AuthenticationException {
						return super.loadUser(userRequest).flatMap(user -> {
							WebClient web = builder.build();
							System.err.println(user.getAttributes().get("login") + ":"
									+ userRequest.getAccessToken().getTokenValue());
							return web.get().uri(IS_MEMBER_URL, gitHubTeamId, user.getAttributes().get("login"))
									.headers(headers -> headers
											.setBearerAuth(userRequest.getAccessToken().getTokenValue()))
									.exchange().map(response -> {
										if (!response.statusCode().is2xxSuccessful()) {
											throw new BadCredentialsException("Wrong team");
										}
										return user;
									});
						});
					}
				});
	}

}
