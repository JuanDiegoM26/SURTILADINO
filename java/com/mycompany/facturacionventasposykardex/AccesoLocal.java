package com.mycompany.facturacionventasposykardex;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Cuenta del administrador de esta instalación local. No es una cuenta Supabase. */
final class AccesoLocal {
    private final Path archivo;
    private final boolean soloLectura;
    private final Map<String,Sesion> sesiones=new HashMap<>();
    private int fallos=0;
    private long bloqueo=0;
    static final class Sesion {
        final String id=aleatorio(), csrf=aleatorio(), usuario;
        final long creada=System.currentTimeMillis();
        long ultimo=creada;
        Sesion(String u){usuario=u;}
        boolean vencida(long ahora){return ahora-ultimo>=1800000L || ahora-creada>=28800000L;}
    }
    AccesoLocal(Path p){this(p,false);}
    AccesoLocal(Path p,boolean cloud){archivo=p;soloLectura=cloud;}
    void validarCuenta()throws IOException {
        Properties p=new Properties();try(InputStream in=Files.newInputStream(archivo)){p.load(in);}
        try{
            if(p.getProperty("usuario","").isBlank() || Base64.getDecoder().decode(p.getProperty("sal","")).length<16 || Base64.getDecoder().decode(p.getProperty("hash","")).length!=32 || !"600000".equals(p.getProperty("iteraciones")))throw new IllegalArgumentException();
        }catch(IllegalArgumentException e){throw new IOException("Revisa el archivo privado de acceso de SURTILADINO.");}
    }
    synchronized boolean configurado(){return Files.exists(archivo);}
    synchronized Sesion buscar(String cookie){
        long ahora=System.currentTimeMillis();
        sesiones.values().removeIf(s->s.vencida(ahora));
        if(cookie==null)return null;
        for(String parte:cookie.split(";")){
            String[] par=parte.trim().split("=",2);
            if(par.length==2 && par[0].equals("surti_session")){
                Sesion s=sesiones.get(par[1]);if(s!=null)s.ultimo=ahora;return s;
            }
        }
        return null;
    }
    synchronized void salir(Sesion s){if(s!=null)sesiones.remove(s.id);}
    synchronized boolean bloqueado(){if(bloqueo!=0 && System.currentTimeMillis()>=bloqueo){fallos=0;bloqueo=0;}return bloqueo>System.currentTimeMillis();}
    synchronized Sesion entrar(String usuario,String clave,boolean crear) throws Exception {
        if(bloqueado())return null;
        if(crear){
            if(soloLectura)throw new IllegalArgumentException("La cuenta se configura desde el servidor. No hay registro público.");
            if(configurado())throw new IllegalArgumentException("La cuenta ya fue creada. Inicia sesión.");
            if(!usuario.matches("[a-zA-Z0-9._-]{3,40}"))throw new IllegalArgumentException("Usa un usuario de 3 a 40 letras, números, puntos o guiones.");
            if(clave.length()<12 || clave.length()>128)throw new IllegalArgumentException("Elige una contraseña de 12 a 128 caracteres.");
            byte[] sal=new byte[24];new SecureRandom().nextBytes(sal);
            Properties p=new Properties();p.setProperty("usuario",usuario);p.setProperty("sal",Base64.getEncoder().encodeToString(sal));
            p.setProperty("hash",Base64.getEncoder().encodeToString(hash(clave,sal)));p.setProperty("iteraciones","600000");
            // CREATE_NEW impide sobrescribir una cuenta existente.
            try(OutputStream out=Files.newOutputStream(archivo,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){p.store(out,"Cuenta local SURTILADINO. No compartir.");}
        } else {
            if(!configurado())return null;
            Properties p=new Properties();try(InputStream in=Files.newInputStream(archivo)){p.load(in);}
            byte[] esperado=Base64.getDecoder().decode(p.getProperty("hash"));
            byte[] recibido=hash(clave,Base64.getDecoder().decode(p.getProperty("sal")));
            if(!MessageDigest.isEqual(esperado,recibido) || !p.getProperty("usuario").equals(usuario)){
                if(++fallos>=5)bloqueo=System.currentTimeMillis()+60000L;return null;
            }
        }
        fallos=0;bloqueo=0;
        sesiones.values().removeIf(s->s.vencida(System.currentTimeMillis()));
        if(sesiones.size()>=32)sesiones.clear();
        Sesion s=new Sesion(usuario);sesiones.put(s.id,s);return s;
    }
    private static byte[] hash(String clave,byte[] sal) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(clave.toCharArray(),sal,600000,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    private static String aleatorio(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
}
