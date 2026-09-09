-- ============================================================================
--  친구 추가를 "수락제" 로 바꾸는 마이그레이션
-- ============================================================================
--
--  바뀌는 점
--    이전: 내 초대 코드를 아는 사람이 코드를 넣으면 그 즉시 친구가 되어
--          내 근무표가 상대에게 보였다. 내 승인 절차가 없었다.
--    이후: 코드를 넣으면 "요청" 만 생긴다. 내가 수락해야 서로 보인다.
--
--  Supabase 대시보드 → SQL Editor 에 통째로 붙여넣고 Run.
--  여러 번 실행해도 안전하다.
-- ============================================================================

-- ---------------------------------------------------------------- 요청 테이블

create table if not exists public.friend_requests (
    from_user  uuid not null references auth.users on delete cascade,
    to_user    uuid not null references auth.users on delete cascade,
    created_at timestamptz not null default now(),
    primary key (from_user, to_user),
    constraint no_self_request check (from_user <> to_user)
);

alter table public.friend_requests enable row level security;

-- 내가 보냈거나 내가 받은 요청만 보인다.
drop policy if exists requests_select on public.friend_requests;
create policy requests_select on public.friend_requests for select
using (from_user = auth.uid() or to_user = auth.uid());

-- 보낸 쪽은 취소, 받은 쪽은 거절. 새로 만드는 것은 함수로만 한다.
drop policy if exists requests_delete on public.friend_requests;
create policy requests_delete on public.friend_requests for delete
using (from_user = auth.uid() or to_user = auth.uid());

-- ---------------------------------------------------------------- 요청 보내기
--
-- 이제 코드를 안다고 바로 친구가 되지 않는다. 요청만 쌓인다.
-- 다만 상대가 이미 나에게 요청을 보내 둔 상태라면 서로 원하는 것이 확인된
-- 셈이므로 그 자리에서 맺는다.

create or replace function public.request_friend_by_code(code text)
returns text
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

    if exists (
        select 1 from public.friendships
        where user_id = me and friend_id = target
    ) then
        raise exception 'already_friend';
    end if;

    -- 상대가 먼저 보내 둔 요청이 있으면 양쪽 동의가 확인된 것이다.
    if exists (
        select 1 from public.friend_requests
        where from_user = target and to_user = me
    ) then
        delete from public.friend_requests
        where (from_user = target and to_user = me)
           or (from_user = me and to_user = target);

        insert into public.friendships (user_id, friend_id)
        values (me, target) on conflict do nothing;
        insert into public.friendships (user_id, friend_id)
        values (target, me) on conflict do nothing;

        return 'accepted';
    end if;

    insert into public.friend_requests (from_user, to_user)
    values (me, target) on conflict do nothing;

    return 'requested';
end;
$$;

-- ---------------------------------------------------------------- 요청 수락

create or replace function public.accept_friend_request(requester uuid)
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

    if not exists (
        select 1 from public.friend_requests
        where from_user = requester and to_user = me
    ) then
        raise exception 'no_request';
    end if;

    delete from public.friend_requests
    where (from_user = requester and to_user = me)
       or (from_user = me and to_user = requester);

    insert into public.friendships (user_id, friend_id)
    values (me, requester) on conflict do nothing;
    insert into public.friendships (user_id, friend_id)
    values (requester, me) on conflict do nothing;
end;
$$;

-- ---------------------------------------------------------------- 요청 거절 / 취소
--
-- 받은 요청을 거절할 때도, 내가 보낸 요청을 취소할 때도 이 함수를 쓴다.

create or replace function public.dismiss_friend_request(other uuid)
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

    delete from public.friend_requests
    where (from_user = other and to_user = me)
       or (from_user = me and to_user = other);
end;
$$;

-- ---------------------------------------------------------------- 요청 목록
--
-- 아직 친구가 아니라서 profiles 를 직접 못 읽는다. 그래서 SECURITY DEFINER 로
-- 표시 이름만 꺼내 준다. 상대의 초대 코드는 돌려주지 않는다.

drop function if exists public.list_friend_requests();

create function public.list_friend_requests()
returns table (
    other_id     uuid,
    display_name text,
    direction    text,
    created_at   timestamptz
)
language sql
stable
security definer
set search_path = public
as $$
    select r.from_user, coalesce(p.display_name, ''), 'incoming', r.created_at
    from public.friend_requests r
    join public.profiles p on p.id = r.from_user
    where r.to_user = auth.uid()
    union all
    select r.to_user, coalesce(p.display_name, ''), 'outgoing', r.created_at
    from public.friend_requests r
    join public.profiles p on p.id = r.to_user
    where r.from_user = auth.uid()
    order by 4 desc;
$$;

-- ---------------------------------------------------------------- 옛 함수 제거
--
-- 승인 없이 바로 친구가 되던 경로다. 남겨 두면 우회로가 된다.

drop function if exists public.add_friend_by_code(text);

-- ---------------------------------------------------------------- 권한
--
-- 로그인한 사용자에게만 준다. 함수는 기본적으로 PUBLIC 에 실행 권한이
-- 붙으므로 먼저 회수한 뒤 authenticated 에만 다시 준다.

grant select, delete on public.friend_requests to authenticated;

revoke execute on function public.request_friend_by_code(text) from public;
revoke execute on function public.accept_friend_request(uuid)  from public;
revoke execute on function public.dismiss_friend_request(uuid) from public;
revoke execute on function public.list_friend_requests()       from public;
revoke execute on function public.remove_friend(uuid)          from public;
revoke execute on function public.list_friend_schedules()      from public;

grant execute on function public.request_friend_by_code(text) to authenticated;
grant execute on function public.accept_friend_request(uuid)  to authenticated;
grant execute on function public.dismiss_friend_request(uuid) to authenticated;
grant execute on function public.list_friend_requests()       to authenticated;
grant execute on function public.remove_friend(uuid)          to authenticated;
grant execute on function public.list_friend_schedules()      to authenticated;
