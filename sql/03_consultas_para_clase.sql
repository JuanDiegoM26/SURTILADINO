-- SURTILADINO: consultas de lectura para practicar en Supabase.
-- Selecciona UNA consulta y pulsa Ejecutar / Run.
-- SELECT consulta datos; estas instrucciones no cambian registros.

-- 1. Productos, precio y existencias.
SELECT codigo, nombre, precio_venta, costo_unitario, stock, activo
FROM pos.productos
ORDER BY codigo;

-- 2. Buscar un producto por parte del nombre.
SELECT codigo, nombre, precio_venta, stock
FROM pos.productos
WHERE nombre ILIKE '%arroz%';

-- 3. Productos activos con pocas existencias (cambia el 5 si lo necesitas).
SELECT codigo, nombre, stock
FROM pos.productos
WHERE activo = true AND stock <= 5
ORDER BY stock;

-- 4. Clientes registrados, incluidos los archivados.
SELECT id, nombre, tipo_documento, documento, tipo_cliente, activo
FROM pos.clientes
ORDER BY id;

-- 5. Convenios y reglas de descuento.
SELECT codigo, nombre, tipo, porcentaje, activo
FROM pos.convenios
ORDER BY id;

-- 6. Ultimas ventas/facturas, con su estado.
SELECT v.id AS factura,
       v.fecha AT TIME ZONE 'America/Bogota' AS fecha_bogota,
       v.cliente_factura AS cliente,
       v.estado,
       r.total,
       v.reemplazada_por
FROM pos.ventas AS v
JOIN pos.resumen_ventas AS r ON r.id = v.id
ORDER BY v.id DESC
LIMIT 20;

-- 7. Productos de una factura. Reemplaza 1 por un numero existente.
SELECT producto_factura, cantidad, precio_unitario, descuento, iva, total
FROM pos.detalle_ventas
WHERE venta_id = 1
ORDER BY id;

-- 8. Ultimos movimientos del kardex.
SELECT m.id,
       m.fecha AT TIME ZONE 'America/Bogota' AS fecha_bogota,
       p.nombre AS producto,
       m.tipo, m.cantidad, m.saldo, m.observacion, m.reversa_de
FROM pos.movimientos_kardex AS m
JOIN pos.productos AS p ON p.id = m.producto_id
ORDER BY m.id DESC
LIMIT 50;

-- 9. Total de ventas vigentes de hoy en Bogota.
SELECT COUNT(*) AS cantidad_ventas,
       COALESCE(SUM(r.total), 0) AS total_facturado
FROM pos.resumen_ventas AS r
JOIN pos.ventas AS v ON v.id = r.id
WHERE v.estado = 'VIGENTE'
  AND (v.fecha AT TIME ZONE 'America/Bogota')::date
      = (now() AT TIME ZONE 'America/Bogota')::date;

-- 10. Consultar una fecha concreta: cambia 2026-11-18 por la que quieras.
SELECT v.id, v.cliente_factura, v.estado, r.total
FROM pos.ventas AS v
JOIN pos.resumen_ventas AS r ON r.id = v.id
WHERE (v.fecha AT TIME ZONE 'America/Bogota')::date = DATE '2026-11-18'
ORDER BY v.id;
