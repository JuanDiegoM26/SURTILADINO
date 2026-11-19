package com.mycompany.facturacionventasposykardex;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Interfaz local: el navegador nunca recibe la clave de PostgreSQL. */
public class SurtiladinoWeb {
    private static final boolean CLOUD="cloud".equals(System.getenv("SURTI_MODE"));
    private static final AccesoLocal ACCESO = new AccesoLocal(Path.of(System.getProperty("pos.auth",System.getenv().getOrDefault("SURTI_AUTH_FILE",CLOUD?"/etc/secrets/acceso.local.properties":"acceso.local.properties"))),CLOUD);
    private static final int PORT = Integer.getInteger("pos.port",Integer.parseInt(System.getenv().getOrDefault("PORT",CLOUD?"10000":"8787")));
    private static final String ORIGIN=origen();
    private static String origen(){
        if(!CLOUD)return "http://127.0.0.1:"+PORT;
        String valor=System.getenv().getOrDefault("SURTI_PUBLIC_URL",System.getenv().getOrDefault("RENDER_EXTERNAL_URL",""));
        URI uri=URI.create(valor);
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty()||uri.getPath().equals("/")))throw new IllegalStateException("Configura la URL HTTPS pública del servicio.");
        return "https://"+uri.getRawAuthority();
    }
    private static String cookie(String valor,boolean borrar){return "surti_session="+valor+"; Path=/; HttpOnly; SameSite=Strict"+(CLOUD?"; Secure":"")+(borrar?"; Max-Age=0":"");}
    private static final Map<String, Oferta> OFERTAS = new ConcurrentHashMap<>();
    private static class Oferta {
        final PosDatos.Venta venta; final String sesion,motivo; final long vence = System.currentTimeMillis()+900000;
        Long factura;
        Oferta(PosDatos.Venta v,String s,String m) { venta=v; sesion=s; motivo=m; }
    }
    public static void main(String[] args) throws Exception {
        if(CLOUD){
            ACCESO.validarCuenta();
            try(Connection db=ConexionSupabase.abrir()){
                if(new PosDatos(db).consultar("select version from pos.versiones_esquema where version=2").isEmpty())throw new IllegalStateException("Falta instalar el esquema 2 de SURTILADINO.");
            }catch(Exception ex){throw new IllegalStateException("No se pudo validar la conexión privada o el esquema de SURTILADINO.");}
        }
        HttpServer server=HttpServer.create(new InetSocketAddress(CLOUD?"0.0.0.0":"127.0.0.1",PORT),0);
        server.createContext("/",SurtiladinoWeb::atender);
        server.setExecutor(Executors.newFixedThreadPool(8)); server.start();
        System.out.println("SURTILADINO web: "+ORIGIN);
        System.out.println("Mantenga esta ventana abierta. Ctrl+C para cerrar.");
        if (Arrays.asList(args).contains("--abrir") && java.awt.Desktop.isDesktopSupported())
            java.awt.Desktop.getDesktop().browse(URI.create("http://127.0.0.1:"+PORT));
    }
    private static void atender(HttpExchange x) throws IOException {
        try {
            String path=x.getRequestURI().getPath(), method=x.getRequestMethod();
            if(path.equals("/healthz") && method.equals("GET")){enviar(x,200,Map.of("status","ok"));return;}
            String host=x.getRequestHeaders().getFirst("Host");
            String origin=x.getRequestHeaders().getFirst("Origin");
            String site=x.getRequestHeaders().getFirst("Sec-Fetch-Site");
            if (!URI.create(ORIGIN).getRawAuthority().equals(host) || ("cross-site".equals(site)&&(path.startsWith("/api/")||!method.equals("GET")))
                    || (origin!=null && !origin.equals(ORIGIN))) {
                enviar(x,403,Map.of("error","Abre SURTILADINO desde su dirección oficial para continuar.")); return;
            }
            AccesoLocal.Sesion sesion=ACCESO.buscar(x.getRequestHeaders().getFirst("Cookie"));
            if (!path.startsWith("/api/")) {
                if (!method.equals("GET")) { enviar(x,405,Map.of("error","Método no permitido.")); return; }
                if(path.equals("/") && sesion==null){headers(x);x.getResponseHeaders().set("Location","/login");x.sendResponseHeaders(302,-1);return;}
                String file=Map.of("/","index.html","/app.js","app.js","/crud.js","crud.js","/style.css","style.css","/login","login.html","/login.js","login.js").get(path);
                if (file==null) { enviar(x,404,Map.of("error","No encontrado.")); return; }
                byte[] body=Files.readAllBytes(Path.of(System.getProperty("pos.web","web"),file));
                headers(x); x.getResponseHeaders().set("Content-Type",file.endsWith("js")?"text/javascript; charset=utf-8":file.endsWith("css")?"text/css; charset=utf-8":"text/html; charset=utf-8");
                x.sendResponseHeaders(200,body.length); x.getResponseBody().write(body); return;
            }
            if (path.equals("/api/session") && method.equals("GET")) {
                enviar(x,200,sesion==null?Map.of("authenticated",false,"setupRequired",!CLOUD&&!ACCESO.configurado()):Map.of("authenticated",true,"token",sesion.csrf,"usuario",sesion.usuario));return;
            }
            boolean login=method.equals("POST") && (path.equals("/api/login")||path.equals("/api/setup"));
            if(method.equals("POST") && !ORIGIN.equals(origin)){enviar(x,403,Map.of("error","Abre el formulario desde SURTILADINO."));return;}
            if(!login){
                if(sesion==null){enviar(x,401,Map.of("error","Inicia sesión para continuar."));return;}
                if(!sesion.csrf.equals(x.getRequestHeaders().getFirst("X-Surti-Token"))){enviar(x,403,Map.of("error","Recarga SURTILADINO para continuar."));return;}
            }
            if (!(method.equals("GET") || method.equals("POST"))) { enviar(x,405,Map.of("error","Método no permitido.")); return; }
            Map<String,String> p=params(x.getRequestURI().getRawQuery());
            if (method.equals("POST")) {
                byte[] bytes=x.getRequestBody().readNBytes(65537);
                if (bytes.length>65536) throw new IllegalArgumentException("Solicitud demasiado grande.");
                p=params(new String(bytes,StandardCharsets.UTF_8));
            }
            if(login){
                if(ACCESO.bloqueado()){x.getResponseHeaders().set("Retry-After","60");enviar(x,429,Map.of("error","Demasiados intentos. Espera un minuto antes de volver a intentar."));return;}
                String clave=p.getOrDefault("clave","");
                if(clave.length()>128)throw new IllegalArgumentException("La contraseña admite hasta 128 caracteres.");
                boolean crear=path.equals("/api/setup");
                if(crear && !clave.equals(p.get("confirmacion")))throw new IllegalArgumentException("Las contraseñas no coinciden.");
                AccesoLocal.Sesion nueva=ACCESO.entrar(p.getOrDefault("usuario","").trim(),clave,crear);
                if(nueva==null){enviar(x,401,Map.of("error","Usuario o contraseña incorrectos."));return;}
                ACCESO.salir(sesion);
                x.getResponseHeaders().set("Set-Cookie",cookie(nueva.id,false));
                enviar(x,200,Map.of("ok",true));return;
            }
            if(path.equals("/api/logout") && method.equals("POST")){
                ACCESO.salir(sesion);String sid=sesion.id;OFERTAS.entrySet().removeIf(e->e.getValue().sesion.equals(sid));
                x.getResponseHeaders().set("Set-Cookie",cookie("",true));
                enviar(x,200,Map.of("ok",true));return;
            }
            Object result;
            try (Connection db=ConexionSupabase.abrir()) {
                PosDatos d=new PosDatos(db);
                CrudDatos crud=new CrudDatos(db);
                switch(method+" "+path) {
                    case "GET /api/data":
                        result=Map.of("productos",d.productos(),"clientes",d.clientes(),"convenios",d.convenios(),"maestros",crud.maestros(),"fecha",LocalDate.now(ZoneId.of("America/Bogota")).toString()); break;
                    case "GET /api/facturas":
                        result=d.consultar("select r.id,to_char(r.fecha at time zone 'America/Bogota','DD/MM/YYYY HH24:MI'),r.cliente_factura,r.total,v.estado,v.reemplazada_por,v.motivo_anulacion from pos.resumen_ventas r join pos.ventas v on v.id=r.id order by r.id desc limit 200"); break;
                    case "GET /api/venta": result=crud.venta(numero(p,"id")); break;
                    case "GET /api/factura": result=Map.of("texto",d.factura(numero(p,"id"))); break;
                    case "GET /api/kardex":
                        result=d.consultar("select m.id,to_char(m.fecha at time zone 'America/Bogota','DD/MM/YYYY HH24:MI'),p.nombre,m.tipo,m.cantidad,m.saldo,m.observacion,m.reversa_de,r.id,m.producto_id from pos.movimientos_kardex m join pos.productos p on p.id=m.producto_id left join pos.movimientos_kardex r on r.reversa_de=m.id where (?=0 or m.producto_id=?) order by m.id desc limit 300",numero(p,"producto"),numero(p,"producto")); break;
                    case "GET /api/resumen":
                        LocalDate dia=LocalDate.parse(p.getOrDefault("fecha",""));
                        result=d.consultar("select count(*),coalesce(sum(subtotal),0),coalesce(sum(descuento),0),coalesce(sum(iva),0),coalesce(sum(total),0),coalesce(sum(base-costo),0) from pos.resumen_ventas r where (fecha at time zone 'America/Bogota')::date=? and exists(select 1 from pos.ventas v where v.id=r.id and v.estado='VIGENTE')",java.sql.Date.valueOf(dia)).get(0); break;
                    case "POST /api/clientes":
                        result=Map.of("id",crud.cliente(p)); break;
                    case "POST /api/productos": result=Map.of("id",crud.producto(p)); break;
                    case "POST /api/convenios": result=Map.of("id",crud.convenio(p)); break;
                    case "POST /api/eliminar": result=Map.of("mensaje",crud.eliminar(campo(p,"modulo",20),numero(p,"id"),numero(p,"version"))); break;
                    case "POST /api/movimiento": result=Map.of("id",crud.crearMovimiento(p)); break;
                    case "POST /api/corregir-movimiento": result=Map.of("id",crud.corregirMovimiento(numero(p,"id"),entero(p,"cantidad"),campo(p,"motivo",250))); break;
                    case "POST /api/anular-movimiento": result=Map.of("id",crud.corregirMovimiento(numero(p,"id"),null,campo(p,"motivo",250))); break;
                    case "POST /api/anular-venta": crud.anular(numero(p,"id"),campo(p,"motivo",250));result=Map.of("ok",true);break;
                    case "POST /api/entrada": d.entrada(numero(p,"producto"),entero(p,"cantidad")); result=Map.of("ok",true); break;
                    case "POST /api/convenio": d.porcentajeMayorista(new BigDecimal(campo(p,"porcentaje",10))); result=Map.of("ok",true); break;
                    case "POST /api/cotizar":
                        Map<Long,Integer> items=new TreeMap<>();
                        for (Map.Entry<String,String> e:p.entrySet()) if(e.getKey().startsWith("item_")) items.put(Long.valueOf(e.getKey().substring(5)),Integer.valueOf(e.getValue()));
                        long original=numero(p,"original");
                        PosDatos.Venta v=original>0?d.cotizarCorreccion(original,numero(p,"cliente"),entero(p,"corriente"),numero(p,"convenio"),items):d.cotizar(numero(p,"cliente"),entero(p,"corriente"),numero(p,"convenio"),items);
                        String motivo=original>0?campo(p,"motivo",250):"";
                        OFERTAS.entrySet().removeIf(e->e.getValue().vence<System.currentTimeMillis());
                        if(OFERTAS.size()>500) throw new IllegalArgumentException("Hay demasiadas cotizaciones abiertas. Espera unos minutos.");
                        String key=UUID.randomUUID().toString(); OFERTAS.put(key,new Oferta(v,sesion.id,motivo));
                        List<Object> lineas=new ArrayList<>();
                        for(PosDatos.Linea l:v.lineas) lineas.add(Map.of("nombre",l.nombre,"cantidad",l.cantidad,"subtotal",l.subtotal,"descuento",l.descuento,"iva",l.iva,"total",l.total));
                        result=Map.of("token",key,"cliente",v.nombreCliente,"total",v.total,"lineas",lineas); break;
                    case "POST /api/vender":
                        Oferta o=OFERTAS.get(p.get("cotizacion"));
                        if(o==null || !o.sesion.equals(sesion.id) || o.vence<System.currentTimeMillis()) throw new IllegalArgumentException("La cotización venció. Revisa la venta otra vez.");
                        synchronized(o) { if(o.factura==null) o.factura=crud.confirmar(o.venta,o.motivo); result=Map.of("id",o.factura); } break;
                    default: enviar(x,404,Map.of("error","Acción no encontrada.")); return;
                }
            }
            enviar(x,200,result);
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            enviar(x,400,Map.of("error",e instanceof NumberFormatException?"Revisa los números ingresados.":e instanceof java.time.DateTimeException?"Selecciona una fecha válida.":e.getMessage()==null?"Revisa los datos ingresados.":e.getMessage()));
        } catch (SQLException e) {
            String state=e.getSQLState();
            enviar(x,409,Map.of("error","23505".equals(state)?"Ese registro ya existe o el movimiento ya fue revertido.":"P0001".equals(state)?"El cambio no es válido para el inventario actual. Comprueba las existencias: no se puede retirar más de lo disponible ni revertir dos veces un movimiento.":"23503".equals(state)?"El registro tiene operaciones asociadas o ya no existe. Actualiza la pantalla.":"No se pudo completar la operación. Revisa los datos, la conexión y actualiza antes de intentar nuevamente."));
            System.err.println("Operación no completada. SQLState: "+state);
        } catch (Exception e) {
            enviar(x,500,Map.of("error","No se pudo conectar o cargar la información. Revisa internet y el archivo local de conexión."));
            System.err.println("Solicitud no completada: "+e.getClass().getSimpleName());
        } finally { x.close(); }
    }
    private static String campo(Map<String,String> p,String k,int max) {
        String v=p.getOrDefault(k,"").trim(); if(v.isEmpty()||v.length()>max) throw new IllegalArgumentException("Revisa el campo "+k+"."); return v;
    }
    private static long numero(Map<String,String> p,String k) { return Long.parseLong(p.getOrDefault(k,"0")); }
    private static int entero(Map<String,String> p,String k) { return Integer.parseInt(p.getOrDefault(k,"0")); }
    private static Map<String,String> params(String s) {
        Map<String,String> p=new HashMap<>(); if(s==null||s.isEmpty()) return p;
        for(String pair:s.split("&")) { String[] a=pair.split("=",2); p.put(URLDecoder.decode(a[0],StandardCharsets.UTF_8),URLDecoder.decode(a.length==2?a[1]:"",StandardCharsets.UTF_8)); } return p;
    }
    private static void headers(HttpExchange x) {
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        x.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        x.getResponseHeaders().set("Referrer-Policy","no-referrer");
        if(CLOUD)x.getResponseHeaders().set("Strict-Transport-Security","max-age=31536000");
    }
    private static void enviar(HttpExchange x,int status,Object obj) throws IOException {
        headers(x); x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        byte[] b=json(obj).getBytes(StandardCharsets.UTF_8); x.sendResponseHeaders(status,b.length); x.getResponseBody().write(b);
    }
    static String json(Object o) {
        if(o==null) return "null"; if(o instanceof Number||o instanceof Boolean) return o.toString();
        if(o instanceof Map) { List<String> a=new ArrayList<>(); ((Map<?,?>)o).forEach((k,v)->a.add(json(k.toString())+":"+json(v))); return "{"+String.join(",",a)+"}"; }
        if(o instanceof Object[]) return json(Arrays.asList((Object[])o));
        if(o instanceof Iterable) { List<String>a=new ArrayList<>(); for(Object v:(Iterable<?>)o)a.add(json(v)); return "["+String.join(",",a)+"]"; }
        StringBuilder s=new StringBuilder("\""); for(char c:o.toString().toCharArray()) { if(c=='"'||c=='\\')s.append('\\').append(c); else if(c<32)s.append(String.format("\\u%04x",(int)c)); else s.append(c); } return s.append('"').toString();
    }
}
