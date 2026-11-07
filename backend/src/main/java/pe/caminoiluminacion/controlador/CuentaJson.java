package pe.caminoiluminacion.controlador;

import pe.caminoiluminacion.dao.IntencionDAO;
import pe.caminoiluminacion.modelo.Documento;
import pe.caminoiluminacion.modelo.Notificacion;
import pe.caminoiluminacion.modelo.Pago;
import pe.caminoiluminacion.modelo.Reserva;
import pe.caminoiluminacion.seguridad.Cripto;
import pe.caminoiluminacion.servicio.CuentaService.DatosPersonales;
import pe.caminoiluminacion.servicio.CuentaService.Perfil;
import pe.caminoiluminacion.util.Json;

import java.util.List;
import java.util.function.Function;

/**
 * JSON de "Mi cuenta". Nunca incluye el hash de la contraseña, las rutas de los archivos, el hash de los comprobantes
 * ni el DNI o el celular completos (van enmascarados).
 */
final class CuentaJson {

    private CuentaJson() { }

    static String perfil(Perfil p) {
        return "{\"nombre\":\"" + Json.esc(p.nombre()) + "\",\"apellido\":\"" + Json.esc(p.apellido()) + "\""
                + ",\"dni\":\"" + Json.esc(p.dniEnmascarado()) + "\",\"correo\":\"" + Json.esc(p.correo()) + "\""
                + ",\"telefono\":\"" + Json.esc(p.telefono()) + "\",\"rol\":\"" + Json.esc(p.rol()) + "\""
                + ",\"politicaAceptada\":" + p.politicaAceptada()
                + ",\"politicaVersionAceptada\":\"" + Json.esc(p.politicaVersionAceptada()) + "\""
                + ",\"politicaVigente\":\"" + Json.esc(p.politicaVigente()) + "\""
                + ",\"creadoEn\":\"" + Json.fecha(p.creadoEn()) + "\"}";
    }

    static String datos(DatosPersonales d) {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("\"generadoEn\":\"").append(Json.esc(d.generadoEn())).append("\",\n");
        sb.append("\"perfil\":").append(perfil(d.perfil())).append(",\n");
        sb.append("\"reservas\":").append(lista(d.reservas(), r -> reserva(r, d))).append(",\n");
        sb.append("\"documentos\":").append(lista(d.documentos(), CuentaJson::documento)).append(",\n");
        sb.append("\"pagos\":").append(lista(d.pagos(), CuentaJson::pago)).append(",\n");
        sb.append("\"intencionesDeMisa\":").append(lista(d.intenciones(), CuentaJson::intencion)).append(",\n");
        sb.append("\"notificaciones\":").append(lista(d.notificaciones(), CuentaJson::notificacion)).append("\n}");
        return sb.toString();
    }

    private static <T> String lista(List<T> items, Function<T, String> f) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) sb.append(i > 0 ? ",\n " : "\n ").append(f.apply(items.get(i)));
        return sb.append(items.isEmpty() ? "]" : "\n]").toString();
    }

    private static String reserva(Reserva r, DatosPersonales d) {
        return "{\"sacramento\":\"" + Json.esc(r.getSacramento()) + "\",\"parroquia\":\"" + Json.esc(d.parroquias().get(r.getIdParroquia())) + "\""
                + ",\"solicitante\":\"" + Json.esc(r.getNombreSolicitante()) + "\",\"fecha\":\"" + Json.fecha(r.getFechaSolicitada()) + "\""
                + ",\"notas\":\"" + Json.esc(r.getNotas()) + "\",\"estado\":\"" + Json.esc(r.getEstado()) + "\""
                + ",\"motivoRechazo\":\"" + Json.esc(r.getMotivoRechazo()) + "\",\"creadoEn\":\"" + Json.fecha(r.getCreadoEn()) + "\"}";
    }

    private static String documento(Documento x) {
        return "{\"nombreArchivo\":\"" + Json.esc(x.getNombreArchivo()) + "\",\"tipo\":\"" + Json.esc(x.getTipoMime()) + "\""
                + ",\"bytes\":" + (x.getTamanoBytes() == null ? "null" : x.getTamanoBytes())
                + ",\"estado\":\"" + Json.esc(x.getEstadoValidacion()) + "\",\"motivoRechazo\":\"" + Json.esc(x.getMotivoRechazo()) + "\""
                + ",\"enviadoEn\":\"" + Json.fecha(x.getFechaEnvio()) + "\"}";
    }

    private static String pago(Pago p) {
        String celular = Cripto.enmascarar(Cripto.descifrar(p.celularOrigen()), 3);
        return "{\"concepto\":\"" + Json.esc(p.concepto()) + "\",\"monto\":" + p.monto().toPlainString()
                + ",\"metodo\":\"" + Json.esc(p.metodo()) + "\",\"celularOrigen\":\"" + Json.esc(celular) + "\""
                + ",\"numeroOperacion\":\"" + Json.esc(p.numeroOperacion()) + "\",\"estado\":\"" + Json.esc(p.estado()) + "\""
                + ",\"observacion\":\"" + Json.esc(p.observacion()) + "\",\"fecha\":\"" + Json.fecha(p.creadoEn()) + "\"}";
    }

    private static String intencion(IntencionDAO.Fila f) {
        var i = f.intencion();
        return "{\"fechaMisa\":\"" + Json.fecha(i.fechaMisa()) + "\",\"horaMisa\":\"" + Json.esc(i.horaMisa()) + "\""
                + ",\"tipo\":\"" + Json.esc(i.tipo()) + "\",\"texto\":\"" + Json.esc(i.texto()) + "\""
                + ",\"estadoPago\":\"" + Json.esc(f.estadoPago()) + "\",\"monto\":" + (f.monto() == null ? "null" : f.monto().toPlainString()) + "}";
    }

    private static String notificacion(Notificacion n) {
        return "{\"asunto\":\"" + Json.esc(n.asunto()) + "\",\"mensaje\":\"" + Json.esc(n.mensaje()) + "\""
                + ",\"leida\":" + n.leida() + ",\"fecha\":\"" + Json.fecha(n.creadaEn()) + "\"}";
    }
}
