-- ============================================================================
-- 관제 스케줄러 - Supabase 스키마
--
-- Supabase 대시보드 > SQL Editor 에 이 파일 전체를 붙여넣고 실행하세요.
--
-- 설계 원칙
--   1. 근무표만 올라간다. 일정 / 인수인계 / 점검 기록은 기기 밖으로 나가지 않는다.
--   2. 친구가 아니면 아무것도 읽을 수 없다 (RLS 로 강제).
--   3. 초대 코드로 남을 검색할 수 없다. 코드 대조는 서버 함수 안에서만 이뤄진다.
-- ============================================================================

-- ---------------------------------------------------------------- 테이블

-- 프로필: 표시 이름과 초대 코드
create table if not exists public.profiles (
    id           uuid primary key references auth.users on delete cascade,
    display_name text not null default '',
    invite_code  text not null unique,
    created_at   timestamptz not null default now()
);

-- 친구 관계: 조회를 단순하게 하려고 양방향 2행으로 저장한다.
create table if not exists public.friendships (
    user_id    uuid not null references auth.users on delete cascade,
    friend_id  uuid not null references auth.users on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, friend_id),
    constraint no_self_friend check (user_id <> friend_id)
);

-- 공유 근무표: 패턴 자체를 올려두고 상대 앱이 날짜를 계산한다.
create table if not exists public.shift_shares (
    user_id          uuid primary key references auth.users on delete cascade,
    pattern_name     text not null default '',
    cycle_days       int  not null,
    anchor_epoch_day bigint not null,
    my_offset        int  not null default 0,
    cycle            jsonb not null,  -- ["주","주","오",...] 각 일차의 짧은 라벨
    types            jsonb not null,  -- [{"label":"주","name":"주간","start":"06:00","end":"14:00","color":"FF2E7D32"}]
    updated_at       timestamptz not null default now()
);

-- 연차 / 대타 등 특정 날짜의 근무 변경
create table if not exists public.shift_overrides (
    user_id   uuid not null references auth.users on delete cascade,
    epoch_day bigint not null,
    label     text not null,
    primary key (user_id, epoch_day)
);

-- ---------------------------------------------------------------- RLS 활성화

alter table public.profiles       enable row level security;
alter table public.friendships    enable row level security;
alter table public.shift_shares   enable row level security;
alter table public.shift_overrides enable row level security;

-- ---------------------------------------------------------------- 정책: profiles

drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles for select
using (
    id = auth.uid()
    or exists (
        select 1 from public.friendships f
        where f.user_id = auth.uid() and f.friend_id = profiles.id
    )
);

drop policy if exists profiles_insert on public.profiles;
create policy profiles_insert on public.profiles for insert
with check (id = auth.uid());

drop policy if exists profiles_update on public.profiles;
create policy profiles_update on public.profiles for update
using (id = auth.uid()) with check (id = auth.uid());

-- ---------------------------------------------------------------- 정책: friendships

drop policy if exists friendships_select on public.friendships;
create policy friendships_select on public.friendships for select
using (user_id = auth.uid());

-- 추가는 서버 함수(add_friend_by_code)로만 한다. 직접 insert 는 막는다.
drop policy if exists friendships_delete on public.friendships;
create policy friendships_delete on public.friendships for delete
using (user_id = auth.uid() or friend_id = auth.uid());

-- ---------------------------------------------------------------- 정책: shift_shares

drop policy if exists shares_select on public.shift_shares;
create policy shares_select on public.shift_shares for select
using (
    user_id = auth.uid()
    or exists (
        select 1 from public.friendships f
        where f.user_id = auth.uid() and f.friend_id = shift_shares.user_id
    )
);

drop policy if exists shares_write on public.shift_shares;
create policy shares_write on public.shift_shares for all
using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---------------------------------------------------------------- 정책: shift_overrides

drop policy if exists overrides_select on public.shift_overrides;
create policy overrides_select on public.shift_overrides for select
using (
    user_id = auth.uid()
    or exists (
        select 1 from public.friendships f
        where f.user_id = auth.uid() and f.friend_id = shift_overrides.user_id
    )
);

drop policy if exists overrides_write on public.shift_overrides;
create policy overrides_write on public.shift_overrides for all
using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---------------------------------------------------------------- 가입 시 프로필 자동 생성

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    code text;
begin
    -- 헷갈리는 문자를 뺀 8자리 코드를 중복되지 않을 때까지 뽑는다.
    loop
        code := (
            select string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789',
                                     (floor(random() * 32) + 1)::int, 1), '')
            from generate_series(1, 8)
        );
        exit when not exists (select 1 from public.profiles where invite_code = code);
    end loop;

    insert into public.profiles (id, display_name, invite_code)
    values (
        new.id,
        coalesce(
            new.raw_user_meta_data->>'name',
            new.raw_user_meta_data->>'full_name',
            new.raw_user_meta_data->>'nickname',
            ''
        ),
        code
    );
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------- 초대 코드로 친구 추가
--
-- SECURITY DEFINER 라서 코드가 맞을 때만 상대 id 를 돌려준다.
-- 클라이언트는 profiles 를 직접 조회할 수 없으므로 코드 무작위 대입으로
-- 남의 계정을 찾아내는 것을 막는다.

create or replace function public.add_friend_by_code(code text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    target uuid;
    me     uuid := auth.uid();
begin
    if me is null then
        raise exception 'not_authenticated';
    end if;

    select id into target
    from public.profiles
    where invite_code = upper(regexp_replace(code, '[^A-Za-z0-9]', '', 'g'));

    if target is null then
        raise exception 'invalid_code';
    end if;

    if target = me then
        raise exception 'self_code';
    end if;

    insert into public.friendships (user_id, friend_id) values (me, target)
    on conflict do nothing;
    insert into public.friendships (user_id, friend_id) values (target, me)
    on conflict do nothing;

    return target;
end;
$$;

-- ---------------------------------------------------------------- 친구 끊기 (양방향)

create or replace function public.remove_friend(target uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    me uuid := auth.uid();
begin
    if me is null then
        raise exception 'not_authenticated';
    end if;
    delete from public.friendships
    where (user_id = me and friend_id = target)
       or (user_id = target and friend_id = me);
end;
$$;

-- ---------------------------------------------------------------- 친구 목록 + 근무표 한 번에 조회

create or replace function public.list_friend_schedules()
returns table (
    friend_id        uuid,
    display_name     text,
    invite_code      text,
    pattern_name     text,
    cycle_days       int,
    anchor_epoch_day bigint,
    my_offset        int,
    cycle            jsonb,
    types            jsonb,
    updated_at       timestamptz
)
language sql
stable
security invoker
as $$
    select p.id, p.display_name, p.invite_code,
           coalesce(s.pattern_name, ''), coalesce(s.cycle_days, 0),
           coalesce(s.anchor_epoch_day, 0), coalesce(s.my_offset, 0),
           coalesce(s.cycle, '[]'::jsonb), coalesce(s.types, '[]'::jsonb),
           s.updated_at
    from public.friendships f
    join public.profiles p on p.id = f.friend_id
    left join public.shift_shares s on s.user_id = f.friend_id
    where f.user_id = auth.uid()
    order by p.display_name, p.id;
$$;

-- ---------------------------------------------------------------- 테이블 권한
--
-- 프로젝트 생성 시 "Automatically expose new tables" 를 껐다면 이 GRANT 가 필요하다.
-- 로그인한 사용자에게만 준다. 익명(anon) 역할에는 아무 권한도 주지 않는다.
-- 실제로 어떤 행을 볼 수 있는지는 위의 RLS 정책이 결정한다.

grant usage on schema public to authenticated;

grant select, insert, update on public.profiles        to authenticated;
grant select, delete          on public.friendships    to authenticated;
grant select, insert, update, delete on public.shift_shares    to authenticated;
grant select, insert, update, delete on public.shift_overrides to authenticated;

-- 함수 실행 권한
grant execute on function public.add_friend_by_code(text)  to authenticated;
grant execute on function public.remove_friend(uuid)       to authenticated;
grant execute on function public.list_friend_schedules()   to authenticated;
