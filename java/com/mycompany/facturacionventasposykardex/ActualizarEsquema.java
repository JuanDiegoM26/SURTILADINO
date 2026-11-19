package com.mycompany.facturacionventasposykardex;
import java.nio.file.*;
import java.sql.*;
/** Actualizador explícito; no se ejecuta al abrir el programa. */
public class ActualizarEsquema {
 public static void main(String[] args)throws Exception{
  try(Connection c=ConexionSupabase.abrir()){
   PosDatos d=new PosDatos(c);
   if(d.consultar("select to_regclass('pos.versiones_esquema')").get(0)[0]!=null && !d.consultar("select version from pos.versiones_esquema where version=2").isEmpty()){System.out.println("Esquema 2 ya instalado.");return;}
   try(Statement s=c.createStatement()){s.execute(Files.readString(Path.of("sql/02_crud.sql")));}
   System.out.println("Esquema CRUD 2 instalado. Registros existentes conservados.");
  }
 }
}
