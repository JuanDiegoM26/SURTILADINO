-- Consultas de lectura. Selecciona UNA consulta y pulsa Ejecutar en Supabase.
-- No cambian los datos del negocio.

-- 1. Catálogo con categoría, proveedor y límites de stock.
select p.codigo, p.nombre, c.nombre as categoria, pr.nombre as proveedor,
       p.precio_venta, p.porcentaje_iva, p.stock, p.stock_minimo, p.stock_maximo
from pos.productos p
left join pos.categorias c on c.id=p.categoria_id
left join pos.proveedores pr on pr.id=p.proveedor_id
order by p.codigo;

-- 2. Productos que necesitan reposición.
select codigo, nombre, stock, stock_minimo, stock_maximo,
       greatest(stock_maximo-stock,0) as cantidad_sugerida
from pos.productos where activo and stock<=stock_minimo order by stock;

-- 3. Últimas ventas, pagos y comprobantes.
select v.id, v.fecha at time zone 'America/Bogota' as fecha,
       v.cliente_factura, r.total, v.metodo_pago, v.recibido, v.cambio,
       v.estado, v.responsable
from pos.ventas v join pos.resumen_ventas r on r.id=v.id
order by v.id desc limit 50;

-- 4. Historial de inventario (últimos 100 movimientos).
select m.id, m.fecha at time zone 'America/Bogota' as fecha,
       p.codigo,p.nombre,m.tipo,m.motivo_tipo,m.cantidad,m.saldo,
       m.responsable,m.documento_referencia,m.observacion
from pos.movimientos_kardex m join pos.productos p on p.id=m.producto_id
order by m.id desc limit 100;

-- 5. Cambios de datos maestros y ventas.
select fecha at time zone 'America/Bogota' as fecha,
       responsable, tabla, accion, registro
from pos.auditoria order by id desc limit 100;
