package com.tecsup.gateway_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway (puerto 8080): punto de entrada único del sistema.
 * Recibe todas las peticiones del cliente y las enruta, según el Path,
 * al microservicio correspondiente usando lb://nombre-en-eureka.
 * Así el cliente no necesita conocer los puertos 8082-8085.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayServiceApplication.class, args);
	}

}
