package pe.caminoiluminacion.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Limita los intentos fallidos de inicio de sesión (fuerza bruta). Como el DNI es público
 * y tiene solo 8 dígitos, sin este límite se podrían probar contraseñas sin freno.
 * La clave combina IP + DNI para que un tercero no pueda bloquear a otra persona desde
 * otra red. Es en memoria: se reinicia al reiniciar el servidor (suficiente para esta etapa).
 */
public class LimiteIntentos {

    private static final class Estado {
        int fallos;
        long inicioVentana;
        long bloqueadoHasta;
    }

    private final int maxFallos;
    private final long ventanaMs;
    private final long bloqueoMs;
    private final LongSupplier reloj;
    private final Map<String, Estado> estados = new HashMap<>();

    public LimiteIntentos() {
        this(5, 15 * 60_000L, 5 * 60_000L, System::currentTimeMillis);
    }

    public LimiteIntentos(int maxFallos, long ventanaMs, long bloqueoMs, LongSupplier reloj) {
        this.maxFallos = maxFallos;
        this.ventanaMs = ventanaMs;
        this.bloqueoMs = bloqueoMs;
        this.reloj = reloj;
    }

    public synchronized boolean estaBloqueado(String clave) {
        Estado e = estados.get(clave);
        return e != null && e.bloqueadoHasta > reloj.getAsLong();
    }

    public synchronized void registrarFallo(String clave) {
        long ahora = reloj.getAsLong();
        limpiar(ahora);
        Estado e = estados.computeIfAbsent(clave, k -> new Estado());
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
        estados.remove(clave);
    }

    /** Evita que el mapa crezca sin límite: descarta entradas ya vencidas. */
    private void limpiar(long ahora) {
        if (estados.size() < 5_000) return;
        Iterator<Estado> it = estados.values().iterator();
        while (it.hasNext()) {
            Estado e = it.next();
            if (e.bloqueadoHasta <= ahora && ahora - e.inicioVentana > ventanaMs) it.remove();
        }
    }
}
