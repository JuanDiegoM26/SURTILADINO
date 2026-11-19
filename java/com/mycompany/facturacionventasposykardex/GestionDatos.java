package com.mycompany.facturacionventasposykardex;

import java.sql.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;

/** Gestión comercial de una sede. Importes históricos y kardex inmutables. */
public class GestionDatos {
 private final Connection db; private final PosDatos d; private final CrudDatos crud;
 public GestionDatos(Connection db){this.db=db;d=new PosDatos(db);crud=new CrudDatos(db);}
 public List<Map<String,Object>> filas(String sql,Object...args)throws SQLException{
  List<Map<String,Object>> out=new ArrayList<>();
  try(PreparedStatement p=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++){Object v=r.getObject(i);row.put(r.getMetaData().getColumnLabel(i),v instanceof Number||v instanceof Boolean||v==null?v:v.toString());}out.add(row);}}}return out;
 }
 private void ejecutar(String sql,Object...args)throws SQLException{try(PreparedStatement p=db.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);if(p.executeUpdate()!=1)throw new IllegalArgumentException("El registro cambió. Actualiza antes de guardar.");}}
 static String texto(Map<String,String> p,String k,int max){String s=p.getOrDefault(k,"").trim();if(s.length()>max)throw new IllegalArgumentException("El campo "+k+" excede "+max+" caracteres.");return s;}
 static long numero(Map<String,String> p,String k){return Long.parseLong(p.getOrDefault(k,"0"));}
 static String obligatorio(Map<String,String> p,String k,int max){String s=texto(p,k,max);if(s.isEmpty())throw new IllegalArgumentException("Completa "+k+".");return s;}
 private Long relacion(Map<String,String> p,String k,String tabla)throws SQLException{long id=numero(p,k);if(id==0)return null;if(d.consultar("select id from pos."+tabla+" where id=? and activo for share",id).isEmpty())throw new IllegalArgumentException("Selecciona un registro activo de "+tabla+".");return id;}
 private static void correo(String s){if(!s.isEmpty()&&!s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("Revisa el correo electrónico.");}
 public Map<String,Object> catalogo()throws SQLException{
  Map<String,Object> m=new LinkedHashMap<>();
  m.put("productos",filas("select p.*,c.nombre categoria,pr.nombre proveedor from pos.productos p left join pos.categorias c on c.id=p.categoria_id left join pos.proveedores pr on pr.id=p.proveedor_id order by p.id"));
  m.put("clientes",filas("select * from pos.clientes order by nombre"));
  m.put("categorias",filas("select * from pos.categorias order by nombre"));
  m.put("proveedores",filas("select * from pos.proveedores order by nombre"));
  m.put("configuracion",filas("select * from pos.configuracion where id=1").get(0));return m;
 }
 public long producto(Map<String,String> p)throws SQLException{
  int min=Integer.parseInt(p.getOrDefault("stock_minimo","5")),max=Integer.parseInt(p.getOrDefault("stock_maximo","100"));
  BigDecimal iva=new BigDecimal(p.getOrDefault("porcentaje_iva","19"));
  if(min<0||max<min)throw new IllegalArgumentException("El máximo debe ser igual o mayor al mínimo; no uses valores negativos.");
  if(!(iva.compareTo(BigDecimal.ZERO)==0||iva.compareTo(new BigDecimal("5"))==0||iva.compareTo(new BigDecimal("19"))==0))throw new IllegalArgumentException("Selecciona IVA 0, 5 o 19 para el ejercicio.");
  String url=texto(p,"imagen_url",1000);if(!url.isEmpty()){URI u=URI.create(url);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException("La imagen debe usar una dirección HTTPS válida.");}
  Long cat=relacion(p,"categoria_id","categorias"),prov=relacion(p,"proveedor_id","proveedores");
  String barras=texto(p,"codigo_barras",50),marca=texto(p,"marca",80),presentacion=texto(p,"presentacion",100),ubicacion=texto(p,"ubicacion",80);
  long id=crud.producto(p);
  ejecutar("update pos.productos set categoria_id=?,proveedor_id=?,codigo_barras=?,marca=?,presentacion=?,ubicacion=?,stock_minimo=?,stock_maximo=?,porcentaje_iva=?,imagen_url=? where id=?",cat,prov,barras.isEmpty()?null:barras,marca,presentacion,ubicacion,min,max,iva,url,id);
  return id;
 }
 public long cliente(Map<String,String> p)throws SQLException{
  String tel=texto(p,"telefono",40),email=texto(p,"correo",150),dir=texto(p,"direccion",200);correo(email);long id=crud.cliente(p);
  ejecutar("update pos.clientes set telefono=?,correo=?,direccion=? where id=?",tel,email,dir,id);return id;
 }
 public long maestro(String tipo,Map<String,String> p)throws SQLException{
  if(!Set.of("categorias","proveedores").contains(tipo))throw new IllegalArgumentException("Módulo inválido.");
  long id=numero(p,"id");String nombre=obligatorio(p,"nombre",tipo.equals("categorias")?80:150);boolean activo=Boolean.parseBoolean(p.getOrDefault("activo","true"));
  if(tipo.equals("categorias")){
   String desc=texto(p,"descripcion",250);
   if(id==0)return Long.parseLong(d.consultar("insert into pos.categorias(nombre,descripcion) values(?,?) returning id",nombre,desc).get(0)[0]);
   ejecutar("update pos.categorias set nombre=?,descripcion=?,activo=?,version=version+1 where id=? and version=?",nombre,desc,activo,id,numero(p,"version"));
  }else{
   String doc=obligatorio(p,"documento",30),contacto=texto(p,"contacto",100),tel=texto(p,"telefono",40),email=texto(p,"correo",150),dir=texto(p,"direccion",200);correo(email);
   if(id==0)return Long.parseLong(d.consultar("insert into pos.proveedores(nombre,documento,contacto,telefono,correo,direccion) values(?,?,?,?,?,?) returning id",nombre,doc,contacto,tel,email,dir).get(0)[0]);
   ejecutar("update pos.proveedores set nombre=?,documento=?,contacto=?,telefono=?,correo=?,direccion=?,activo=?,version=version+1 where id=? and version=?",nombre,doc,contacto,tel,email,dir,activo,id,numero(p,"version"));
  }return id;
 }
 public String eliminar(String tipo,long id,long version)throws SQLException{
  if(!Set.of("categorias","proveedores").contains(tipo))return crud.eliminar(tipo,id,version);
  if(d.consultar("select id from pos."+tipo+" where id=? and version=? for update",id,version).isEmpty())throw new IllegalArgumentException("El registro cambió. Actualiza la pantalla.");
  String fk=tipo.equals("categorias")?"categoria_id":"proveedor_id";
  boolean usado=!d.consultar("select id from pos.productos where "+fk+"=? limit 1",id).isEmpty();
  if(tipo.equals("proveedores"))usado|=!d.consultar("select id from pos.movimientos_kardex where proveedor_id=? limit 1",id).isEmpty();
  if(usado){ejecutar("update pos."+tipo+" set activo=false,version=version+1 where id=?",id);return "Archivado para conservar el historial.";}
  ejecutar("delete from pos."+tipo+" where id=?",id);return "Registro eliminado.";
 }
 public void configurar(Map<String,String> p)throws SQLException{
  String email=texto(p,"correo",150);correo(email);
  ejecutar("update pos.configuracion set nombre=?,documento=?,direccion=?,ciudad=?,telefono=?,correo=?,sede=?,pie_factura=?,version=version+1 where id=1 and version=?",obligatorio(p,"nombre",150),texto(p,"documento",30),texto(p,"direccion",200),texto(p,"ciudad",100),texto(p,"telefono",40),email,obligatorio(p,"sede",100),texto(p,"pie_factura",250),numero(p,"version"));
 }
 public long movimiento(Map<String,String> p)throws SQLException{
  String tipo=obligatorio(p,"tipo",20),motivo=obligatorio(p,"motivo",250),clase=p.getOrDefault("motivo_tipo","GENERAL");
  if(!Set.of("ENTRADA","AJUSTE_ENTRADA","AJUSTE_SALIDA").contains(tipo)||!Set.of("GENERAL","COMPRA","CONTEO","MERMA","ROTURA","VENCIMIENTO").contains(clase))throw new IllegalArgumentException("Tipo de movimiento inválido.");
  if(Set.of("MERMA","ROTURA","VENCIMIENTO").contains(clase)&&!tipo.equals("AJUSTE_SALIDA"))throw new IllegalArgumentException("Una pérdida debe descontar unidades.");
  if(clase.equals("COMPRA")&&!tipo.equals("ENTRADA"))throw new IllegalArgumentException("Una compra debe registrarse como entrada.");
  int cantidad=Integer.parseInt(p.getOrDefault("cantidad","0"));if(cantidad<=0)throw new IllegalArgumentException("La cantidad debe ser positiva.");
  Long prov=relacion(p,"proveedor_id","proveedores");
  return Long.parseLong(d.consultar("insert into pos.movimientos_kardex(producto_id,tipo,cantidad,observacion,documento_referencia,proveedor_id,motivo_tipo) values(?,?,?,?,?,?,?) returning id",numero(p,"producto"),tipo,cantidad,motivo,texto(p,"documento_referencia",100),prov,clase).get(0)[0]);
 }
 public List<Map<String,Object>> kardex(String desde,String hasta,long producto,String tipo)throws SQLException{
  LocalDate a=LocalDate.parse(desde),b=LocalDate.parse(hasta);rango(a,b);
  return filas("select m.*,p.nombre producto,p.codigo,pr.nombre proveedor,r.id revertido_por,case when m.tipo in('SALIDA','AJUSTE_SALIDA') then m.saldo+m.cantidad else m.saldo-m.cantidad end saldo_anterior,to_char(m.fecha at time zone 'America/Bogota','DD/MM/YYYY HH24:MI') fecha_local from pos.movimientos_kardex m join pos.productos p on p.id=m.producto_id left join pos.proveedores pr on pr.id=m.proveedor_id left join pos.movimientos_kardex r on r.reversa_de=m.id where m.fecha >= (?::date::timestamp at time zone 'America/Bogota') and m.fecha < ((?::date+1)::timestamp at time zone 'America/Bogota') and (?=0 or m.producto_id=?) and (?='' or m.tipo=?) order by m.id desc limit 2000",a.toString(),b.toString(),producto,producto,tipo,tipo);
 }
 private static void rango(LocalDate a,LocalDate b){if(b.isBefore(a)||b.isAfter(a.plusYears(2)))throw new IllegalArgumentException("Selecciona un rango de hasta dos años, con la fecha inicial primero.");}
 public Map<String,Object> reportes(String desde,String hasta)throws SQLException{
  LocalDate a=LocalDate.parse(desde),b=LocalDate.parse(hasta);rango(a,b);Object[] fechas={a.toString(),b.toString()};
  String filtro=" v.estado='VIGENTE' and v.fecha >= (?::date::timestamp at time zone 'America/Bogota') and v.fecha < ((?::date+1)::timestamp at time zone 'America/Bogota') ";
  Map<String,Object> m=new LinkedHashMap<>();
  m.put("totales",filas("select count(*) ventas,coalesce(sum(r.total),0) total,coalesce(sum(r.base),0) base,coalesce(sum(r.iva),0) iva,coalesce(sum(r.descuento),0) descuento,coalesce(sum(r.costo),0) costo,coalesce(sum(r.base-r.costo),0) margen,coalesce(avg(r.total),0) ticket from pos.resumen_ventas r join pos.ventas v on v.id=r.id where "+filtro,fechas).get(0));
  m.put("dias",filas("select (v.fecha at time zone 'America/Bogota')::date dia,sum(r.total) total,sum(r.costo) costo,sum(r.base-r.costo) margen from pos.resumen_ventas r join pos.ventas v on v.id=r.id where "+filtro+" group by 1 order by 1",fechas));
  m.put("categorias",filas("select d.categoria_factura categoria,sum(d.total) total,sum(d.base-d.cantidad*d.costo_unitario) margen from pos.detalle_ventas d join pos.ventas v on v.id=d.venta_id where "+filtro+" group by 1 order by total desc",fechas));
  m.put("pagos",filas("select v.metodo_pago metodo,count(*) ventas,sum(r.total) total from pos.resumen_ventas r join pos.ventas v on v.id=r.id where "+filtro+" group by 1 order by total desc",fechas));
  m.put("top",filas("select d.producto_id,max(d.producto_factura) producto,sum(d.cantidad) unidades,sum(d.total) total,sum(d.base-d.cantidad*d.costo_unitario) margen from pos.detalle_ventas d join pos.ventas v on v.id=d.venta_id where "+filtro+" group by 1 order by unidades desc limit 10",fechas));
  m.put("movimientos",filas("select coalesce(sum(cantidad) filter(where tipo in('INICIAL','ENTRADA','AJUSTE_ENTRADA','DEVOLUCION')),0) entradas,coalesce(sum(cantidad) filter(where tipo in('SALIDA','AJUSTE_SALIDA')),0) salidas,coalesce(sum(cantidad) filter(where motivo_tipo in('MERMA','ROTURA','VENCIMIENTO') and reversa_de is null and not exists(select 1 from pos.movimientos_kardex r where r.reversa_de=m.id)),0) perdidas from pos.movimientos_kardex m where fecha >= (?::date::timestamp at time zone 'America/Bogota') and fecha < ((?::date+1)::timestamp at time zone 'America/Bogota')",fechas).get(0));
  return m;
 }
 public List<Map<String,Object>> auditoria()throws SQLException{return filas("select id,to_char(fecha at time zone 'America/Bogota','DD/MM/YYYY HH24:MI') fecha,responsable,tabla,accion,registro from pos.auditoria order by id desc limit 200");}
 public int importar(String csv)throws SQLException{
  List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quotes=false;
  csv=csv.replace("\uFEFF","");
  for(int i=0;i<csv.length();i++){char ch=csv.charAt(i);if(ch=='"'){if(quotes&&i+1<csv.length()&&csv.charAt(i+1)=='"'){cell.append('"');i++;}else quotes=!quotes;}else if(!quotes&&(ch==';'||ch=='\n')){row.add(cell.toString().replace("\r",""));cell.setLength(0);if(ch=='\n'){if(row.stream().anyMatch(v->!v.isBlank()))rows.add(row);row=new ArrayList<>();}}else cell.append(ch);}
  if(quotes)throw new IllegalArgumentException("CSV: comillas sin cerrar.");row.add(cell.toString().replace("\r",""));if(row.stream().anyMatch(v->!v.isBlank()))rows.add(row);
  String[] keys={"codigo","nombre","precio","costo","inicial","codigo_barras","marca","presentacion","ubicacion","stock_minimo","stock_maximo","porcentaje_iva"};
  if(rows.size()<2||rows.size()>101||!rows.get(0).equals(Arrays.asList(keys)))throw new IllegalArgumentException("Usa la plantilla CSV de SURTILADINO: máximo 100 productos por carga.");
  for(int i=1;i<rows.size();i++){if(rows.get(i).size()!=keys.length)throw new IllegalArgumentException("Revisa las columnas de la fila "+(i+1));Map<String,String> p=new HashMap<>();for(int k=0;k<keys.length;k++)p.put(keys[k],rows.get(i).get(k));try{producto(p);}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Fila "+(i+1)+": "+ex.getMessage());}}
  return rows.size()-1;
 }
}
