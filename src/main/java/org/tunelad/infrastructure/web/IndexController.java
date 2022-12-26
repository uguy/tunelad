package org.tunelad.infrastructure.web;

import java.net.URI;

import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

	// Refreshing browser while on url /tracks or about end up with 404 if we don"t redirect to root
	// https://docs.spring.io/spring-framework/docs/6.0.x/reference/html/web-reactive.html#webflux-ann-requestmapping-uri-templates
	@GetMapping(value = "/?**")
	public Mono<Void> indexController(ServerHttpRequest request, ServerHttpResponse response) {
		if (!request.getURI().toString().equals("/")) {
			response.setStatusCode(HttpStatus.TEMPORARY_REDIRECT);
			response.getHeaders().setLocation(URI.create("/"));
		}
		return response.setComplete();
	}

}
