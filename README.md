# factusmart — SDK de Java

SDK oficial del API de facturación electrónica de **FactuSmart** (SUNAT, Perú): emite facturas,
boletas, notas de crédito/débito y guías de remisión (GRE 2.0), anula facturas y boletas, calcula el
IGV y los totales, consulta los catálogos SUNAT, y descarga el PDF, el XML firmado y el CDR.

- Java 11 o superior. **Cero dependencias**: usa `java.net.http.HttpClient` y un lector JSON propio.
- Se usa igual que los SDK de Node, PHP y Python: mismos nombres de métodos, mismos errores.
- Documentación del API: <https://factusmart.pe/api-facturacion-electronica/docs>

## Instalar

Gradle:

```groovy
implementation 'pe.factusmart:factusmart:0.1.0'
```

Maven:

```xml
<dependency>
  <groupId>pe.factusmart</groupId>
  <artifactId>factusmart</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Ejemplo en 10 líneas

```java
import pe.factusmart.FactuSmart;
import pe.factusmart.Json;
import java.util.Map;

FactuSmart fs = FactuSmart.builder().apiKey("TU_API_KEY").baseUrl("https://TU_URL_BASE/api/v1/issuer").ruc("20123456789").build();

Map<String, Object> calc = FactuSmart.calcularComprobante(Json.lista(
    Json.objeto("descripcion", "Servicio de consultoría", "cantidad", 1, "valor_unitario", 100, "afectacion", "10")));

Map<String, Object> r = fs.comprobantes.emitir(Json.objeto(
    "codigo_tipo_documento", "01",
    "datos_del_cliente_o_receptor", Json.objeto("codigo_tipo_documento_identidad", "6", "numero_documento", "20555555555"),
    "items", calc.get("items"), "totales", calc.get("totales")));

System.out.println(r.get("aceptado_por_sunat") + " " + r.get("serie_numero"));
```

`baseUrl` **no es igual para todas las cuentas**: cada cuenta vive en un servidor distinto. Cópiala
de tu panel de integrador (pestaña Configuración, "URL base de la API").

Los datos van y vienen como `Map<String, Object>` / `List<Object>` con las claves del API tal cual
(en español con guion bajo). `Json.objeto(...)` y `Json.lista(...)` son atajos para armarlos; también
puedes pasar tus propios `Map` y `List`. En las respuestas, los enteros llegan como `Long` y los
decimales como `BigDecimal` (nunca `double`: son montos de dinero).

## La regla de oro: HTTP 200 no significa "aceptado por SUNAT"

Un comprobante puede generarse bien y aun así no ser aceptado (SUNAT caída, rechazo, observación).
Eso **no lanza excepción**: el SDK devuelve la respuesta tal cual y **tú** debes revisar:

```java
if (!Boolean.TRUE.equals(r.get("aceptado_por_sunat"))) {
    System.out.println(r.get("estado") + " " + r.get("sunat") + " " + r.get("accion_requerida"));
    // ej. "01" → reintenta con fs.comprobantes.reenviar((String) r.get("external_id"))
}
```

Revisa también `entorno` (`"prueba"` o `"produccion"`) y `tiene_validez_fiscal`: cada RUC nace en
modo prueba (SUNAT beta, sin validez fiscal).

## Opciones del constructor (`FactuSmart.builder()`)

| Opción | Obligatoria | Default | Nota |
|---|---|---|---|
| `apiKey(String)` | sí | — | Va en el header `X-API-Key`. |
| `baseUrl(String)` | sí | — | URL base de tu servidor (sin barra final; si la pones, se quita). |
| `ruc(String)` | no | — | RUC emisor por defecto. Se cambia por llamada con `Opciones.conRuc(...)`. |
| `timeout(int segundos)` / `timeout(Duration)` | no | `60` s | Por petición (SUNAT a veces tarda). |
| `maxReintentos(int)` | no | `3` | Ver "Reintentos e idempotencia". |
| `verificarSsl(boolean)` | no | `true` | `false` **solo** para pruebas locales con certificado propio. |
| `transporte(Transporte)` | no | `HttpClient` | Para inyectar tu propio transporte HTTP (proxies, pruebas). |

Si falta `apiKey` o `baseUrl`, `build()` lanza `FactuSmartException` con un mensaje claro. El cliente
es inmutable y seguro entre hilos: crea uno y reúsalo.

## Métodos

`opciones` es un `Opciones` (todos sus campos son opcionales):
`new Opciones().ruc("20...").idempotencyKey("venta-123")`, o los atajos `Opciones.conRuc(...)` /
`Opciones.conIdempotencyKey(...)`. Cada método tiene también la versión sin `opciones`.

| Método | Endpoint | Devuelve |
|---|---|---|
| `fs.ping(opciones)` | `GET /ping` | `Map` |
| `fs.cuenta.saldo()` | `GET /account` | `Map` (`creditos_disponibles`, plan, RUCs) |
| `fs.series.listar(opciones)` | `GET /series` | `Map` |
| `fs.comprobantes.emitir(datos, opciones)` | `POST /documents` | `Map` |
| `fs.comprobantes.listar(filtros, opciones)` | `GET /documents` | `Map` (`comprobantes`, `siguiente_cursor`) |
| `fs.comprobantes.consultar(externalId, opciones)` | `GET /documents/{id}` | `Map` |
| `fs.comprobantes.descargar(externalId, "pdf" \| "xml" \| "cdr", opciones)` | `GET /documents/{id}/{tipo}` | **`byte[]`** |
| `fs.comprobantes.descargar(externalId, "pdf", Opciones.conFormato("ticket"))` | `GET /documents/{id}/pdf?formato=ticket` | **`byte[]`** (PDF en ticket/A5) |
| `fs.comprobantes.reenviar(externalId, opciones)` | `POST /documents/{id}/reenviar` | `Map` |
| `fs.comprobantes.consultarSunat(externalId, opciones)` | `POST /documents/{id}/consultar-sunat` | `Map` |
| `fs.comprobantes.historial(externalId, opciones)` | `GET /documents/{id}/historial` | `Map` |
| `fs.comprobantes.calcular(items, new Opciones().porcentajeIgv(10.5))` | `POST /calcular` | `Map` (`items`, `totales`) |
| `fs.anulaciones.crear(datos, opciones)` | `POST /anulaciones` | `Map` (`tipo`, `identificador`, `ticket`) |
| `fs.anulaciones.estado(ticket, opciones)` | `GET /anulaciones/{ticket}/status` | `Map` (`tipo`, `estado`, `documentos`) |
| `fs.catalogos.listar(opciones)` | `GET /catalogos` | `Map` (`catalogos`, `nota`) |
| `fs.catalogos.consultar(nombre, new Opciones().q("kilo"))` | `GET /catalogos/{nombre}` | `Map` (`catalogo`, `total`, `datos`) |
| `fs.catalogos.buscarProductoSunat(q, new Opciones().limite(10))` | `GET /catalogos/productos-sunat` | `Map` (`total`, `hay_mas`, `datos`) |
| `fs.establecimientos.listar(opciones)` | `GET /establecimientos` | `Map` (`establecimientos`) |
| `fs.establecimientos.crear(datos, opciones)` | `POST /establecimientos` | `Map` (`establecimiento`) |
| `fs.guias.emitir(datos, opciones)` | `POST /guias` | `Map` (revisa `enviado`) |
| `fs.guias.estado(externalId, opciones)` | `GET /guias/{id}/status` | `Map` |
| `fs.rucs.listar()` | `GET /rucs` | `Map` |
| `fs.rucs.crear(Json.objeto("ruc", ..., "empresa", ..., "correo", ..., "clave", ...))` | `POST /rucs` | `Map` (`ya_existia`) |
| `fs.rucs.activar(ruc, activo)` | `PATCH /rucs/{ruc}/activation` | `Map` |

Ayudantes estáticos: `FactuSmart.calcularComprobante(items)` y
`FactuSmart.verificarFirmaWebhook(cuerpoCrudo, firma, secreto)`.

### Lo que el SDK completa por ti

- **RUC:** en GET va como `?ruc=`; en POST va en el cuerpo como `ruc` (si ya lo pusiste en tus datos, se respeta el tuyo). Los endpoints `/rucs*` nunca lo reciben.
- **`numero_documento`:** si no lo mandas, va `"#"` (FactuSmart asigna el correlativo). Si lo mandas, se respeta (numeración propia).
- **`fecha_de_emision` / `hora_de_emision`:** si no las mandas, se usan la fecha y hora **de Perú (America/Lima)**, no las de tu servidor.
- **`codigo_tipo_moneda`:** `"PEN"` si no viene. **`codigo_tipo_operacion`:** `"0101"` si no viene, solo en facturas y boletas (01/03); en notas no aplica.

Tu `Map` nunca se modifica: el SDK trabaja sobre una copia.

### Descargar archivos

```java
byte[] pdf = fs.comprobantes.descargar((String) r.get("external_id"), "pdf");
java.nio.file.Files.write(java.nio.file.Paths.get("comprobante.pdf"), pdf);
```

El CDR solo existe cuando SUNAT respondió; si no existe, se lanza `NoEncontrado`.

**PDF en ticket u otro tamaño** (el mismo que imprime el sistema web):

```java
byte[] ticket = fs.comprobantes.descargar(externalId, "pdf", Opciones.conFormato("ticket")); // 80 mm
byte[] t58 = fs.comprobantes.descargar(externalId, "pdf", new Opciones().formato("ticket58"));
```

| `formato` | Tamaño |
|---|---|
| *(sin formato)* / `a4` | A4 |
| `a5` | A5 apaisado |
| `ticket` / `ticket80` | Ticket de 80 mm |
| `ticket58` | Ticket de 58 mm |
| `ticket50` | Ticket de 50 mm |

Sin `formato` el SDK no manda el parámetro (A4 de siempre). `formato` con `"xml"`/`"cdr"` o un valor
fuera de la tabla lanza `ErrorDeValidacion` (422, `DATOS_INVALIDOS`, `getCampos().get("formato")`)
**sin hacer la petición**. No cambia el PDF que recibe tu cliente final.

### Locales (anexos)

```java
Map<String, Object> l = fs.establecimientos.listar(); // cada uno con codigo_anexo, predeterminado y series
Map<String, Object> nuevo = fs.establecimientos.crear(Json.objeto(
    "codigo_anexo", "0001", "nombre", "Tienda Miraflores",
    "direccion", "Av. Larco 123", "ubigeo", "150122"));
```

`crear` nunca se reintenta automáticamente (no es idempotente): si falla por red, revisa con
`listar()` antes de volver a llamarlo. Si tu plan no admite más locales lanza `RucNoAutorizado` con
`getCodigo()` = `LIMITE_ESTABLECIMIENTOS`. Editar o eliminar un local, solo desde el sistema web.

### Anular facturas y boletas

`anulaciones.crear` sirve para las dos; el API elige el camino según lo que mandes:

| Mandas | SUNAT lo anula con | `tipo` | `identificador` |
|---|---|---|---|
| Facturas y sus notas | Comunicación de baja | `baja` | `RA-AAAAMMDD-N` |
| Boletas y sus notas (solo **aceptadas**, estado `05`) | Resumen diario | `resumen` | `RC-AAAAMMDD-N` |
| Las dos cosas juntas | — | `422` `ANULACION_MIXTA` (`ErrorDeValidacion`) | — |

```java
Map<String, Object> a = fs.anulaciones.crear(Json.objeto(
    "fecha_de_emision_de_documentos", "14-10-2026", // DD-MM-AAAA, la de emisión de los comprobantes
    "documentos", Json.lista(Json.objeto("external_id", r.get("external_id"), "motivo_anulacion", "Error en el comprobante"))));
System.out.println(a.get("tipo") + " " + a.get("identificador") + " " + a.get("ticket"));
Map<String, Object> e = fs.anulaciones.estado((String) a.get("ticket")); // estado "05" = aceptada
```

La respuesta trae también `creditos_restantes` y `credito_cobrado` (y en RUCs demo `modo_demo` y
`pruebas_restantes_hoy`). Haz una petición para las facturas y otra para las boletas.
`anulaciones.crear` nunca se reintenta automáticamente.

### Catálogos SUNAT

```java
Map<String, Object> l = fs.catalogos.listar(); // unidades, afectacion-igv, monedas, ...
Map<String, Object> u = fs.catalogos.consultar("unidades", new Opciones().q("kilo")); // o por número: consultar("03")
// usa solo las filas con "activo": true: el RUC valida contra ellas

// Código de producto SUNAT (catálogo 25), por texto o por código (exacto o prefijo):
Map<String, Object> p = fs.catalogos.buscarProductoSunat("computador", new Opciones().limite(10)); // máx. 50
// p.get("datos") → [{codigo, descripcion, nivel, categoria}, ...]; "hay_mas": true = afina la búsqueda
// el código va en "codigo_producto_sunat" de cada ítem al emitir
```

`consultar` lee el catálogo del RUC (`ruc` de la llamada o del constructor). Un nombre que no existe
lanza `NoEncontrado` (la lista válida viene en `e.getRespuesta()`, clave `catalogos_disponibles`).

## ¿Calcular en el servidor o local?

| | `fs.comprobantes.calcular(items, opciones)` | `FactuSmart.calcularComprobante(items)` |
|---|---|---|
| Dónde | En FactuSmart (`POST /calcular`) | En tu proceso, sin petición |
| Precio | `valor_unitario` (sin IGV) **o** `precio_unitario` (con IGV) | Solo `valor_unitario` (sin IGV) |
| IGV | 18 % o 10.5 % (`new Opciones().porcentajeIgv(10.5)`, Ley 31556, restaurantes y hoteles MYPE acogidos) | Solo 18 % |
| Costo | No emite ni cobra crédito (ni manda `ruc`) | Cero peticiones |

Para lo que soportan los dos, dan **los mismos números** al centavo.

- Usa `comprobantes.calcular` si tus precios incluyen IGV, si aplicas la tasa 10.5 % o si prefieres
  que el cálculo lo haga siempre FactuSmart. Con `precio_unitario` se respeta lo que paga el cliente:
  3 menús de S/ 10.00 suman S/ 30.00 (no S/ 29.99).
- Usa `calcularComprobante` si tus precios son sin IGV al 18 % y no quieres una petición extra.

```java
Map<String, Object> c = fs.comprobantes.calcular(Json.lista(
    Json.objeto("descripcion", "Menú", "cantidad", 3, "precio_unitario", 10, "afectacion", "10", "unidad_de_medida", "ZZ")));
fs.comprobantes.emitir(Json.objeto("codigo_tipo_documento", "03", "datos_del_cliente_o_receptor", cliente,
    "items", c.get("items"), "totales", c.get("totales")));
```

Los errores de `comprobantes.calcular` vienen por ítem en `e.getCampos()` (clave `items.N.campo`). Al
no tener efectos, se reintenta igual que un GET.

## `calcularComprobante(items)`

Recibe ítems (`Map`) con `descripcion`, `cantidad`, `valor_unitario` (**sin IGV**), `afectacion` y
opcionalmente `codigo_interno` y `unidad_de_medida` (default `NIU`). Devuelve un `Map` con `items` y
`totales` listos para emitir; los montos son `BigDecimal`.

| `afectacion` | Significado | IGV | Suma en |
|---|---|---|---|
| `10` | Gravado | 18% | `total_operaciones_gravadas` |
| `20` | Exonerado | 0 | `total_operaciones_exoneradas` |
| `30` | Inafecto | 0 | `total_operaciones_inafectas` |
| `40` | Exportación | 0 | `total_exportacion` |

Redondeo a 2 decimales por ítem, "mitad hacia arriba" (`BigDecimal` + `HALF_UP`, sin el error de
`double`): `10.555 × 3` da `31.67`, igual que en los SDK de Node, PHP y Python. Puedes pasar los
números como `int`, `double`, `BigDecimal` o texto. Un ítem inválido lanza `IllegalArgumentException`.

## Errores

Todas son **no comprobadas** (no te obligan a `try/catch`) y heredan de `FactuSmartException`:

- `getStatus()`: HTTP (`0` si fue red/timeout).
- `getMensaje()`: el `message` del API. `getRespuesta()`: el cuerpo completo (`Map`, texto o `null`).
- `getCodigo()`: código estable del error, en MAYÚSCULAS (ej. `DATOS_INVALIDOS`). Úsalo en tu lógica
  en vez de comparar textos. `null` si fue red/timeout o si el servidor es de una versión anterior.
- `isReintentable()`: `true` solo si el API dijo que repetir la misma petición más tarde puede
  funcionar. `getReintentable()` da el valor crudo (`null` si el API no lo mandó).
- `getCampos()`: errores por campo, ej. `{items.0.cantidad=[...]}` (sobre todo en 422). Vacío si no hay.

| HTTP | Clase | Cuándo |
|---|---|---|
| 401 | `ErrorDeAutenticacion` | API key ausente, inválida o cuenta desactivada |
| 402 | `SinCreditos` | Sin créditos; no se emitió nada |
| 403 | `RucNoAutorizado` | El RUC no es de tu cuenta o está desactivado |
| 404 | `NoEncontrado` | Comprobante, guía, ticket, catálogo o archivo inexistente |
| 422 | `ErrorDeValidacion` | Datos inválidos; el crédito no se cobra. Revisa `getCampos()` |
| 429 | `LimiteDePeticiones` | Límite de peticiones; `getSegundosDeEspera()` si se pudo leer |
| otros / red | `FactuSmartException` | |

```java
try {
    fs.comprobantes.emitir(datos);
} catch (ErrorDeValidacion e) {
    System.out.println("Corrige el JSON: " + e.getMensaje() + " " + e.getCampos());
} catch (SinCreditos e) {
    System.out.println("Recarga créditos");
} catch (FactuSmartException e) {
    if ("PLAN_VENCIDO".equals(e.getCodigo())) System.out.println("Renueva tu plan");
    else throw e;
}
```

Códigos más comunes (lista completa en `FactuSmartException.CODIGOS_DE_ERROR`; el API puede agregar
otros y manda `ERROR_<status>` como genérico, así que no asumas una lista cerrada):

| `codigo` | Qué pasó |
|---|---|
| `NO_AUTENTICADO` | API key inválida o cuenta desactivada |
| `PLAN_VENCIDO` / `SIN_CREDITOS` | Plan vencido o sin créditos |
| `RUC_NO_AUTORIZADO` / `RUC_DESACTIVADO` | El RUC no es de tu cuenta o está desactivado |
| `NO_ENCONTRADO` | Comprobante, guía, ticket o archivo inexistente |
| `DATOS_INVALIDOS` | Validación; detalle en `getCampos()` |
| `EMISION_EN_CURSO` | Ya hay una emisión con esa `Idempotency-Key` en proceso |
| `RECHAZADO_SUNAT` / `ERROR_EMISION` | SUNAT rechazó o falló la emisión |
| `SUNAT_NO_DISPONIBLE` | SUNAT no responde (reintentable) |
| `LIMITE_PETICIONES` | Demasiadas peticiones por minuto (reintentable) |
| `LIMITE_PRUEBAS_DIARIO` | 429 **no reintentable**: tope de 200 operaciones/día en RUCs demo |
| `ANULACION_MIXTA` | Facturas y boletas en la misma anulación (422) |
| `LLAVE_DE_PRUEBA_EN_PRODUCCION` | Llave de prueba usada con un RUC en producción (403) |
| `ERROR_INTERNO` | Error del servidor |

### RUCs demo: sin costo, con tope diario

En un RUC en modo demo (pruebas, SUNAT beta, sin validez fiscal) **no se cobra crédito**: las respuestas
traen `credito_cobrado: false`, `modo_demo: true` y `pruebas_restantes_hoy`, y `creditos_restantes` no
baja. A cambio hay un tope de **200 operaciones por día por cuenta**; al llegar, el API responde 429
con `codigo: "LIMITE_PRUEBAS_DIARIO"` y `reintentable: false`. El SDK lanza `LimiteDePeticiones` tras
**una sola** petición (no reintenta: el cupo se reinicia a medianoche, hora de Perú). Distínguelo del
límite por minuto con `getCodigo()`, no con la clase:

```java
catch (LimiteDePeticiones e) {
    if ("LIMITE_PRUEBAS_DIARIO".equals(e.getCodigo())) System.out.println("Se acabaron las pruebas de hoy");
}
```

## Verificar webhooks

FactuSmart firma cada webhook con el header `X-FactuSmart-Firma`: HMAC-SHA256 (hex) del cuerpo
**crudo** con tu secreto. Verifica con el cuerpo tal como llegó (bytes o texto), **nunca** con un JSON
que hayas vuelto a serializar: cambian espacios u orden y la firma ya no coincide.

```java
// Spring: @PostMapping("/webhooks/factusmart")
public ResponseEntity<Void> webhook(@RequestBody byte[] cuerpo, @RequestHeader("X-FactuSmart-Firma") String firma) {
    if (!FactuSmart.verificarFirmaWebhook(cuerpo, firma, System.getenv("FACTUSMART_WEBHOOK_SECRETO"))) {
        return ResponseEntity.status(401).build();
    }
    Map<String, Object> evento = Json.leerObjeto(new String(cuerpo, StandardCharsets.UTF_8));
    // ... procesa evento
    return ResponseEntity.ok().build();
}
```

`verificarFirmaWebhook(cuerpoCrudo, firma, secreto)` acepta `String` o `byte[]`, compara en tiempo
constante y nunca lanza: si la firma o el secreto faltan o están mal formados, devuelve `false`.

## Reintentos e idempotencia

- `comprobantes.emitir` **siempre** manda el header `Idempotency-Key`: la tuya
  (`Opciones.conIdempotencyKey("venta-123")`, recomendado: el id de la venta en tu sistema) o un UUID v4
  generado. **La misma clave se reusa en todos los reintentos de esa llamada**, así un reintento nunca
  emite dos veces ni cobra dos veces.
- Se reintenta solo ante: error de red/timeout, 429, 502, 503, 504 y, en `emitir`, 409 ("emisión en
  curso con esa clave"). Espera creciente: 1 s, 2 s, 4 s… Ante límite de peticiones se respeta
  `Retry-After` o el "Espera N segundos" del mensaje.
- Se reintentan: todos los GET, `comprobantes.emitir`, `comprobantes.calcular` (no tiene efectos) y
  `rucs.crear` (idempotente por RUC). En estas operaciones también se reintenta cuando el API responde
  `reintentable: true` (ej. `ERROR_INTERNO`, `SUNAT_NO_DISPONIBLE`), con el mismo tope y la misma espera.
- **`reintentable: false` manda:** si el cuerpo del error trae `reintentable: false`, no se reintenta
  nunca, aunque el HTTP sea 429, 409, 502, 503 o 504 (caso real: 429 `LIMITE_PRUEBAS_DIARIO`). Si el
  cuerpo no trae `reintentable` (servidor de versión anterior), se usa la regla por HTTP de arriba.
- **Nunca** se reintentan automáticamente: `anulaciones.crear`, `guias.emitir` (no acepta
  `Idempotency-Key`), `comprobantes.reenviar`, `comprobantes.consultarSunat`, `rucs.activar`,
  aunque el API responda `reintentable: true`.

## Pruebas locales con certificado propio (`verificarSsl(false)`)

Con `verificarSsl(false)` el cliente acepta cualquier certificado **solo en esa instancia** (no cambia
nada global de la JVM). Úsalo únicamente contra tu entorno local, nunca en producción.

## Desarrollo del SDK

```bash
./gradlew test -PsoloUnitarias   # sin red (transporte falso) + prueba de contrato contra openapi.yaml
./gradlew test                   # todo, incluido el API local https://factusmart.test (máx. 3 emisiones demo, sin costo)
./gradlew casoComun              # regenera resultado_caso_comun.json
./gradlew build                  # jar, sources y javadoc en build/libs
```

Las pruebas del API local usan las variables `FACTUSMART_BASE_URL`, `FACTUSMART_API_KEY`,
`FACTUSMART_RUC` y `FACTUSMART_OPENAPI` (ruta del `openapi.yaml`; por defecto
`tests/fixtures/openapi.yaml`) si están definidas.

## Licencia

MIT
