# Cambios

## 0.1.0 — 2026-10-14

Primera versión. Mismo comportamiento que el SDK de Node 1.1.0 / PHP 0.2.0 / Python 0.2.0:

- Cliente `FactuSmart` con builder (`apiKey`, `baseUrl`, `ruc`, `timeout`, `maxReintentos`,
  `verificarSsl`, `transporte`). Java 11+, cero dependencias (`java.net.http.HttpClient` y JSON propio).
- Todos los operationId del API: `ping`, `cuenta.saldo`, `series.listar`, `comprobantes.*`
  (`emitir`, `listar`, `consultar`, `descargar`, `reenviar`, `consultarSunat`, `historial`,
  `calcular`), `anulaciones.*`, `guias.*`, `rucs.*` y `catalogos.*`.
- `FactuSmart.calcularComprobante(items)` local con `BigDecimal` y `HALF_UP` (mismo resultado al
  centavo que los otros SDK) y `FactuSmart.verificarFirmaWebhook(cuerpoCrudo, firma, secreto)`.
- Errores no comprobados: `FactuSmartException` (con `getStatus`, `getMensaje`, `getCodigo`,
  `isReintentable`, `getCampos`, `getRespuesta`) y subclases por HTTP.
- `Idempotency-Key` siempre en `comprobantes.emitir`, reusada en los reintentos; `reintentable: false`
  nunca se reintenta (429 `LIMITE_PRUEBAS_DIARIO` tras una sola petición).
