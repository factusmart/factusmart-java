package pe.factusmart;

import java.io.IOException;

/**
 * Lo que envía las peticiones HTTP. Por defecto el SDK usa {@code java.net.http.HttpClient}; puedes
 * inyectar el tuyo (proxies, pruebas sin red) con {@code FactuSmart.builder().transporte(...)}.
 *
 * <p>Debe devolver la respuesta para CUALQUIER código HTTP (también 4xx/5xx) y lanzar
 * {@link IOException} solo ante errores de red o timeout ({@link java.net.http.HttpTimeoutException}).
 */
@FunctionalInterface
public interface Transporte {
    RespuestaHttp enviar(SolicitudHttp solicitud) throws IOException, InterruptedException;
}
