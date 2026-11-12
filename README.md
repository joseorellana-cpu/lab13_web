# Microservicios: Service Discovery y API Gateway

Sistema de pedidos con **seis proyectos Spring Boot**. Todos los clientes externos (Postman,
frontend) entran por un único punto, el **API Gateway (8080)**. El gateway consulta a
**Eureka (8761)** y enruta cada petición al microservicio que corresponde. Cada
microservicio tiene su propia base de datos MySQL y se comunica con los demás **solo por REST
mediante OpenFeign**, usando el nombre lógico registrado en Eureka.

## Arquitectura

```
                         Cliente (Postman / Frontend)
                                     │  http://localhost:8080/api/...
                                     ▼
                       ┌───────────────────────────┐
                       │   gateway-service :8080   │  enrutamiento por Path + lb://
                       └─────────────┬─────────────┘
                                     │ consulta instancias
                       ┌─────────────▼─────────────┐
                       │   eureka-server  :8761    │  registro y descubrimiento
                       └─────────────▲─────────────┘
              ┌──────────────┬───────┴───────┬──────────────┐  (todos se registran)
              │              │               │              │
     categoria-service  producto-service  cliente-service  pedido-service
          :8082     ◄─Feign─   :8083          :8084     ─Feign─►  :8085
     bd_categoria_dev   bd_producto_dev   bd_cliente_dev    bd_pedido_dev
                                                  ▲               │
                                                  └─────Feign─────┘ (cliente y producto)
```

| Componente | Rol |
|---|---|
| **Eureka Server** | Registro de servicios. Cada instancia se registra con su `spring.application.name`. |
| **API Gateway** | Punto de entrada único. Enruta según el `Path` hacia `lb://nombre-servicio`, y Spring Cloud LoadBalancer elige la instancia. |
| **Microservicios** | Lógica de negocio y persistencia propias (*database per service*). |
| **OpenFeign** | Comunicación interna entre servicios por nombre lógico. |

## Tecnologías
- Java 17, Spring Boot 3.5.14, Spring Cloud 2025.0.0
- Spring Cloud Gateway (WebFlux), Spring Cloud LoadBalancer, Spring Boot Actuator
- Netflix Eureka Server / Client, Spring Cloud OpenFeign
- Spring Web, Spring Data JPA, Bean Validation, Lombok
- MySQL 8 (también probado con MariaDB 10.4 de XAMPP)
- Maven (se incluye `mvnw` en cada proyecto)

## Requisitos
- JDK 17 o superior (probado con JDK 21)
- MySQL en `localhost:3306`, usuario `root` y contraseña vacía
- Puertos libres: 8761, 8080 y 8082 a 8085
- Postman (o curl)

## Servicios y puertos
| Servicio | Puerto | Base de datos | Consume a |
|---|---|---|---|
| eureka-server | 8761 | — | — |
| gateway-service | **8080** | — | todos (vía `lb://`) |
| categoria-service | 8082 | bd_categoria_dev | — |
| producto-service | 8083 | bd_producto_dev | categoria-service |
| cliente-service | 8084 | bd_cliente_dev | — |
| pedido-service | 8085 | bd_pedido_dev | cliente-service, producto-service |

## Puesta en marcha
1. Crear las bases de datos:
   ```bash
   mysql -u root < database/crear_bases.sql
   ```
2. Levantar los servicios **en este orden**, cada uno en su propia terminal (o con Run en el IDE):
   1. `eureka-server`
   2. `categoria-service`
   3. `producto-service`
   4. `cliente-service`
   5. `pedido-service`
   6. `gateway-service`
   ```bash
   cd eureka-server && ./mvnw spring-boot:run
   ```
3. Abrir http://localhost:8761 y comprobar que aparecen las **5 instancias**: CATEGORIA, PRODUCTO,
   CLIENTE, PEDIDO y GATEWAY.
4. Esperar unos 30 segundos tras el registro: el gateway refresca su caché de instancias
   periódicamente. Si se adelanta, responderá `503`.

## Rutas expuestas por el gateway (puerto 8080)

### Rutas explícitas (`spring.cloud.gateway.routes`)
| Id | Predicado `Path` | Destino |
|---|---|---|
| categoria-service | `/api/categorias/**` | `lb://categoria-service` |
| producto-service | `/api/productos/**` | `lb://producto-service` |
| cliente-service | `/api/clientes/**` | `lb://cliente-service` |
| pedido-service | `/api/pedidos/**` | `lb://pedido-service` |

### Rutas automáticas (discovery locator)
Con `discovery.locator.enabled: true` y `lower-case-service-id: true`, cada servicio de Eureka
también responde con su nombre como prefijo:
`http://localhost:8080/{nombre-servicio}/...`, por ejemplo
`http://localhost:8080/pedido-service/api/pedidos/1`.

### Endpoints de negocio (todos vía `http://localhost:8080`)
| Servicio | Método | Ruta |
|---|---|---|
| categoria | POST, GET | `/api/categorias` |
| categoria | GET, PUT, DELETE | `/api/categorias/{id}` |
| producto | POST, GET | `/api/productos` |
| producto | GET, PUT, DELETE | `/api/productos/{id}` |
| cliente | POST, GET | `/api/clientes` |
| cliente | GET, PUT, DELETE | `/api/clientes/{id}` |
| pedido | POST, GET | `/api/pedidos` |
| pedido | GET | `/api/pedidos/{id}` (pedido + cliente + producto) |
| pedido | PUT | `/api/pedidos/{id}/anular`, `/pendiente`, `/atender` |

Los pedidos no se eliminan: no existe DELETE.

### Monitoreo del gateway (Actuator)
- `GET http://localhost:8080/actuator/health`: estado del gateway
- `GET http://localhost:8080/actuator/gateway/routes`: rutas cargadas (explícitas y automáticas)

Las URLs de prueba y los bodies de Postman están en [postman/peticiones.md](postman/peticiones.md).

## Manejo de errores
Cada microservicio tiene un `@RestControllerAdvice` global. El gateway reenvía las respuestas
sin modificarlas:

| Código | Caso |
|---|---|
| 400 | Validación (`@Valid`), JSON mal formado o parámetro inválido |
| 404 | Recurso inexistente (local o remoto), o ruta no publicada en el gateway |
| 405 | Método no permitido (ej. DELETE de pedidos) |
| 409 | DNI de cliente duplicado |
| 503 | Servicio caído o aún no registrado en Eureka |

## Estructura de paquetes
```
com.tecsup.<servicio>
├── model        entidades JPA
├── dto          DTOs (datos remotos y respuesta de error)
├── repository   interfaces JpaRepository
├── services     lógica de negocio
├── controller   controladores REST
├── client       interfaces @FeignClient (solo producto y pedido)
└── exception    excepción 404 y @RestControllerAdvice

com.tecsup.gateway_service
└── GatewayServiceApplication   (toda la configuración está en application.yaml)
```

## Notas
- **Nombres en Spring Cloud 2025.0.0:** al arrancar, el gateway muestra avisos de obsolescencia.
  El laboratorio usa `spring-cloud-starter-gateway` y las claves `spring.cloud.gateway.*`, que
  siguen funcionando porque el Gateway las traduce automáticamente. Sus equivalentes nuevos son
  `spring-cloud-starter-gateway-server-webflux` y `spring.cloud.gateway.server.webflux.*`.
- Con MariaDB (XAMPP), Hibernate muestra el aviso `HHH000511` sobre la versión 5.5.5. No afecta
  al laboratorio.
- Las pruebas `*ApplicationTests` levantan el contexto completo y necesitan MySQL encendido.
  Para compilar sin ejecutarlas: `./mvnw package -DskipTests`.
