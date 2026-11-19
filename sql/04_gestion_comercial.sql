-- SURTILADINO v3. Ampliación de una sede; conserva datos e históricos.
-- Ejecutar UNA VEZ, antes de publicar el código v3. No ejecuta 01 ni 02.
begin;
set local lock_timeout = '8s';
insert into pos.versiones_esquema(version) values (3);

create table pos.categorias (
 id bigint generated always as identity primary key,
 nombre varchar(80) not null check(length(trim(nombre))>0),
 descripcion varchar(250) not null default '',
 activo boolean not null default true,
 version integer not null default 1
);
create unique index categorias_nombre_unico on pos.categorias(lower(nombre));
create table pos.proveedores (
 id bigint generated always as identity primary key,
 nombre varchar(150) not null check(length(trim(nombre))>0),
 documento varchar(30) not null unique check(length(trim(documento))>0),
 contacto varchar(100) not null default '',
 telefono varchar(40) not null default '',
 correo varchar(150) not null default '',
 direccion varchar(200) not null default '',
 activo boolean not null default true,
 version integer not null default 1
);
create table pos.configuracion (
 id integer primary key check(id=1),
 nombre varchar(150) not null default 'SURTILADINO',
 documento varchar(30) not null default '',
 direccion varchar(200) not null default '',
 ciudad varchar(100) not null default '',
 telefono varchar(40) not null default '',
 correo varchar(150) not null default '',
 sede varchar(100) not null default 'Sede principal',
 pie_factura varchar(250) not null default 'Gracias por tu compra.',
 version integer not null default 1
);
insert into pos.configuracion(id) values(1);

alter table pos.productos
 add column categoria_id bigint references pos.categorias(id),
 add column proveedor_id bigint references pos.proveedores(id),
 add column codigo_barras varchar(50),
 add column marca varchar(80) not null default '',
 add column presentacion varchar(100) not null default 'Unidad',
 add column ubicacion varchar(80) not null default '',
 add column stock_minimo integer not null default 5 check(stock_minimo>=0),
 add column stock_maximo integer not null default 100 check(stock_maximo>=stock_minimo),
 add column porcentaje_iva numeric(5,2) not null default 19 check(porcentaje_iva in(0,5,19)),
 add column imagen_url varchar(1000) not null default '';
create unique index productos_codigo_barras_unico on pos.productos(codigo_barras) where codigo_barras is not null;
create index productos_categoria on pos.productos(categoria_id);
create index productos_proveedor on pos.productos(proveedor_id);
alter table pos.clientes add column telefono varchar(40) not null default '',
 add column correo varchar(150) not null default '', add column direccion varchar(200) not null default '';
alter table pos.ventas
 add column metodo_pago varchar(20) not null default 'NO_REGISTRADO' check(metodo_pago in('NO_REGISTRADO','EFECTIVO','TARJETA','TRANSFERENCIA','NEQUI_DAVIPLATA','QR')),
 add column referencia_pago varchar(100) not null default '',
 add column recibido numeric(16,2) check(recibido>=0),
 add column cambio numeric(16,2) check(cambio>=0),
 add column responsable varchar(80) not null default 'Anterior a v3',
 add column empresa_factura text not null default 'SURTILADINO';
alter table pos.detalle_ventas add column categoria_factura varchar(80) not null default 'Sin categoría histórica';
alter table pos.movimientos_kardex
 add column responsable varchar(80) not null default 'Anterior a v3',
 add column documento_referencia varchar(100) not null default '',
 add column proveedor_id bigint references pos.proveedores(id),
 add column costo_registrado numeric(12,2) check(costo_registrado>=0),
 add column motivo_tipo varchar(20) not null default 'GENERAL' check(motivo_tipo in('GENERAL','COMPRA','CONTEO','MERMA','ROTURA','VENCIMIENTO','VENTA','DEVOLUCION'));
create index movimientos_proveedor on pos.movimientos_kardex(proveedor_id);
create index movimientos_fecha on pos.movimientos_kardex(fecha);

create table pos.auditoria (
 id bigint generated always as identity primary key,
 fecha timestamptz not null default now(),
 responsable varchar(80) not null,
 tabla varchar(80) not null,
 accion varchar(10) not null,
 registro bigint,
 anterior jsonb,
 nuevo jsonb
);
create function pos.auditar_cambio() returns trigger language plpgsql set search_path='' as $$
declare a jsonb; n jsonb;
begin
 if TG_OP<>'INSERT' then a:=to_jsonb(old); end if;
 if TG_OP<>'DELETE' then n:=to_jsonb(new); end if;
 insert into pos.auditoria(responsable,tabla,accion,registro,anterior,nuevo)
 values(coalesce(nullif(current_setting('surti.usuario',true),''),'Proceso interno'),TG_TABLE_NAME,TG_OP,coalesce((n->>'id')::bigint,(a->>'id')::bigint),a,n);
 return null;
end; $$;
create trigger auditoria_productos after insert or update or delete on pos.productos for each row execute function pos.auditar_cambio();
create trigger auditoria_clientes after insert or update or delete on pos.clientes for each row execute function pos.auditar_cambio();
create trigger auditoria_categorias after insert or update or delete on pos.categorias for each row execute function pos.auditar_cambio();
create trigger auditoria_proveedores after insert or update or delete on pos.proveedores for each row execute function pos.auditar_cambio();
create trigger auditoria_configuracion after update on pos.configuracion for each row execute function pos.auditar_cambio();
create trigger auditoria_ventas after insert or update on pos.ventas for each row execute function pos.auditar_cambio();
create trigger auditoria_convenios after insert or update or delete on pos.convenios for each row execute function pos.auditar_cambio();
create trigger conservar_auditoria before update or delete on pos.auditoria for each row execute function pos.proteger_kardex();
create function pos.completar_movimiento() returns trigger language plpgsql set search_path='' as $$
begin
 new.responsable:=coalesce(nullif(current_setting('surti.usuario',true),''),'Proceso interno');
 if new.costo_registrado is null then
  select costo_unitario into new.costo_registrado from pos.productos where id=new.producto_id;
 end if;
 if new.tipo='SALIDA' then new.motivo_tipo:='VENTA'; end if;
 if new.tipo='DEVOLUCION' then new.motivo_tipo:='DEVOLUCION'; end if;
 return new;
end; $$;
create trigger completar_datos before insert on pos.movimientos_kardex for each row execute function pos.completar_movimiento();
alter table pos.categorias enable row level security;
alter table pos.proveedores enable row level security;
alter table pos.configuracion enable row level security;
alter table pos.auditoria enable row level security;
revoke all on all tables in schema pos from public,anon,authenticated;
revoke all on all sequences in schema pos from public,anon,authenticated;
revoke all on all functions in schema pos from public,anon,authenticated;
commit;
