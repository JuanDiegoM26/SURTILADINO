# SURTILADINO · Gestión comercial v3

Sistema académico para una sede, desarrollado en Java, HTML, CSS y JavaScript.
PostgreSQL en Supabase conserva los datos; Render ejecuta el servidor y la página.
Diseño adaptado de la referencia Stitch: menú azul oscuro, acciones amarillas,
tarjetas, filtros, tablas, formularios y vista de venta con ticket lateral.

## Abrir la versión actualizada en tu computador

En la carpeta SURTILADINO del escritorio, abre ABRIR_SURTILADINO_WEB.cmd.
La versión 3.1 busca un puerto disponible si una versión anterior sigue abierta.
Usa la dirección que abra el programa (8787, 8788 u otro puerto disponible).
Conserva tu usuario y contraseña habituales; evita una pestaña de la versión vieja.
En NetBeans abre el pom.xml de esa misma carpeta y ejecuta el proyecto.
Necesitas internet para consultar Supabase.

## Actualizar el enlace de Render

La migración v3 ya se instaló el 19 de noviembre de 2026 en la base de datos de
SURTILADINO y se verificó que los registros anteriores conservaran sus valores.
No vuelvas a ejecutar 01_crear_base.sql, 02_crud.sql ni 04_gestion_comercial.sql.

1. Abre https://github.com/JuanDiegoM26/SURTILADINO.
2. Entra en Add file > Upload files.
3. Desde la carpeta de entrega, arrastra las carpetas java, web, sql y lib y los
   archivos Dockerfile, pom.xml, render.yaml y README.md. Conserva las carpetas;
   no subas todos los archivos Java o HTML sueltos en la raíz.
4. Confirma los cambios con el mensaje “Actualizar interfaz y gestión comercial v3”.
5. Render debe iniciar el despliegue automático. Si no comienza, abre el servicio
   y pulsa Manual Deploy > Deploy latest commit. Espera el estado Live.
6. Abre https://surtiladino.onrender.com y recarga con Ctrl+F5.

Conserva los .gitignore y .dockerignore que ya están en GitHub. No cambies los
Secret Files de Render: conexion.properties y acceso.local.properties.
La variable SURTI_MODE sigue en cloud y Health Check Path sigue en /healthz.
El archivo ZIP es para transportar la entrega; extrae su contenido antes de subir.
Nunca subas archivos con contraseñas, respaldos ni la carpeta verificacion.

## Qué incluye

- Inicio: cifras reales de productos, stock, alertas, valor del inventario y ventas.
- Productos: crear, consultar, editar y eliminar o archivar si tiene movimientos.
  Categoría, proveedor, marca, presentación, ubicación, código de barras,
  imagen HTTPS opcional, costo, precio base, IVA del ejercicio y mínimos/máximos.
- Inventario: entradas, conteos, salidas por merma/rotura/vencimiento y lista de
  reposición. Los cambios de existencias pasan por el kárdex.
- Ventas: búsqueda o ingreso de código, carrito, cliente, convenios, impuestos
  por producto, registro del medio de pago, valor recibido y cambio en efectivo.
- Facturas: consulta, impresión/PDF, anulación y corrección con una nueva factura.
  Las operaciones históricas no se borran; las anulaciones compensan el stock.
- Clientes y convenios: gestión completa, contactos y archivo de registros usados.
- Kárdex: filtros por fechas, producto y tipo, responsable, documento y saldos.
  Correcciones y anulaciones de movimientos conservan la trazabilidad.
- Reportes: ventas, margen bruto, ticket promedio, categorías, productos vendidos,
  medios de pago y pérdidas registradas. Exportación CSV e impresión/PDF.
- Configuración: datos de la empresa/sede, categorías, proveedores y auditoría.
- Importación CSV de productos nuevos mediante plantilla (máximo 100 por carga).
- Login con contraseña protegida, sesión, control de operaciones y consultas
  preparadas. El navegador no recibe credenciales de PostgreSQL.

## Cómo comenzar

1. Configuración: completa los datos reales de la empresa y crea categorías
   y proveedores. No se inventaron nombres, NIT, teléfonos ni direcciones.
2. Productos > Editar: asigna categoría/proveedor, ubicación, mínimos y máximos.
   Los productos anteriores quedaron con mínimo 5, máximo objetivo 100 e IVA 19 %;
   son valores iniciales editables, no una recomendación tributaria.
3. Inventario: registra entradas con su documento de referencia.
4. Ventas: agrega productos, revisa cliente, convenio y pago; confirma la venta.
5. Consulta su comprobante en Facturas, el stock en Inventario y el detalle en Kárdex.

## Alcance y reglas

Una sede y una cuenta administradora. Los comprobantes son académicos, no DIAN.
Tarjeta, transferencia, Nequi/Daviplata y QR son registros de pagos recibidos:
no conectan ni cobran a un banco. El ingreso por código admite teclado o lector
que escriba como teclado; no requiere controladores de dispositivos específicos.
Las ventas suspendidas permanecen solo en la pestaña abierta y se pierden al cerrar
o recargar. No equivalen a pedidos guardados en la base de datos.
La gestión de vencimiento registra bajas manuales, sin seguimiento de lotes.
El costo mostrado es el costo unitario vigente del catálogo; no calcula promedio
ponderado ni costo FIFO. Cada venta conserva su costo e impuesto históricos.
La auditoría de la aplicación inicia con la actualización v3; los movimientos
anteriores muestran “Anterior a v3” porque no se conoce su responsable.
El kárdex muestra hasta 2.000 movimientos por rango y Facturas las últimas 200;
el historial completo permanece en Supabase.

## Consultas en Supabase

Abre SQL Editor > New query. Copia una consulta de sql/05_consultas_gestion.sql
y pulsa Run. Son consultas de lectura para revisar productos, ventas y movimientos.
No uses los archivos de creación para consultar ni reiniciar la base existente.

## Instalación en otra base de datos

Solo para una base nueva y vacía: ejecutar 01_crear_base.sql, después 02_crud.sql
y finalmente 04_gestion_comercial.sql. No hacerlo en la base de esta entrega.
Para otra instalación existente en v2, respaldar antes y ejecutar únicamente 04.
ActualizarEsquema aplica versiones pendientes; no se ejecuta automáticamente.

## Validación de esta versión

Compilación Java compatible con release 11 y sintaxis de JavaScript verificadas.
Pruebas aisladas de esquema, CRUD, impuestos, pagos, históricos, correcciones,
importación atómica y permisos de tablas. Prueba HTTP de doble confirmación
concurrente sin duplicar ventas y de rollback de operaciones fallidas.
Revisión en navegador de las diez secciones, formularios y ancho móvil de 390 px.
Los datos ficticios de las pruebas se eliminaron del espacio aislado de pruebas.
El código actualizado debe desplegarse en Render para que cambie el enlace público.
