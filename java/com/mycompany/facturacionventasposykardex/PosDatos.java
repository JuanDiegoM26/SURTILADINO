package com.mycompany.facturacionventasposykardex;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Consultas y operaciones del POS. Los parametros nunca se concatenan al SQL. */
public class PosDatos {
    private final Connection db;
    public PosDatos(Connection db) { this.db = db; }

    public List<String[]> consultar(String sql, Object... valores) throws SQLException {
        List<String[]> filas = new ArrayList<>();
        try (PreparedStatement p = db.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) p.setObject(i + 1, valores[i]);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    String[] fila = new String[r.getMetaData().getColumnCount()];
                    for (int i = 0; i < fila.length; i++) fila[i] = r.getString(i + 1);
                    filas.add(fila);
                }
            }
        }
        return filas;
    }

    public List<String[]> productos() throws SQLException {
        return consultar("select id,codigo,nombre,precio_venta,stock from pos.productos where activo order by id");
    }
    public List<String[]> clientes() throws SQLException {
        return consultar("select id,nombre,tipo_documento,documento,tipo_cliente from pos.clientes where activo order by id");
    }
    public List<String[]> convenios() throws SQLException {
        return consultar("select id,codigo,nombre,porcentaje,tipo from pos.convenios where activo order by id");
    }

    public long registrarCliente(String nombre, String tipoDocumento, String documento, String tipo) throws SQLException {
        if (nombre.trim().isEmpty() || documento.trim().isEmpty()) throw new IllegalArgumentException("Nombre y documento son obligatorios.");
        if (!tipoDocumento.equals("CEDULA") && !tipoDocumento.equals("NIT")) throw new IllegalArgumentException("Documento invalido.");
        if (!tipo.equals("Corriente") && !tipo.equals("Mayorista")) throw new IllegalArgumentException("Tipo de cliente invalido.");
        return Long.parseLong(consultar("insert into pos.clientes(nombre,tipo_documento,documento,tipo_cliente) values (?,?,?,?) returning id",
                nombre.trim(), tipoDocumento, documento.trim(), tipo).get(0)[0]);
    }

    public void porcentajeMayorista(BigDecimal porcentaje) throws SQLException {
        if (porcentaje.scale() > 2 || porcentaje.signum() < 0 || porcentaje.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Use un porcentaje entre 0 y 100, con hasta dos decimales.");
        try (PreparedStatement p = db.prepareStatement("update pos.convenios set porcentaje=?,version=version+1 where codigo='MAYORISTA' and tipo='MAYORISTA' and activo")) {
            p.setBigDecimal(1, porcentaje);
            if (p.executeUpdate() != 1) throw new SQLException("Falta el convenio mayorista", "P0001");
        }
    }

    public void entrada(long producto, int cantidad) throws SQLException {
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser positiva.");
        // El trigger de Supabase actualiza el saldo y el stock juntos.
        try (PreparedStatement p = db.prepareStatement("insert into pos.movimientos_kardex(producto_id,tipo,cantidad,observacion) values (?,'ENTRADA',?,'Entrada registrada desde SURTILADINO')")) {
            p.setLong(1, producto); p.setInt(2, cantidad); p.executeUpdate();
        }
    }

    public static BigDecimal dinero(BigDecimal x) { return x.setScale(2, RoundingMode.HALF_UP); }

    public static class Linea {
        public long producto;
        public String nombre;
        public int cantidad, gratis;
        public BigDecimal precio, costo, subtotal, descuento, base, iva, total;
    }
    public static class Venta {
        public long id, cliente, convenio, original;
        public int corriente;
        public String nombreCliente, documento, tipoConvenio, nombreConvenio;
        public BigDecimal porcentaje;
        public List<Linea> lineas = new ArrayList<>();
        public BigDecimal total = new BigDecimal("0.00");
    }

    public static Linea calcular(long producto, String nombre, BigDecimal precio, BigDecimal costo,
                                 int cantidad, String convenio, BigDecimal porcentaje) {
        if (cantidad <= 0) throw new IllegalArgumentException("Cantidad invalida.");
        Linea l = new Linea(); l.producto = producto; l.nombre = nombre; l.precio = precio; l.costo = costo; l.cantidad = cantidad;
        l.subtotal = dinero(precio.multiply(BigDecimal.valueOf(cantidad)));
        l.gratis = convenio.equals("DOS_POR_UNO") ? cantidad / 2 : 0;
        l.descuento = convenio.equals("MAYORISTA")
                ? dinero(l.subtotal.multiply(porcentaje).divide(new BigDecimal("100")))
                : dinero(precio.multiply(BigDecimal.valueOf(l.gratis)));
        l.base = l.subtotal.subtract(l.descuento);
        l.iva = dinero(l.base.multiply(new BigDecimal("0.19")));
        l.total = l.base.add(l.iva);
        return l;
    }

    public Venta cotizar(long cliente, int corriente, long convenio, Map<Long,Integer> cantidades) throws SQLException {
        return preparar(cliente, corriente, convenio, cantidades, false);
    }

    public Venta cotizarCorreccion(long original,long cliente,int corriente,long convenio,Map<Long,Integer> cantidades)throws SQLException {
        Map<Long,Integer> credito=new TreeMap<>();
        if(consultar("select id from pos.ventas where id=? and estado='VIGENTE'",original).isEmpty())throw new IllegalArgumentException("La venta ya fue anulada o no existe.");
        for(String[] r:consultar("select producto_id,cantidad from pos.detalle_ventas where venta_id=?",original))credito.put(Long.parseLong(r[0]),Integer.parseInt(r[1]));
        Venta v=preparar(cliente,corriente,convenio,cantidades,false,credito);v.original=original;return v;
    }

    private Venta preparar(long cliente, int corriente, long convenio, Map<Long,Integer> cantidades, boolean bloquear) throws SQLException {
        return preparar(cliente,corriente,convenio,cantidades,bloquear,Map.of());
    }
    private Venta preparar(long cliente, int corriente, long convenio, Map<Long,Integer> cantidades, boolean bloquear,Map<Long,Integer> credito) throws SQLException {
        if (cantidades.isEmpty()) throw new IllegalArgumentException("La venta no tiene productos.");
        Venta v = new Venta(); v.cliente = cliente; v.corriente = corriente; v.convenio = convenio;
        String tipoCliente = "Corriente";
        if (cliente > 0) {
            List<String[]> c = consultar("select nombre,tipo_documento,documento,tipo_cliente from pos.clientes where id=? and activo" + (bloquear ? " for share" : ""), cliente);
            if (c.isEmpty()) throw new IllegalArgumentException("El cliente no existe.");
            String[] r = c.get(0); v.nombreCliente = r[0]; v.documento = r[1] + ": " + r[2]; tipoCliente = r[3];
        } else {
            if (cliente != 0 || corriente <= 0) throw new IllegalArgumentException("Indique un numero positivo para el cliente corriente.");
            v.nombreCliente = String.valueOf(corriente);
        }
        List<String[]> conv = consultar("select tipo,porcentaje,nombre from pos.convenios where id=? and activo" + (bloquear ? " for share" : ""), convenio);
        if (conv.isEmpty()) throw new IllegalArgumentException("El convenio no existe.");
        v.tipoConvenio = conv.get(0)[0]; v.porcentaje = new BigDecimal(conv.get(0)[1]);
        v.nombreConvenio=conv.get(0)[2];
        if (v.tipoConvenio.equals("MAYORISTA") && !tipoCliente.equals("Mayorista"))
            throw new IllegalArgumentException("El descuento mayorista requiere un cliente mayorista registrado.");
        // Siempre bloquear en el mismo orden evita bloqueos cruzados entre ventas.
        for (Map.Entry<Long,Integer> e : new TreeMap<>(cantidades).entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) throw new IllegalArgumentException("Cantidad invalida.");
            List<String[]> prods = consultar("select nombre,precio_venta,costo_unitario,stock from pos.productos where id=? and activo" + (bloquear ? " for update" : ""), e.getKey());
            if (prods.isEmpty()) throw new IllegalArgumentException("Producto inexistente o inactivo.");
            String[] p = prods.get(0);
            if (e.getValue().longValue() > Long.parseLong(p[3])+credito.getOrDefault(e.getKey(),0)) throw new IllegalArgumentException("Stock insuficiente para " + p[0]);
            Linea l = calcular(e.getKey(), p[0], new BigDecimal(p[1]), new BigDecimal(p[2]), e.getValue(), v.tipoConvenio, v.porcentaje);
            v.lineas.add(l); v.total = v.total.add(l.total);
        }
        return v;
    }

    /** Confirma todo o revierte todo. Una transaccion externa permite pruebas sin guardar. */
    public long vender(Venta confirmada) throws SQLException {
        boolean propia = db.getAutoCommit();
        Savepoint inicio = null;
        if (propia) db.setAutoCommit(false); else inicio = db.setSavepoint();
        try {
            Map<Long,Integer> cantidades = new TreeMap<>();
            for (Linea l : confirmada.lineas) cantidades.put(l.producto, l.cantidad);
            Venta v = preparar(confirmada.cliente, confirmada.corriente, confirmada.convenio, cantidades, true);
            if (!mismaCotizacion(confirmada, v)) throw new IllegalArgumentException("Los datos cambiaron. Revise una nueva cotizacion antes de confirmar.");
            v.id = Long.parseLong(consultar("insert into pos.ventas(cliente_id,numero_cliente_corriente,cliente_factura,documento_factura,convenio_id,porcentaje_aplicado,convenio_factura) values (?,?,?,?,?,?,?) returning id",
                    v.cliente > 0 ? v.cliente : null, v.cliente > 0 ? null : v.corriente,
                    v.nombreCliente, v.documento, v.convenio, v.porcentaje,v.nombreConvenio).get(0)[0]);
            for (Linea l : v.lineas) {
                long detalle = Long.parseLong(consultar("insert into pos.detalle_ventas(venta_id,producto_id,producto_factura,cantidad,precio_unitario,costo_unitario,unidades_gratis,descuento,porcentaje_iva) values (?,?,?,?,?,?,?,?,19) returning id",
                        v.id,l.producto,l.nombre,l.cantidad,l.precio,l.costo,l.gratis,l.descuento).get(0)[0]);
                try (PreparedStatement p = db.prepareStatement("insert into pos.movimientos_kardex(producto_id,tipo,cantidad,detalle_venta_id,observacion) values (?,'SALIDA',?,?,'Venta SURTILADINO')")) {
                    p.setLong(1,l.producto); p.setInt(2,l.cantidad); p.setLong(3,detalle); p.executeUpdate();
                }
            }
            if (propia) db.commit(); else db.releaseSavepoint(inicio);
            return v.id;
        } catch (SQLException | RuntimeException e) {
            try { if (propia) db.rollback(); else db.rollback(inicio); }
            catch (SQLException fallo) { e.addSuppressed(fallo); }
            throw e;
        } finally { if (propia) db.setAutoCommit(true); }
    }

    private boolean mismaCotizacion(Venta a, Venta b) {
        if (!a.nombreCliente.equals(b.nombreCliente) || !java.util.Objects.equals(a.documento,b.documento)
                || !a.tipoConvenio.equals(b.tipoConvenio) || !a.nombreConvenio.equals(b.nombreConvenio) || a.porcentaje.compareTo(b.porcentaje)!=0
                || a.lineas.size()!=b.lineas.size()) return false;
        for (int i=0;i<a.lineas.size();i++) {
            Linea x=a.lineas.get(i), y=b.lineas.get(i);
            if (x.producto!=y.producto || !x.nombre.equals(y.nombre) || x.precio.compareTo(y.precio)!=0
                    || x.costo.compareTo(y.costo)!=0 || x.total.compareTo(y.total)!=0) return false;
        }
        return true;
    }

    public String factura(long id) throws SQLException {
        List<String[]> cab = consultar("select v.id,to_char(v.fecha at time zone 'America/Bogota','DD/MM/YYYY HH24:MI:SS'),v.cliente_factura,v.documento_factura,v.convenio_factura,v.porcentaje_aplicado,v.estado,v.motivo_anulacion,v.reemplazada_por from pos.ventas v where v.id=?",id);
        if (cab.isEmpty()) return "No existe esa factura.";
        String[] c=cab.get(0);
        StringBuilder texto=new StringBuilder("\n===== SURTILADINO - FACTURA =====\nNumero: "+c[0]+"\nFecha y hora (Bogota): "+c[1]+"\nCliente: "+c[2]);
        if (c[3]!=null) texto.append("\n").append(c[3]); else texto.append("\nCliente corriente sin registro");
        texto.append("\nConvenio: ").append(c[4]).append(" | Porcentaje: ").append(c[5]).append("%");
        texto.append("\nEstado: ").append(c[6]);
        if(c[7]!=null)texto.append("\nMotivo de anulación: ").append(c[7]);
        if(c[8]!=null)texto.append("\nReemplazada por factura: ").append(c[8]);
        for (String[] d : consultar("select producto_factura,cantidad,precio_unitario,subtotal,unidades_gratis,descuento,base,iva,total from pos.detalle_ventas where venta_id=? order by id",id)) {
            texto.append("\n--------------------\nProducto: ").append(d[0]).append("\nCantidad: ").append(d[1])
                 .append(" | Precio: $").append(d[2]).append("\nSubtotal: $").append(d[3])
                 .append(" | Unidades gratis: ").append(d[4]).append("\nDescuento: $").append(d[5])
                 .append("\nBase: $").append(d[6]).append(" | IVA simulado 19%: $").append(d[7])
                 .append("\nTotal producto: $").append(d[8]);
        }
        String[] t=consultar("select subtotal,descuento,base,iva,total from pos.resumen_ventas where id=?",id).get(0);
        return texto+"\n====================\nSubtotal: $"+t[0]+"\nDescuentos: $"+t[1]+"\nBase sin IVA: $"+t[2]+"\nIVA: $"+t[3]+"\nTOTAL A PAGAR: $"+t[4];
    }
}
