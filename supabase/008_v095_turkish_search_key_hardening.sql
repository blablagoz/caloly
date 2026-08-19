-- Caloly v0.9.5 - Make Turkish people search and username login accent/case tolerant.

create or replace function public.caloly_username_key(value text)
returns text
language sql
immutable
parallel safe
set search_path = pg_catalog
as $$
    select translate(
        lower(
            translate(
                normalize(coalesce(value, ''), NFC),
                'ÇĞİÖŞÜIçğıöşü',
                'CGIOSUIcgiosu'
            )
        ),
        U&'\0307',
        ''
    )
$$;

update public.login_identifiers
set username_key = public.caloly_username_key(username)
where username_key <> public.caloly_username_key(username);

reindex index public.profiles_username_key;

revoke all on function public.caloly_username_key(text) from public, anon, authenticated;

notify pgrst, 'reload schema';
