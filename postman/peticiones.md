# Peticiones para Postman (vía API Gateway)

Todas las peticiones van al **gateway en el puerto 8080**. El gateway las enruta al
microservicio correspondiente usando Eureka, así que el cliente no necesita conocer los
puertos 8082 a 8085.
Header en POST/PUT: `Content-Type: application/json`.

## URLs de prueba por el gateway

| Servicio | URL |
|---|---|
| categoria-service | `GET http://localhost:8080/api/categorias` |
| categoria-service | `GET http://localhost:8080/api/categorias/1` |
| producto-service | `GET http://localhost:8080/api/productos` |
| producto-service | `GET http://localhost:8080/api/productos/1` |
| cliente-service | `GET http://localhost:8080/api/clientes` |
| cliente-service | `GET http://localhost:8080/api/clientes/1` |
| pedido-service | `GET http://localhost:8080/api/pedidos` |
| pedido-service | `GET http://localhost:8080/api/pedidos/1` |
| Ruta automática (discovery locator) | `GET http://localhost:8080/pedido-service/api/pedidos/1` |
| Rutas del gateway | `GET http://localhost:8080/actuator/gateway/routes` |
| Salud del gateway | `GET http://localhost:8080/actuator/health` |
| Panel de Eureka | `http://localhost:8761` |

## Flujo completo (ejecutar en orden)

Los ids de ejemplo suponen bases vacías. Si ya tienes datos, usa los ids que devuelva cada POST.

### 1. Crear categoría
`POST http://localhost:8080/api/categorias`
```json
{
  "nombre": "Computo",
  "estado": "ACTIVO"
}
```
Respuesta `201 Created`: `{"id":1,"nombre":"Computo","estado":"ACTIVO"}`

### 2. Crear producto
`POST http://localhost:8080/api/productos`
```json
{
  "nombre": "Laptop",
  "precio": 2500,
  "stock": 10,
  "categoriaId": 1
}
```
Respuesta `201 Created`. Incluye el objeto `categoria`, obtenido por Feign desde categoria-service.

### 3. Registrar cliente
`POST http://localhost:8080/api/clientes`
```json
{
  "nombre": "Carlos",
  "apellido": "Ramirez",
  "direccion": "Av. Los Olivos 123, Lima",
  "estado": "ACTIVO",
  "nroDni": "45678912"
}
```
Respuesta `201 Created`.

### 4. Registrar pedido
`POST http://localhost:8080/api/pedidos`
```json
{
  "clienteId": 1,
  "productoId": 1,
  "cantidad": 2
}
```
Respuesta `201 Created`, con estado inicial `PENDIENTE`.

### 5. Consultar pedido completo
`GET http://localhost:8080/api/pedidos/1`

Respuesta `200 OK`. Combina el **pedido** (bd_pedido_dev) con el **cliente** (cliente-service)
y el **producto** (producto-service):
```json
{
  "id": 1,
  "cantidad": 2,
  "cliente": { "id": 1, "nombre": "Carlos", "apellido": "Ramirez" },
  "producto": { "id": 1, "nombre": "Laptop", "precio": 2500 },
  "estado": "PENDIENTE"
}
```

## Cambios de estado del pedido (sin body)
- `PUT http://localhost:8080/api/pedidos/1/atender`
- `PUT http://localhost:8080/api/pedidos/1/anular`
- `PUT http://localhost:8080/api/pedidos/1/pendiente`

## Pruebas de error (el gateway reenvía la respuesta del servicio)
| Petición | Resultado esperado |
|---|---|
| `POST :8080/api/productos` con `"precio": 0` | 400, `"precio": "El precio debe ser mayor a cero"` |
| `POST :8080/api/productos` con `"categoriaId": 99` | 404, la categoría no existe en categoria-service |
| `POST :8080/api/clientes` con `"nroDni": "12ab"` | 400, el nro_dni debe tener 8 dígitos |
| `POST :8080/api/pedidos` con `{"cantidad": 0}` | 400 con errores en clienteId, productoId y cantidad |
| `GET :8080/api/pedidos/99` | 404, pedido no encontrado |
| `GET :8080/api/no-existe` | 404 del gateway: ninguna ruta coincide |
| Apagar cliente-service y `GET :8080/api/clientes` | 503, sin instancias disponibles |
