package pe.factusmart;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

/** Transporte por defecto: {@code java.net.http.HttpClient} (Java 11+), sin dependencias. */
final class TransporteHttpClient implements Transporte {

    private final HttpClient cliente;

    TransporteHttpClient(Duration timeout, boolean verificarSsl) {
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (!verificarSsl) b.sslContext(contextoSinVerificar());
        this.cliente = b.build();
    }

    @Override
    public RespuestaHttp enviar(SolicitudHttp s) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher cuerpo = s.getCuerpo() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(s.getCuerpo());
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(s.getUrl()))
                .timeout(s.getTimeout())
                .method(s.getMetodo(), cuerpo);
        for (Map.Entry<String, String> e : s.getCabeceras().entrySet()) r.header(e.getKey(), e.getValue());
        HttpResponse<byte[]> resp = cliente.send(r.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new RespuestaHttp(resp.statusCode(), resp.headers().map(), resp.body());
    }

    /**
     * Acepta cualquier certificado y no comprueba el nombre del host. SOLO para pruebas locales con
     * certificado propio ({@code verificarSsl(false)}). Con un {@link X509ExtendedTrustManager} la
     * comprobación del host la hace el propio administrador de confianza, así que no se toca ninguna
     * propiedad global de la JVM.
     */
    private static SSLContext contextoSinVerificar() {
        TrustManager confiarEnTodo = new X509ExtendedTrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] c, String a) {}
            @Override public void checkServerTrusted(X509Certificate[] c, String a) {}
            @Override public void checkClientTrusted(X509Certificate[] c, String a, Socket s) {}
            @Override public void checkServerTrusted(X509Certificate[] c, String a, Socket s) {}
            @Override public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) {}
            @Override public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] {confiarEnTodo}, new SecureRandom());
            return ctx;
        } catch (GeneralSecurityException e) {
            throw new FactuSmartException("No se pudo preparar la conexión sin verificar SSL: " + e.getMessage());
        }
    }
}
