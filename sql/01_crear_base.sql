-- SURTILADINO: ventas y Kardex. Ejecutar UNA VEZ en un proyecto nuevo.
-- Todo se crea dentro de "pos", separado de otras tablas del proyecto.
-- Si el esquema ya existe, la transaccion se cancela sin borrar datos.
begin;
create schema pos;

create table pos.productos (
    id bigint generated always as identity primary key,
    codigo varchar(30) not null unique,
    nombre varchar(100) not null check (length(trim(nombre)) > 0),
    precio_venta numeric(12,2) not null check (precio_venta >= 0),
    costo_unitario numeric(12,2) not null check (costo_unitario >= 0),
    stock integer not null default 0 check (stock >= 0),
    activo boolean not null default true
);

create table pos.clientes (
    id bigint generated always as identity primary key,
    nombre varchar(150) not null check (length(trim(nombre)) > 0),
    tipo_documento varchar(10) not null check (tipo_documento in ('CEDULA', 'NIT')),
    documento varchar(30) not null check (length(trim(documento)) > 0),
    tipo_cliente varchar(12) not null check (tipo_cliente in ('Corriente', 'Mayorista')),
    creado_en timestamptz not null default now(),
    unique (tipo_documento, documento)
);

create table pos.convenios (
    id bigint generated always as identity primary key,
    codigo varchar(20) not null unique
        check (codigo in ('SIN_CONVENIO', 'DOS_POR_UNO', 'MAYORISTA')),
    nombre varchar(80) not null,
    porcentaje numeric(5,2) not null default 0 check (porcentaje between 0 and 100),
    check (codigo = 'MAYORISTA' or porcentaje = 0)
);

create table pos.ventas (
    id bigint generated always as identity primary key,
    fecha timestamptz not null default now(),
    cliente_id bigint references pos.clientes(id),
    numero_cliente_corriente integer check (numero_cliente_corriente > 0),
    -- Copias historicas: cambiar un cliente no cambia una factura anterior.
    cliente_factura varchar(150) not null,
    documento_factura varchar(50),
    convenio_id bigint not null references pos.convenios(id),
    porcentaje_aplicado numeric(5,2) not null default 0
        check (porcentaje_aplicado between 0 and 100),
    check ((cliente_id is not null and numero_cliente_corriente is null)
        or (cliente_id is null and numero_cliente_corriente is not null))
);

create table pos.detalle_ventas (
    id bigint generated always as identity primary key,
    venta_id bigint not null references pos.ventas(id),
    producto_id bigint not null references pos.productos(id),
    producto_factura varchar(100) not null,
    cantidad integer not null check (cantidad > 0),
    precio_unitario numeric(12,2) not null check (precio_unitario >= 0),
    costo_unitario numeric(12,2) not null check (costo_unitario >= 0),
    unidades_gratis integer not null default 0 check (unidades_gratis >= 0 and unidades_gratis <= cantidad),
    descuento numeric(16,2) not null default 0
        check (descuento >= 0 and descuento <= cantidad * precio_unitario),
    -- IVA academico simulado del programa original, no configuracion tributaria real.
    porcentaje_iva numeric(5,2) not null default 19 check (porcentaje_iva between 0 and 100),
    subtotal numeric(16,2) generated always as (cantidad * precio_unitario) stored,
    base numeric(16,2) generated always as (cantidad * precio_unitario - descuento) stored,
    iva numeric(16,2) generated always as
        (round((cantidad * precio_unitario - descuento) * porcentaje_iva / 100, 2)) stored,
    total numeric(16,2) generated always as
        (cantidad * precio_unitario - descuento
         + round((cantidad * precio_unitario - descuento) * porcentaje_iva / 100, 2)) stored,
    unique (venta_id, producto_id),
    unique (id, producto_id)
);

create table pos.movimientos_kardex (
    id bigint generated always as identity primary key,
    producto_id bigint not null references pos.productos(id),
    fecha timestamptz not null default now(),
    tipo varchar(10) not null check (tipo in ('INICIAL', 'ENTRADA', 'SALIDA')),
    cantidad integer not null check (cantidad > 0),
    saldo integer not null check (saldo >= 0),
    detalle_venta_id bigint unique,
    observacion varchar(250),
    foreign key (detalle_venta_id, producto_id) references pos.detalle_ventas(id, producto_id),
    check ((tipo = 'SALIDA' and detalle_venta_id is not null)
        or (tipo in ('INICIAL', 'ENTRADA') and detalle_venta_id is null))
);
create unique index un_inicial_por_producto
    on pos.movimientos_kardex(producto_id) where tipo = 'INICIAL';
create index kardex_por_producto on pos.movimientos_kardex(producto_id, id);
create index ventas_por_cliente on pos.ventas(cliente_id);

-- El movimiento actualiza el stock y calcula su saldo dentro de la misma transaccion.
-- El bloqueo del producto evita que dos ventas descuenten el mismo stock a la vez.
create function pos.actualizar_stock() returns trigger
language plpgsql set search_path = '' as $$
declare
    actual integer;
    unidades_vendidas integer;
begin
    select stock into actual from pos.productos where id = new.producto_id for update;
    if not found then raise exception 'El producto no existe'; end if;
    if new.tipo = 'INICIAL' and exists (
        select 1 from pos.movimientos_kardex where producto_id = new.producto_id
    ) then raise exception 'El producto ya tiene movimientos'; end if;
    if new.tipo = 'SALIDA' then
        select cantidad into unidades_vendidas from pos.detalle_ventas
            where id = new.detalle_venta_id and producto_id = new.producto_id;
        if not found or unidades_vendidas <> new.cantidad then
            raise exception 'La salida debe coincidir con el detalle de la venta';
        end if;
        if new.cantidad > actual then raise exception 'Stock insuficiente'; end if;
        new.saldo := actual - new.cantidad;
    else
        new.saldo := actual + new.cantidad;
    end if;
    update pos.productos set stock = new.saldo where id = new.producto_id;
    return new;
end;
$$;
create trigger registrar_stock before insert on pos.movimientos_kardex
    for each row execute function pos.actualizar_stock();

-- Los movimientos ya registrados no se modifican ni borran.
create function pos.proteger_kardex() returns trigger
language plpgsql set search_path = '' as $$
begin
    raise exception 'Un movimiento registrado no se modifica ni se borra';
end;
$$;
create trigger conservar_historial before update or delete on pos.movimientos_kardex
    for each row execute function pos.proteger_kardex();

create view pos.resumen_ventas with (security_invoker = true) as
select v.id, v.fecha, v.cliente_factura, count(d.id) as productos_distintos,
       coalesce(sum(d.subtotal), 0) as subtotal,
       coalesce(sum(d.descuento), 0) as descuento,
       coalesce(sum(d.base), 0) as base,
       coalesce(sum(d.iva), 0) as iva,
       coalesce(sum(d.total), 0) as total,
       coalesce(sum(d.cantidad * d.costo_unitario), 0) as costo
from pos.ventas v left join pos.detalle_ventas d on d.venta_id = v.id
group by v.id;

-- La primera conexion sera desde Java local, con un usuario autorizado.
-- No se habilita acceso anonimo desde una pagina web.
alter table pos.productos enable row level security;
alter table pos.clientes enable row level security;
alter table pos.convenios enable row level security;
alter table pos.ventas enable row level security;
alter table pos.detalle_ventas enable row level security;
alter table pos.movimientos_kardex enable row level security;
revoke all on schema pos from public, anon, authenticated;
revoke all on all tables in schema pos from public, anon, authenticated;
revoke all on all sequences in schema pos from public, anon, authenticated;
revoke all on all functions in schema pos from public, anon, authenticated;

-- Datos de demostracion tomados de los vectores del programa original.
insert into pos.productos (codigo, nombre, precio_venta, costo_unitario) values
    ('P001', 'Arroz', 5000, 3500), ('P002', 'Leche', 4000, 2800), ('P003', 'Aceite', 12000, 9000);
insert into pos.convenios (codigo, nombre, porcentaje) values
    ('SIN_CONVENIO', 'Sin convenio', 0), ('DOS_POR_UNO', '2x1', 0), ('MAYORISTA', 'Mayorista', 10);
insert into pos.movimientos_kardex (producto_id, tipo, cantidad, observacion)
select id, 'INICIAL', case codigo when 'P001' then 20 when 'P002' then 15 else 10 end,
       'Existencia inicial de demostracion del ejercicio'
from pos.productos;
commit;

select codigo, nombre, precio_venta, stock from pos.productos order by id;
