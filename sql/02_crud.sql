-- Ampliación conservando registros y movimientos existentes. Ejecutar una sola vez.
begin;
create table if not exists pos.versiones_esquema (version integer primary key, aplicada_en timestamptz not null default now());
insert into pos.versiones_esquema(version) values (2);
alter table pos.productos add column version integer not null default 1;
alter table pos.clientes add column activo boolean not null default true, add column version integer not null default 1;
alter table pos.convenios add column activo boolean not null default true, add column version integer not null default 1, add column tipo varchar(20);
update pos.convenios set tipo=codigo;
alter table pos.convenios alter column tipo set not null;
alter table pos.convenios drop constraint convenios_codigo_check, drop constraint convenios_check;
alter table pos.convenios add constraint tipo_convenio_valido check(tipo in ('SIN_CONVENIO','DOS_POR_UNO','MAYORISTA'));
alter table pos.convenios add constraint descuento_segun_tipo check(tipo='MAYORISTA' or porcentaje=0);
alter table pos.ventas add column convenio_factura varchar(80), add column estado varchar(10) not null default 'VIGENTE' check(estado in ('VIGENTE','ANULADA')),
 add column motivo_anulacion varchar(250), add column anulada_en timestamptz, add column reemplazada_por bigint references pos.ventas(id);
update pos.ventas v set convenio_factura=c.nombre from pos.convenios c where c.id=v.convenio_id;
alter table pos.ventas alter column convenio_factura set not null;
alter table pos.movimientos_kardex alter column tipo type varchar(20), add column reversa_de bigint unique references pos.movimientos_kardex(id);
alter table pos.movimientos_kardex drop constraint movimientos_kardex_tipo_check, drop constraint movimientos_kardex_check;
alter table pos.movimientos_kardex add constraint tipo_movimiento_valido check(tipo in ('INICIAL','ENTRADA','SALIDA','AJUSTE_ENTRADA','AJUSTE_SALIDA','DEVOLUCION'));
alter table pos.movimientos_kardex add constraint detalle_segun_tipo check((tipo='SALIDA' and detalle_venta_id is not null) or (tipo<>'SALIDA' and detalle_venta_id is null));
create or replace function pos.actualizar_stock() returns trigger language plpgsql set search_path='' as $$
declare actual integer; habilitado boolean; unidades integer; original pos.movimientos_kardex%rowtype;
begin
 select stock,activo into actual,habilitado from pos.productos where id=new.producto_id for update;
 if not found then raise exception 'El producto no existe'; end if;
 if new.reversa_de is null and not habilitado then raise exception 'El producto está archivado'; end if;
 if new.reversa_de is not null then
   select * into original from pos.movimientos_kardex where id=new.reversa_de for update;
   if not found or original.producto_id<>new.producto_id or original.cantidad<>new.cantidad or original.reversa_de is not null then raise exception 'Reversión inválida'; end if;
   if original.tipo='SALIDA' then
     if new.tipo<>'DEVOLUCION' or not exists(select 1 from pos.detalle_ventas d join pos.ventas v on v.id=d.venta_id where d.id=original.detalle_venta_id and v.estado='ANULADA') then raise exception 'Debe anular la venta antes de devolver existencias'; end if;
   elsif original.tipo in ('INICIAL','ENTRADA','AJUSTE_ENTRADA') then
     if new.tipo<>'AJUSTE_SALIDA' then raise exception 'La reversión debe descontar unidades'; end if;
   elsif original.tipo='AJUSTE_SALIDA' then
     if new.tipo<>'AJUSTE_ENTRADA' then raise exception 'La reversión debe devolver unidades'; end if;
   else raise exception 'Movimiento no reversible'; end if;
 elsif new.tipo='DEVOLUCION' then raise exception 'Una devolución necesita su movimiento original';
 end if;
 if new.tipo='INICIAL' and exists(select 1 from pos.movimientos_kardex where producto_id=new.producto_id) then raise exception 'El producto ya tiene movimientos'; end if;
 if new.tipo='SALIDA' then
   select cantidad into unidades from pos.detalle_ventas where id=new.detalle_venta_id and producto_id=new.producto_id;
   if not found or unidades<>new.cantidad then raise exception 'La salida debe coincidir con la venta'; end if;
 end if;
 if new.tipo in ('SALIDA','AJUSTE_SALIDA') then
   if new.cantidad>actual then raise exception 'Stock insuficiente para este cambio'; end if;
   new.saldo:=actual-new.cantidad;
 else new.saldo:=actual+new.cantidad;
 end if;
 update pos.productos set stock=new.saldo where id=new.producto_id;
 return new;
end; $$;
create index ventas_estado_fecha on pos.ventas(estado,fecha);
alter table pos.versiones_esquema enable row level security;
revoke all on pos.versiones_esquema from public,anon,authenticated;
revoke all on function pos.actualizar_stock() from public,anon,authenticated;
commit;
