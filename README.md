# SURTILADINO

Punto de venta académico en Java, HTML, CSS y JavaScript con PostgreSQL en Supabase.
Incluye acceso con usuario y contraseña, productos, clientes, convenios, ventas,
facturas, kárdex y resumen por fecha. IVA simulado del 19 % para el ejercicio.

## Cómo se publica

**GitHub** guarda el código. **Render** ejecuta la página y el servidor Java.
**Supabase** conserva la base de datos. El profesor abrirá el enlace HTTPS de Render
desde su propio computador, sin instalar Java ni mantener encendido tu equipo.

### 1. Subir el código a GitHub

1. Inicia sesión en tu cuenta de GitHub.
2. Pulsa **+ > New repository** y escribe `SURTILADINO`.
3. Puedes dejarlo privado: Render admite repositorios privados y el enlace del
   sistema puede ser público con login. Si el profesor también necesita el código,
   dale acceso al repositorio o decide hacerlo público.
4. Usa **Add file > Upload files** (o el enlace de carga del repositorio vacío).
5. Sube el CONTENIDO de la carpeta preparada `SURTILADINO_publicar`.
   `Dockerfile`, `render.yaml`, `java`, `web` y `lib` deben quedar en la raíz.
6. Guarda los cambios con **Commit changes**.

La carpeta preparada no contiene contraseñas, cuentas, copias de respaldo ni
archivos de prueba. No subas la carpeta de trabajo completa del escritorio.

### 2. Crear el servicio en Render

1. Inicia sesión en Render y vincula tu cuenta de GitHub.
2. Selecciona **New > Web Service** y el repositorio `SURTILADINO`.
3. Configura:

| Campo | Valor |
|---|---|
| Name | `surtiladino` (Render puede pedir otro nombre si ya está ocupado) |
| Branch | La rama donde subiste el código, normalmente `main` |
| Root Directory | Vacío si Dockerfile está en la raíz |
| Language / Runtime | `Docker` |
| Dockerfile Path | `./Dockerfile` |
| Region | Virginia, si está disponible |
| Instance Type | `Free` |
| Health Check Path | `/healthz` |

4. Agrega la variable de entorno `SURTI_MODE` con valor `cloud`.
   El Dockerfile también establece este valor. Render proporciona automáticamente
   `PORT` y `RENDER_EXTERNAL_URL`: no necesitas inventar la dirección pública.
5. Agrega los dos archivos privados indicados abajo. Si el formulario inicial
   no ofrece Secret Files, crea el servicio, entra a **Environment**, agrégalos
   y vuelve a desplegar. Hasta que estén configurados, el servidor no iniciará.
6. Crea el servicio y espera que el despliegue aparezca **Live**.

### 3. Configurar los dos Secret Files

En el servicio de Render, entra a **Environment > Secret Files > Add Secret File**.
Usa los archivos de la instalación que ya funciona en el escritorio:

| Nombre exacto en Render | Archivo local | Contenido |
|---|---|---|
| `conexion.properties` | SURTILADINO del escritorio | Conexión privada a Supabase |
| `acceso.local.properties` | SURTILADINO del escritorio | Usuario y hash de la contraseña del sistema |

Abre cada archivo local con Bloc de notas y copia su contenido únicamente al
campo privado correspondiente de Render. No lo pegues en GitHub, en la descripción
del repositorio ni en un archivo público. No cambies sus nombres.

En un servicio Docker, Render monta esos archivos en `/etc/secrets/`. El programa
ya está preparado para leerlos allí. La cuenta permanecerá configurada cuando
Render reinicie la aplicación. El usuario y contraseña de acceso serán los mismos
que configuraste en la instalación local de la que tomaste el archivo de acceso.

El sitio público no permite que un visitante cree la primera cuenta. Si falta el
archivo privado de acceso, el servidor se detiene en vez de abrir un registro libre.

### 4. Comprobar la publicación

1. Abre la dirección HTTPS que Render muestre para TU servicio.
2. Inicia sesión y comprueba que aparecen los productos de Supabase.
3. Cierra sesión y verifica que los módulos no se puedan consultar sin ingresar.
4. Prueba el enlace en una ventana privada o en otro computador.
5. Entrega al profesor ese enlace y, por un medio privado, las credenciales que
   decidas compartir. La cuenta actual es de administrador: permite hacer cambios.

El plan gratuito puede suspender el servicio después de 15 minutos sin actividad.
El primer acceso posterior puede tardar cerca de un minuto. El reinicio cierra las
sesiones, pero los datos del negocio permanecen en Supabase. Abre el enlace antes
de la exposición para comprobar que terminó de despertar.

## Consultas sencillas en Supabase

1. Abre el proyecto **SURTILADINO** en Supabase.
2. En el menú izquierdo selecciona **SQL Editor / Editor SQL**.
3. Pulsa **New query / Nueva consulta**.
4. Pega UNA consulta, pulsa **Run / Ejecutar** y mira **Results / Resultados**.

```sql
SELECT codigo, nombre, precio_venta, stock
FROM pos.productos
ORDER BY codigo;
```

- `SELECT`: columnas que quieres ver.
- `FROM`: tabla que vas a consultar.
- `pos.productos`: esquema `pos`, tabla `productos`.
- `ORDER BY`: columna por la que ordenas el resultado.

Para buscar un producto:

```sql
SELECT codigo, nombre, precio_venta, stock
FROM pos.productos
WHERE nombre ILIKE '%arroz%';
```

`WHERE` filtra los registros. Cambia `arroz` por otra palabra.
`ILIKE` busca sin distinguir mayúsculas de minúsculas y `%` permite que haya
texto antes o después. `SELECT` no modifica los registros.

En `sql/03_consultas_para_clase.sql` hay diez ejemplos comentados. Ejecuta uno a
la vez. No ejecutes otra vez los scripts de creación para consultar datos.

## Base de datos

La base de SURTILADINO utilizada durante el desarrollo ya tiene los scripts 01 y 02.
Para una base completamente nueva: ejecutar primero `sql/01_crear_base.sql` y
después `sql/02_crud.sql`. No ejecutarlos sobre una base que ya tiene esas tablas.

## Ejecución local y estructura

- `java/`: servidor Java, acceso, consultas y operaciones transaccionales.
- `web/`: interfaz.
- `sql/`: esquema, ampliación de gestión y consultas de clase.
- `lib/`: controlador PostgreSQL JDBC 42.7.14, con su licencia incluida en el JAR.
- `Dockerfile`: compilación y ejecución en Render.
- `render.yaml`: configuración del servicio sin secretos.

En Windows con NetBeans instalado, puedes usar `ABRIR_SURTILADINO_WEB.cmd` y tus
archivos privados locales. El modo local continúa en `http://127.0.0.1:8787`.
No establezcas `SURTI_MODE=cloud` en el uso local normal.

La aplicación mantiene las sesiones y las cotizaciones pendientes en la memoria
de una instancia. Los reinicios requieren volver a ingresar y cotizar; las ventas
confirmadas quedan en Supabase. Tras una interrupción durante una confirmación,
consulta las facturas antes de repetir la venta.

Documentación oficial:
- https://render.com/docs/web-services
- https://render.com/docs/configure-environment-variables
- https://render.com/docs/free
- https://supabase.com/docs/guides/database/overview
