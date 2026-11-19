package com.mycompany.facturacionventasposykardex;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

/** Menu del ejercicio conectado a Supabase. Ya no reinicia los datos al salir. */
public class FacturacionventasposyKardex {
    private static final Scanner leer = new Scanner(System.in);
    private static String texto(String mensaje) {
        System.out.print(mensaje);
        if (!leer.hasNextLine()) throw new FinEntrada();
        return leer.nextLine().trim();
    }
    private static class FinEntrada extends RuntimeException { }
    private static int numero(String mensaje,int minimo,int maximo) {
        while (true) {
            try { int n=Integer.parseInt(texto(mensaje)); if(n>=minimo && n<=maximo)return n; }
            catch(NumberFormatException e) { }
            System.out.println("Ingrese un numero entre "+minimo+" y "+maximo+".");
        }
    }
    private static long identificador(String mensaje) {
        while(true) {
            try { long n=Long.parseLong(texto(mensaje)); if(n>=0)return n; }
            catch(NumberFormatException e) { }
            System.out.println("Ingrese un identificador valido; 0 para cancelar.");
        }
    }
    private static void mostrar(String cabecera,List<String[]> filas) {
        System.out.println(cabecera);
        for(String[] f:filas)System.out.println(String.join(" | ",f));
        if(filas.isEmpty())System.out.println("Sin registros.");
    }
    public static void main(String[] args) {
        try {
            System.out.println("SURTILADINO - ejercicio universitario de consola");
            // Se conserva el acceso del ejercicio anterior. No es un login para publicar en internet.
            boolean acceso=false;
            for(int i=0;i<3 && !acceso;i++) {
                String usuario=texto("Usuario: "), clave=texto("Contrasena: ");
                acceso=usuario.equals("admin") && clave.equals("1234");
                if(!acceso)System.out.println("Datos incorrectos.");
            }
            if(!acceso){System.out.println("Acceso bloqueado.");return;}
            try(Connection conexion=ConexionSupabase.abrir()) {
                PosDatos datos=new PosDatos(conexion);
                System.out.println("Conectado a Supabase. Los cambios se conservan al cerrar.");
                int opcion;
                do {
                    System.out.println("\n===== SURTILADINO =====\n1. Entrada de inventario\n2. Consultar productos\n3. Realizar venta\n4. Consultar Kardex\n5. Facturas\n6. Resumen de caja por fecha\n7. Convenios\n8. Clientes\n0. Salir");
                    opcion=numero("Opcion: ",0,8);
                    try {
                        switch(opcion) {
                            case 1:
                                mostrar("ID | Codigo | Producto | Precio | Stock",datos.productos());
                                long producto=identificador("ID del producto (0 cancela): ");
                                if(producto!=0){datos.entrada(producto,numero("Cantidad: ",1,Integer.MAX_VALUE));System.out.println("Entrada y saldo guardados.");}
                                break;
                            case 2: mostrar("ID | Codigo | Producto | Precio | Stock",datos.productos());break;
                            case 3: venta(datos);break;
                            case 4:
                                mostrar("Producto | Inicial | Entradas | Salidas | Stock",datos.consultar("select p.nombre,coalesce(sum(m.cantidad) filter(where m.tipo='INICIAL'),0),coalesce(sum(m.cantidad) filter(where m.tipo='ENTRADA'),0),coalesce(sum(m.cantidad) filter(where m.tipo='SALIDA'),0),p.stock from pos.productos p left join pos.movimientos_kardex m on m.producto_id=p.id group by p.id order by p.id"));
                                long id=identificador("ID de producto para ver movimientos (0 vuelve): ");
                                if(id>0)mostrar("Movimiento | Fecha Bogota | Tipo | Cantidad | Saldo | Factura",datos.consultar("select m.id,to_char(m.fecha at time zone 'America/Bogota','DD/MM/YYYY HH24:MI:SS'),m.tipo,m.cantidad,m.saldo,coalesce(d.venta_id::text,'-') from pos.movimientos_kardex m left join pos.detalle_ventas d on d.id=m.detalle_venta_id where m.producto_id=? order by m.id",id));
                                break;
                            case 5: facturas(datos);break;
                            case 6: caja(datos);break;
                            case 7: convenios(datos);break;
                            case 8: clientes(datos);break;
                            default: System.out.println("Hasta pronto. Tus datos permanecen en Supabase.");
                        }
                    } catch(IllegalArgumentException e){System.out.println(e.getMessage());}
                    catch(SQLException e){
                        errorSQL(e);
                        if(e.getSQLState()!=null && e.getSQLState().startsWith("08")) {
                            System.out.println("Se perdio la conexion. Reinicia y consulta las facturas antes de repetir una venta.");break;
                        }
                    }
                }while(opcion!=0);
            }
        }catch(FinEntrada e){System.out.println("Entrada finalizada.");}
        catch(IOException e){System.out.println(e.getMessage());}
        catch(SQLException e){errorSQL(e);}
        finally {leer.close();}
    }
    private static void errorSQL(SQLException e) {
        String estado=e.getSQLState();
        if("23505".equals(estado))System.out.println("Ese registro ya existe (por ejemplo, un cliente con el mismo documento).");
        else if("23503".equals(estado))System.out.println("No existe el cliente o producto indicado.");
        else System.out.println("No se pudo completar la operacion. Estado SQL: "+estado+". Revisa los datos y la conexion.");
    }
    private static void clientes(PosDatos datos)throws SQLException {
        int opcion;
        do {
            opcion=numero("\nCLIENTES\n1. Registrar corriente\n2. Registrar mayorista\n3. Consultar\n0. Volver\nOpcion: ",0,3);
            if(opcion==3)mostrar("ID | Nombre | Documento | Numero | Tipo",datos.clientes());
            if(opcion==1 || opcion==2){
                String nombre=texto("Nombre o razon social: ");
                int tipo=numero("Documento (1 CEDULA / 2 NIT): ",1,2);
                String documento=texto("Numero del documento: ");
                long id=datos.registrarCliente(nombre,tipo==1?"CEDULA":"NIT",documento,opcion==1?"Corriente":"Mayorista");
                System.out.println("Cliente guardado. ID: "+id);
            }
        }while(opcion!=0);
    }
    private static void convenios(PosDatos datos)throws SQLException {
        int opcion;
        do {
            opcion=numero("\nCONVENIOS\n1. Consultar\n2. Cambiar porcentaje mayorista\n0. Volver\nOpcion: ",0,2);
            if(opcion==1){mostrar("ID | Codigo | Nombre | Porcentaje",datos.convenios());System.out.println("2x1: por pares del mismo producto. Solo un convenio por venta.");}
            if(opcion==2){
                try {datos.porcentajeMayorista(new BigDecimal(texto("Porcentaje (0 a 100): ").replace(',','.')));System.out.println("Porcentaje guardado.");}
                catch(NumberFormatException e){System.out.println("Porcentaje invalido.");}
            }
        }while(opcion!=0);
    }
    private static void venta(PosDatos datos)throws SQLException {
        mostrar("ID | Nombre | Documento | Numero | Tipo",datos.clientes());
        long cliente=identificador("ID de cliente registrado (0 para corriente sin registro): ");
        int corriente=cliente==0?numero("Numero del cliente corriente: ",1,Integer.MAX_VALUE):0;
        mostrar("ID | Codigo | Convenio | Porcentaje",datos.convenios());
        long convenio=identificador("ID del convenio (0 cancela): ");
        if(convenio==0)return;
        Map<Long,Integer> cantidades=new TreeMap<>();
        for(String[] p:datos.productos()){
            int cantidad=numero(p[2]+" | $"+p[3]+" | Stock "+p[4]+" | Unidades (0 omite): ",0,Integer.parseInt(p[4]));
            if(cantidad>0)cantidades.put(Long.parseLong(p[0]),cantidad);
        }
        PosDatos.Venta cotizacion=datos.cotizar(cliente,corriente,convenio,cantidades);
        System.out.println("\nCliente: "+cotizacion.nombreCliente);
        for(PosDatos.Linea l:cotizacion.lineas)System.out.println(l.nombre+" x "+l.cantidad+" | Gratis: "+l.gratis+" | Descuento: $"+l.descuento+" | Total con IVA simulado: $"+l.total);
        System.out.println("TOTAL: $"+cotizacion.total);
        if(numero("Confirmar (1 si / 0 cancelar): ",0,1)==0){System.out.println("Venta cancelada. No se guardaron cambios.");return;}
        long id=datos.vender(cotizacion);
        System.out.println("VENTA GUARDADA. Factura numero "+id);
        System.out.println(datos.factura(id));
    }
    private static void facturas(PosDatos datos)throws SQLException {
        int opcion=numero("\n1. Ultima factura\n2. Consultar por numero\n3. Listar ultimas 20\n0. Volver\nOpcion: ",0,3);
        if(opcion==1){
            List<String[]> filas=datos.consultar("select id from pos.ventas order by id desc limit 1");
            System.out.println(filas.isEmpty()?"No existen ventas.":datos.factura(Long.parseLong(filas.get(0)[0])));
        } else if(opcion==2){long id=identificador("Numero de factura (0 cancela): ");if(id>0)System.out.println(datos.factura(id));}
        else if(opcion==3)mostrar("Factura | Fecha Bogota | Cliente | Total",datos.consultar("select id,to_char(fecha at time zone 'America/Bogota','DD/MM/YYYY HH24:MI:SS'),cliente_factura,total from pos.resumen_ventas order by id desc limit 20"));
    }
    private static void caja(PosDatos datos)throws SQLException {
        String entrada=texto("Fecha AAAA-MM-DD (Enter = hoy en Bogota): ");
        LocalDate fecha;
        try {fecha=entrada.isEmpty()?LocalDate.now(ZoneId.of("America/Bogota")):LocalDate.parse(entrada);}
        catch(java.time.format.DateTimeParseException e){System.out.println("Fecha invalida.");return;}
        String[] r=datos.consultar("select count(*),coalesce(sum(total),0),coalesce(sum(base),0),coalesce(sum(iva),0),coalesce(sum(costo),0),coalesce(sum(base-costo),0) from pos.resumen_ventas r where (fecha at time zone 'America/Bogota')::date=? and exists(select 1 from pos.ventas v where v.id=r.id and v.estado='VIGENTE')",java.sql.Date.valueOf(fecha)).get(0);
        System.out.println("RESUMEN DE CAJA "+fecha+"\nVentas: "+r[0]+"\nTotal facturado: $"+r[1]+"\nBase sin IVA: $"+r[2]+"\nIVA simulado: $"+r[3]+"\nCosto: $"+r[4]+"\nMargen estimado: $"+r[5]);
        System.out.println("Resumen calculado de las ventas guardadas; no registra arqueo ni medios de pago.");
    }
}
