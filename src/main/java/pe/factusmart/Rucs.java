package pe.factusmart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** RUCs de la cuenta. Se usa como {@code fs.rucs}. Estos endpoints nunca reciben el RUC emisor. */
public final class Rucs {

    private final FactuSmart fs;

    Rucs(FactuSmart fs) {
        this.fs = fs;
    }

    /** {@code GET /rucs}. */
    public Map<String, Object> listar() {
        return fs.objeto(FactuSmart.Peticion.get("/rucs").reintentable(true));
    }

    /**
     * {@code POST /rucs} — datos: {@code ruc}, {@code empresa}, {@code correo}, {@code clave}.
     * Idempotente por RUC (si ya es tuyo devuelve {@code ya_existia: true}), por eso se reintenta.
     */
    public Map<String, Object> crear(Map<String, ?> datos) {
        if (datos == null) throw new FactuSmartException("rucs.crear(datos): pasa ruc, empresa, correo y clave.");
        return fs.objeto(FactuSmart.Peticion.post("/rucs", new LinkedHashMap<String, Object>(datos)).reintentable(true));
    }

    /** {@code PATCH /rucs/{ruc}/activation} — activa o desactiva un RUC. No se reintenta automáticamente. */
    public Map<String, Object> activar(String ruc, boolean activo) {
        String r = FactuSmart.segmento(ruc, "rucs.activar(ruc, activo): falta el RUC.");
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("activo", activo);
        return fs.objeto(FactuSmart.Peticion.patch("/rucs/" + r + "/activation", cuerpo).reintentable(false));
    }

    // ───────────── Configuración del RUC (certificado, SOL, entorno) ─────────────
    // El RUC va en la ruta: nunca se manda X-RUC ni ?ruc=. Las claves y el certificado nunca
    // aparecen en el mensaje de una excepción (se ocultan con ***).

    /**
     * {@code PUT /rucs/{ruc}/certificado} — instala el certificado digital (.pfx / .p12) leyendo el
     * archivo. El SDK lo manda en base64. El API comprueba que abra con la clave, que esté vigente y que
     * sea de este RUC. No se reintenta automáticamente.
     */
    public Map<String, Object> subirCertificado(String ruc, Path archivo, String clave) {
        if (archivo == null) throw new FactuSmartException("rucs.subirCertificado(ruc, archivo, clave): falta el archivo .pfx / .p12.");
        if (!Files.isRegularFile(archivo)) {
            throw new FactuSmartException("rucs.subirCertificado: no existe el archivo del certificado: " + archivo.toAbsolutePath());
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new FactuSmartException("rucs.subirCertificado: no se pudo leer el archivo del certificado: "
                    + archivo.toAbsolutePath(), 0, null, e);
        }
        return subirCertificado(ruc, bytes, clave);
    }

    /** Igual que {@link #subirCertificado(String, Path, String)}, con la ruta del archivo como texto. */
    public Map<String, Object> subirCertificado(String ruc, String rutaArchivo, String clave) {
        if (rutaArchivo == null || rutaArchivo.trim().isEmpty()) {
            throw new FactuSmartException("rucs.subirCertificado(ruc, archivo, clave): falta el archivo .pfx / .p12.");
        }
        return subirCertificado(ruc, Paths.get(rutaArchivo.trim()), clave);
    }

    /** Igual que {@link #subirCertificado(String, Path, String)}, con el contenido del .pfx en bytes. */
    public Map<String, Object> subirCertificado(String ruc, byte[] certificado, String clave) {
        String r = FactuSmart.segmento(ruc, "rucs.subirCertificado(ruc, archivo, clave): falta el RUC.");
        if (certificado == null || certificado.length == 0) {
            throw new FactuSmartException("rucs.subirCertificado: el certificado está vacío.");
        }
        if (clave == null || clave.isEmpty()) {
            throw new FactuSmartException("rucs.subirCertificado: falta la clave del certificado.");
        }
        String base64 = Base64.getEncoder().encodeToString(certificado);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("certificado", base64);
        cuerpo.put("clave", clave);
        return fs.objeto(FactuSmart.Peticion.put("/rucs/" + r + "/certificado", cuerpo)
                .secreto(clave).secreto(base64).reintentable(false));
    }

    /**
     * {@code PUT /rucs/{ruc}/sunat} — guarda el usuario secundario SOL y su clave y/o cambia el entorno
     * ({@code prueba} | {@code produccion}). Solo se mandan los valores no nulos; {@code usuarioSol} y
     * {@code claveSol} van juntos. No llama a SUNAT: para comprobar el SOL usa {@link #verificar(String)}.
     * No se reintenta automáticamente.
     */
    public Map<String, Object> configurarSunat(String ruc, String usuarioSol, String claveSol, String entorno) {
        String r = FactuSmart.segmento(ruc, "rucs.configurarSunat(ruc, usuarioSol, claveSol, entorno): falta el RUC.");
        boolean hayUsuario = usuarioSol != null && !usuarioSol.trim().isEmpty();
        boolean hayClave = claveSol != null && !claveSol.isEmpty();
        boolean hayEntorno = entorno != null && !entorno.trim().isEmpty();
        if (hayUsuario != hayClave) {
            throw new FactuSmartException("rucs.configurarSunat: usuarioSol y claveSol van juntos (pasa los dos o ninguno).");
        }
        if (!hayUsuario && !hayEntorno) {
            throw new FactuSmartException("rucs.configurarSunat: pasa usuarioSol y claveSol, o entorno (prueba | produccion), o los tres.");
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        if (hayUsuario) {
            cuerpo.put("usuario_sol", usuarioSol.trim());
            cuerpo.put("clave_sol", claveSol);
        }
        if (hayEntorno) cuerpo.put("entorno", entorno.trim());
        return fs.objeto(FactuSmart.Peticion.put("/rucs/" + r + "/sunat", cuerpo)
                .secreto(hayClave ? claveSol : null).reintentable(false));
    }

    /** {@code PUT /rucs/{ruc}/sunat} solo con el entorno ({@code prueba} | {@code produccion}). */
    public Map<String, Object> configurarSunat(String ruc, String entorno) {
        if (entorno == null || entorno.trim().isEmpty()) {
            throw new FactuSmartException("rucs.configurarSunat(ruc, entorno): falta el entorno (prueba | produccion).");
        }
        return configurarSunat(ruc, null, null, entorno);
    }

    /**
     * {@code POST /rucs/{ruc}/verificar} — prueba real del certificado y del usuario SOL contra SUNAT,
     * sin emitir nada ({@code listo_para_emitir}, {@code checks.certificado}, {@code checks.sol}). No
     * consume crédito.
     */
    public Map<String, Object> verificar(String ruc) {
        String r = FactuSmart.segmento(ruc, "rucs.verificar(ruc): falta el RUC.");
        return fs.objeto(FactuSmart.Peticion.post("/rucs/" + r + "/verificar", null).reintentable(true));
    }

    /**
     * {@code GET /rucs/{ruc}/configuracion} — entorno, certificado (vencimiento), usuario SOL
     * enmascarado, canal de envío y qué falta para producción ({@code faltantes}). No llama a SUNAT.
     */
    public Map<String, Object> configuracion(String ruc) {
        String r = FactuSmart.segmento(ruc, "rucs.configuracion(ruc): falta el RUC.");
        return fs.objeto(FactuSmart.Peticion.get("/rucs/" + r + "/configuracion").reintentable(true));
    }
}
