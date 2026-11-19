package com.mycompany.facturacionventasposykardex;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Primera conexion local: la configuracion se guarda fuera del codigo fuente. */
public class ConexionSupabase {
    public static Connection abrir() throws IOException, SQLException {
        String rutaPredeterminada="cloud".equals(System.getenv("SURTI_MODE"))?"/etc/secrets/conexion.properties":"conexion.properties";
        Path archivo = Path.of(System.getProperty("pos.config",System.getenv().getOrDefault("SURTI_CONFIG_FILE",rutaPredeterminada)));
        if (!Files.isRegularFile(archivo)) {
            throw new IOException("Falta conexion.properties. Copia conexion.ejemplo.properties y completa sus datos.");
        }
        Properties config = new Properties();
        try (InputStream entrada = Files.newInputStream(archivo)) {
            config.load(entrada);
        }
        String host = obligatorio(config, "host");
        String puerto = config.getProperty("puerto", "5432").trim();
        if (!host.matches("[a-zA-Z0-9.-]+") || !puerto.matches("[0-9]{1,5}")) {
            throw new IOException("Revisa el host y puerto del Session pooler.");
        }
        Properties conexion = new Properties();
        conexion.setProperty("user", obligatorio(config, "usuario"));
        String clave = config.getProperty("clave");
        if (clave == null || clave.isEmpty()) throw new IOException("Falta la clave de la base de datos en el archivo local.");
        conexion.setProperty("password", clave);
        conexion.setProperty("sslmode", "require");
        conexion.setProperty("connectTimeout", "10");
        conexion.setProperty("socketTimeout", "20");
        conexion.setProperty("ApplicationName", "SURTILADINO");
        return DriverManager.getConnection("jdbc:postgresql://" + host + ":" + puerto + "/postgres", conexion);
    }

    private static String obligatorio(Properties config, String campo) throws IOException {
        String valor = config.getProperty(campo, "").trim();
        if (valor.isEmpty()) throw new IOException("Falta el campo " + campo + " en conexion.properties.");
        return valor;
    }
}
