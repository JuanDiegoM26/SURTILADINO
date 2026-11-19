package com.mycompany.facturacionventasposykardex;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Prueba de lectura: no crea ventas ni modifica existencias. */
public class ProbarConexion {
    public static void main(String[] args) {
        try (Connection conexion = ConexionSupabase.abrir()) {
            conexion.setReadOnly(true);
            String sql = "select codigo, nombre, precio_venta, stock from pos.productos order by id";
            try (PreparedStatement consulta = conexion.prepareStatement(sql);
                 ResultSet filas = consulta.executeQuery()) {
                System.out.println("Conexion correcta con PostgreSQL de Supabase.");
                while (filas.next()) {
                    System.out.println(filas.getString("codigo") + " | " + filas.getString("nombre")
                            + " | Precio: " + filas.getBigDecimal("precio_venta")
                            + " | Existencias: " + filas.getInt("stock"));
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (SQLException e) {
            System.err.println("No se completo la conexion o consulta. Estado SQL: " + e.getSQLState());
            System.err.println("Revisa el conector PostgreSQL, los datos del Session pooler y si creaste las tablas.");
            System.exit(1);
        }
    }
}
