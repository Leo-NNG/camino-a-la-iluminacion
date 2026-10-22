package pe.caminoiluminacion.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Limita los intentos fallidos de inicio de sesión (fuerza bruta). Como el DNI es público
 * y tiene solo 8 dígitos, sin este límite se podrían probar contraseñas sin freno.
 * La clave combina IP + DNI para que un tercero no pueda bloquear a otra persona desde
 * otra red.
 *
 * El estado vive en una caché de Google Guava: las entradas que nadie vuelve a tocar
 * caducan solas (ventana + bloqueo) y el tamaño total tiene tope, así que no hace falta
 * "limpiar" a mano y un atacante no puede llenar la memoria con claves inventadas.
 * Es en memoria: se reinicia al reiniciar el servidor (suficiente para esta etapa).
 */
public class LimiteIntentos {

    private static final long MAX_CLAVES = 10_000;

    private static final class Estado {
        int fallos;
        long inicioVentana;
        long bloqueadoHasta;
    }

    private final int maxFallos;
    private final long ventanaMs;
    private final long bloqueoMs;
    private final LongSupplier reloj;
    private final Cache<String, Estado> estados;

    public LimiteIntentos() {
        this(5, 15 * 60_000L, 5 * 60_000L, System::currentTimeMillis);
    }

    public LimiteIntentos(int maxFallos, long ventanaMs, long bloqueoMs, LongSupplier reloj) {
        Preconditions.checkArgument(maxFallos > 0, "maxFallos debe ser positivo");
        Preconditions.checkArgument(ventanaMs > 0 && bloqueoMs > 0, "ventana y bloqueo deben ser positivos");
        this.maxFallos = maxFallos;
        this.ventanaMs = ventanaMs;
        this.bloqueoMs = bloqueoMs;
        this.reloj = reloj;
        // El reloj inyectado también mueve la caché: así las pruebas controlan el tiempo sin dormir.
        Ticker ticker = new Ticker() {
            @Override public long read() { return TimeUnit.MILLISECONDS.toNanos(reloj.getAsLong()); }
        };
        this.estados = CacheBuilder.newBuilder()
                .maximumSize(MAX_CLAVES)
                .expireAfterAccess(ventanaMs + bloqueoMs, TimeUnit.MILLISECONDS)
                .ticker(ticker)
                .build();
    }

    public synchronized boolean estaBloqueado(String clave) {
        Estado e = estados.getIfPresent(clave);
        return e != null && e.bloqueadoHasta > reloj.getAsLong();
    }

    public synchronized void registrarFallo(String clave) {
        long ahora = reloj.getAsLong();
        Estado e = estados.getIfPresent(clave);
        if (e == null) {
            e = new Estado();
            estados.put(clave, e);
        }
        if (e.fallos == 0 || ahora - e.inicioVentana > ventanaMs) {
            e.fallos = 0;
            e.inicioVentana = ahora;
        }
        e.fallos++;
        if (e.fallos >= maxFallos) {
            e.bloqueadoHasta = ahora + bloqueoMs;
            e.fallos = 0;
        }
    }

    public synchronized void reiniciar(String clave) {
        estados.invalidate(clave);
    }
}
