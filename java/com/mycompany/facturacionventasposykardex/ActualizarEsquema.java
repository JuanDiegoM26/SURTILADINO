package com.mycompany.facturacionventasposykardex;
import java.nio.file.*;
import java.sql.*;
/** Actualizador explícito; no se ejecuta al abrir el programa. */
public class ActualizarEsquema {
 public static void main(String[] args)throws Exception{
  try(Connection c=ConexionSupabase.abrir()){
   PosDatos d=new PosDatos(c);
   if(d.consultar("select to_regclass('pos.versiones_esquema')").get(0)[0]==null || d.consultar("select version from pos.versiones_esquema where version=2").isEmpty()) {
    try(Statement s=c.createStatement()){s.execute(Files.readString(Path.of("sql/02_crud.sql")));}
   }
   if(!d.consultar("select version from pos.versiones_esquema where version=3").isEmpty()){System.out.println("Esquema 3 ya instalado; no se realizaron cambios.");return;}
   try(Statement s=c.createStatement()){s.execute(Files.readString(Path.of("sql/04_gestion_comercial.sql")));}
   System.out.println("Esquema comercial 3 instalado. Registros existentes conservados.");
  }
 }
}
