# Cambios

## 0.3.0 — 2026-11-10

Configuración del RUC por API (tag «Configuración del RUC» del openapi.yaml). El RUC va en la ruta:
ninguna de estas llamadas manda `X-RUC` ni `?ruc=`.

- `fs.rucs.subirCertificado(ruc, archivo, clave)` (`PUT /rucs/{ruc}/certificado`, operationId
  `uploadRucCertificate`): instala el .pfx / .p12. Acepta `Path`, la ruta como `String` o los
  `byte[]`; el SDK lo codifica en base64. Un archivo que no existe lanza `FactuSmartException` sin
  llamar al API. No se reintenta.
- `fs.rucs.configurarSunat(ruc, usuarioSol, claveSol, entorno)` y `configurarSunat(ruc, entorno)`
  (`PUT /rucs/{ruc}/sunat`, `updateRucSunat`): usuario secundario SOL + clave y/o entorno
  (`prueba` | `produccion`). Solo manda lo que no es `null`; `usuarioSol` y `claveSol` van juntos. No se
  reintenta.
- `fs.rucs.verificar(ruc)` (`POST /rucs/{ruc}/verificar`, `verifyRucConfig`): prueba el certificado y
  el SOL contra SUNAT sin emitir. Se reintenta como un GET.
- `fs.rucs.configuracion(ruc)` (`GET /rucs/{ruc}/configuracion`, `getRucConfig`).
- Seguridad: en las peticiones con claves, si la respuesta de error repite la clave, la clave SOL o el
  certificado, se reemplazan por `***` en el mensaje, en `getCampos()` y en `getRespuesta()`.
- `tests/fixtures/openapi.yaml` actualizado a la spec actual (30 operationId).

## 0.2.0 — 2026-11-06

- `fs.consultas.ruc(numero)`, `fs.consultas.dni(numero)` y `fs.consultas.documento(tipo, numero)`
  (`GET /consultas/{tipo}/{numero}`, operationId `lookupDocument`): nombre, dirección, estado,
  condición y ubigeo de un RUC o DNI, con `desde_cache`, `consultas_restantes`, `periodo` y
  `se_renueva`. Nunca mandan el RUC emisor. Un `tipo` que no sea `ruc`/`dni` lanza
  `ErrorDeValidacion` 422 `DATOS_INVALIDOS` sin hacer la petición.
- 429 `LIMITE_CONSULTAS` (`reintentable: false`) no se reintenta; `LIMITE_PETICIONES` y
  503 `CONSULTA_NO_DISPONIBLE` sí, como cualquier GET.
- `fs.raiz()` (`GET /`, operationId `apiRoot`): confirma que el API responde, sin llave ni RUC.
- `FactuSmartException.CODIGOS_DE_ERROR` suma `LIMITE_CONSULTAS` y `CONSULTA_NO_DISPONIBLE`.
- `tests/fixtures/openapi.yaml` actualizado a la spec actual.

## 0.1.0 — 2026-10-14

Primera versión. Mismo comportamiento que el SDK de Node 1.1.0 / PHP 0.2.0 / Python 0.2.0:

- Cliente `FactuSmart` con builder (`apiKey`, `baseUrl`, `ruc`, `timeout`, `maxReintentos`,
  `verificarSsl`, `transporte`). Java 11+, cero dependencias (`java.net.http.HttpClient` y JSON propio).
- Todos los operationId del API: `ping`, `cuenta.saldo`, `series.listar`, `comprobantes.*`
  (`emitir`, `listar`, `consultar`, `descargar`, `reenviar`, `consultarSunat`, `historial`,
  `calcular`), `anulaciones.*`, `guias.*`, `rucs.*` y `catalogos.*`.
- `comprobantes.descargar(id, "pdf", Opciones.conFormato("ticket"))`: PDF en `a4`, `a5`, `ticket`,
  `ticket80`, `ticket58` o `ticket50` (`?formato=`). Validación local sin petición: `formato` con
  xml/cdr o fuera de la lista → `ErrorDeValidacion` 422 `DATOS_INVALIDOS`.
- `establecimientos.listar(opciones)` (`GET /establecimientos`) y `establecimientos.crear(datos,
  opciones)` (`POST /establecimientos`, RUC en el cuerpo, nunca se reintenta).
- `catalogos.buscarProductoSunat(q, opciones)` (`GET /catalogos/productos-sunat`, opciones
  `limite` y `ruc`): código de producto SUNAT por texto o código.
- `Opciones` gana `formato(...)` / `Opciones.conFormato(...)` y `limite(...)`.
- `FactuSmart.calcularComprobante(items)` local con `BigDecimal` y `HALF_UP` (mismo resultado al
  centavo que los otros SDK) y `FactuSmart.verificarFirmaWebhook(cuerpoCrudo, firma, secreto)`.
- Errores no comprobados: `FactuSmartException` (con `getStatus`, `getMensaje`, `getCodigo`,
  `isReintentable`, `getCampos`, `getRespuesta`) y subclases por HTTP.
- `Idempotency-Key` siempre en `comprobantes.emitir`, reusada en los reintentos; `reintentable: false`
  nunca se reintenta (429 `LIMITE_PRUEBAS_DIARIO` tras una sola petición).
