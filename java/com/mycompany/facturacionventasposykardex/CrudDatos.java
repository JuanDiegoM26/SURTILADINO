package com.mycompany.facturacionventasposykardex;

import java.sql.*;
import java.math.BigDecimal;
import java.util.*;

/** Gestión de maestros y correcciones trazables de operaciones ya registradas. */
public class CrudDatos {
 private final Connection db; private final PosDatos d;
 public CrudDatos(Connection c){db=c;d=new PosDatos(c);}
 private interface Trabajo<T>{T hacer()throws SQLException;}
 private <T>T transaccion(Trabajo<T> t)throws SQLException{
  boolean propia=db.getAutoCommit();Savepoint s=null;
  if(propia)db.setAutoCommit(false);else s=db.setSavepoint();
  try{T r=t.hacer();if(propia)db.commit();else db.releaseSavepoint(s);return r;}
  catch(SQLException|RuntimeException ex){if(propia)db.rollback();else db.rollback(s);throw ex;}
  finally{if(propia)db.setAutoCommit(true);}
 }
 private int ejecutar(String sql,Object...p)throws SQLException{try(PreparedStatement s=db.prepareStatement(sql)){for(int i=0;i<p.length;i++)s.setObject(i+1,p[i]);return s.executeUpdate();}}
 private static String texto(Map<String,String> p,String k,int max){String s=p.getOrDefault(k,"").trim();if(s.isEmpty()||s.length()>max)throw new IllegalArgumentException("Revisa el campo "+k+".");return s;}
 private static long id(Map<String,String> p,String k){return Long.parseLong(p.getOrDefault(k,"0"));}
 private static BigDecimal decimal(Map<String,String> p,String k,BigDecimal max){BigDecimal n=new BigDecimal(texto(p,k,20));if(n.signum()<0||n.scale()>2||n.compareTo(max)>0)throw new IllegalArgumentException("Revisa "+k+": usa un valor positivo con hasta dos decimales.");return n;}
 private static boolean activo(Map<String,String> p){if(!Set.of("true","false").contains(p.getOrDefault("activo","true")))throw new IllegalArgumentException("Estado no válido.");return Boolean.parseBoolean(p.getOrDefault("activo","true"));}
 private static void cambiado(int n){if(n!=1)throw new IllegalArgumentException("El registro cambió o ya no existe. Actualiza la pantalla antes de editar.");}
 public Map<String,Object> maestros()throws SQLException{
  return Map.of("productos",d.consultar("select id,codigo,nombre,precio_venta,stock,costo_unitario,activo,version from pos.productos order by id"),
   "clientes",d.consultar("select id,nombre,tipo_documento,documento,tipo_cliente,activo,version from pos.clientes order by id"),
   "convenios",d.consultar("select id,codigo,nombre,porcentaje,tipo,activo,version from pos.convenios order by id"));
 }
 public long producto(Map<String,String> p)throws SQLException{
  String codigo=texto(p,"codigo",30),nombre=texto(p,"nombre",100);BigDecimal precio=decimal(p,"precio",new BigDecimal("9999999999.99")),costo=decimal(p,"costo",new BigDecimal("9999999999.99"));long registro=id(p,"id");
  return transaccion(()->{
   if(registro==0){long nuevo=Long.parseLong(d.consultar("insert into pos.productos(codigo,nombre,precio_venta,costo_unitario,activo) values (?,?,?,?,?) returning id",codigo,nombre,precio,costo,activo(p)).get(0)[0]);int inicial=Integer.parseInt(p.getOrDefault("inicial","0"));if(inicial<0)throw new IllegalArgumentException("Existencia inicial inválida.");if(inicial>0)movimiento(nuevo,"INICIAL",inicial,"Existencia inicial del producto",null);return nuevo;}
   cambiado(ejecutar("update pos.productos set codigo=?,nombre=?,precio_venta=?,costo_unitario=?,activo=?,version=version+1 where id=? and version=?",codigo,nombre,precio,costo,activo(p),registro,id(p,"version")));return registro;
  });
 }
 public long cliente(Map<String,String> p)throws SQLException{
  String nombre=texto(p,"nombre",150),doc=texto(p,"documento",30),td=texto(p,"tipo_documento",10),tc=texto(p,"tipo_cliente",12);
  if(!Set.of("CEDULA","NIT").contains(td)||!Set.of("Corriente","Mayorista").contains(tc))throw new IllegalArgumentException("Tipo de cliente o documento inválido.");
  long registro=id(p,"id");if(registro==0)return d.registrarCliente(nombre,td,doc,tc);
  cambiado(ejecutar("update pos.clientes set nombre=?,documento=?,tipo_documento=?,tipo_cliente=?,activo=?,version=version+1 where id=? and version=?",nombre,doc,td,tc,activo(p),registro,id(p,"version")));return registro;
 }
 public long convenio(Map<String,String> p)throws SQLException{
  String codigo=texto(p,"codigo",20),nombre=texto(p,"nombre",80),tipo=texto(p,"tipo",20);BigDecimal porcentaje=decimal(p,"porcentaje",new BigDecimal("100"));
  if(!Set.of("SIN_CONVENIO","DOS_POR_UNO","MAYORISTA").contains(tipo))throw new IllegalArgumentException("Tipo de convenio inválido.");
  if(!tipo.equals("MAYORISTA")&&porcentaje.signum()!=0)throw new IllegalArgumentException("El porcentaje solo aplica al descuento mayorista.");
  long registro=id(p,"id");if(registro==0)return Long.parseLong(d.consultar("insert into pos.convenios(codigo,nombre,tipo,porcentaje,activo) values (?,?,?,?,?) returning id",codigo,nombre,tipo,porcentaje,activo(p)).get(0)[0]);
  cambiado(ejecutar("update pos.convenios set codigo=?,nombre=?,tipo=?,porcentaje=?,activo=?,version=version+1 where id=? and version=?",codigo,nombre,tipo,porcentaje,activo(p),registro,id(p,"version")));return registro;
 }
 public String eliminar(String modulo,long registro,long version)throws SQLException{
  if(!Set.of("productos","clientes","convenios").contains(modulo))throw new IllegalArgumentException("Módulo inválido.");
  return transaccion(()->{
   if(d.consultar("select id from pos."+modulo+" where id=? and version=? for update",registro,version).isEmpty())throw new IllegalArgumentException("El registro cambió. Actualiza la pantalla.");
   String consulta=modulo.equals("productos")?"select 1 from pos.movimientos_kardex where producto_id=? union all select 1 from pos.detalle_ventas where producto_id=?":modulo.equals("clientes")?"select 1 from pos.ventas where cliente_id=?":"select 1 from pos.ventas where convenio_id=?";
   boolean usado=!(modulo.equals("productos")?d.consultar(consulta,registro,registro):d.consultar(consulta,registro)).isEmpty();
   if(usado){ejecutar("update pos."+modulo+" set activo=false,version=version+1 where id=?",registro);return "Archivado: se conserva porque tiene historial. Puedes reactivarlo al editar.";}
   cambiado(ejecutar("delete from pos."+modulo+" where id=?",registro));return "Registro eliminado.";
  });
 }
 private long movimiento(long producto,String tipo,int cantidad,String motivo,Long reversa)throws SQLException{
  if(cantidad<=0)throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
  if(motivo==null||motivo.trim().isEmpty()||motivo.length()>250)throw new IllegalArgumentException("Escribe un motivo de hasta 250 caracteres.");
  return Long.parseLong(d.consultar("insert into pos.movimientos_kardex(producto_id,tipo,cantidad,observacion,reversa_de) values (?,?,?,?,?) returning id",producto,tipo,cantidad,motivo.trim(),reversa).get(0)[0]);
 }
 public long crearMovimiento(Map<String,String> p)throws SQLException{
  String tipo=texto(p,"tipo",20);if(!Set.of("ENTRADA","AJUSTE_ENTRADA","AJUSTE_SALIDA").contains(tipo))throw new IllegalArgumentException("Movimiento manual inválido.");
  return movimiento(id(p,"producto"),tipo,Integer.parseInt(p.getOrDefault("cantidad","0")),texto(p,"motivo",250),null);
 }
 public long corregirMovimiento(long registro,Integer cantidad,String motivo)throws SQLException{
  return transaccion(()->{
   List<String[]> rows=d.consultar("select producto_id,tipo,cantidad,reversa_de from pos.movimientos_kardex where id=?",registro);
   if(rows.isEmpty())throw new IllegalArgumentException("El movimiento no existe.");String[] r=rows.get(0);long producto=Long.parseLong(r[0]);
   // Mismo orden que el trigger: primero producto, después movimiento.
   d.consultar("select id from pos.productos where id=? for update",producto);d.consultar("select id from pos.movimientos_kardex where id=? for update",registro);
   if(r[3]!=null||!Set.of("INICIAL","ENTRADA","AJUSTE_ENTRADA","AJUSTE_SALIDA").contains(r[1])||!d.consultar("select id from pos.movimientos_kardex where reversa_de=?",registro).isEmpty())throw new IllegalArgumentException("Este movimiento no se puede corregir aquí. Las salidas por venta se corrigen desde Facturas.");
   String inverso=r[1].equals("AJUSTE_SALIDA")?"AJUSTE_ENTRADA":"AJUSTE_SALIDA";
   long reversa=movimiento(producto,inverso,Integer.parseInt(r[2]),motivo,registro);
   if(cantidad==null)return reversa;
   return movimiento(producto,r[1].equals("INICIAL")?"ENTRADA":r[1],cantidad,motivo,null);
  });
 }
 public void anular(long venta,String motivo)throws SQLException{
  transaccion(()->{
   if(motivo==null||motivo.trim().isEmpty()||motivo.length()>250)throw new IllegalArgumentException("Escribe el motivo de anulación (máximo 250 caracteres).");
   List<String[]> rows=d.consultar("select estado from pos.ventas where id=? for update",venta);
   if(rows.isEmpty())throw new IllegalArgumentException("La venta no existe.");
   if(rows.get(0)[0].equals("ANULADA"))throw new IllegalArgumentException("La venta ya está anulada.");
   List<String[]> movimientos=d.consultar("select m.id,m.producto_id,m.cantidad from pos.movimientos_kardex m join pos.detalle_ventas d on d.id=m.detalle_venta_id where d.venta_id=? order by m.producto_id",venta);
   for(String[] m:movimientos)d.consultar("select id from pos.productos where id=? for update",Long.parseLong(m[1]));
   ejecutar("update pos.ventas set estado='ANULADA',motivo_anulacion=?,anulada_en=now() where id=?",motivo.trim(),venta);
   for(String[] m:movimientos)movimiento(Long.parseLong(m[1]),"DEVOLUCION",Integer.parseInt(m[2]),motivo,Long.parseLong(m[0]));
   return null;
  });
 }
 public long confirmar(PosDatos.Venta v,String motivo)throws SQLException{
  if(v.original==0)return d.vender(v);
  return transaccion(()->{
   anular(v.original,motivo);long nueva=d.vender(v);
   ejecutar("update pos.ventas set reemplazada_por=? where id=?",nueva,v.original);return nueva;
  });
 }
 public Map<String,Object> venta(long registro)throws SQLException{
  List<String[]> r=d.consultar("select id,coalesce(cliente_id,0),coalesce(numero_cliente_corriente,0),convenio_id,estado from pos.ventas where id=?",registro);
  if(r.isEmpty())throw new IllegalArgumentException("La venta no existe.");
  return Map.of("cabecera",r.get(0),"lineas",d.consultar("select producto_id,cantidad from pos.detalle_ventas where venta_id=? order by producto_id",registro));
 }
}
