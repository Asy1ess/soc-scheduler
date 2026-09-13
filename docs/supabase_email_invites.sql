-- ============================================================================
--  이메일로 친구 요청 보내기
-- ============================================================================
--
--  초대 코드 대신 이메일을 넣어도 요청이 간다. 수락은 여전히 받는 쪽이 한다.
--
--  가입 여부를 드러내지 않는다:
--    - 그 이메일 사용자가 이미 있으면 바로 friend_requests 에 넣는다.
--    - 아직 없으면 email_invites 에 예약해 두고, 그 이메일로 처음 로그인하는
--      순간 friend_requests 로 옮긴다.
--    - 어느 쪽이든 보낸 사람에게는 같은 결과('requested')를 돌려준다.
--      그래야 "이 이메일이 이 앱을 쓰는지" 를 캐내는 데 쓸 수 없다.
--
--  Supabase 대시보드 → SQL Editor 에 통째로 붙여넣고 Run. 여러 번 실행해도 안전하다.
-- ============================================================================

-- ---------------------------------------------------------------- 예약 테이블

create table if not exists public.email_invites (
    from_user  uuid not null references auth.users on delete cascade,
    email      text not null,
    created_at timestamptz not null default now(),
    primary key (from_user, email)
);

-- 아무도 직접 읽거나 쓰지 못한다. 아래 함수들만 다룬다.
alter table public.email_invites enable row level security;
revoke all on public.email_invites from anon, authenticated;

-- ---------------------------------------------------------------- 이메일로 요청

create or replace function public.request_friend_by_email(target_email text)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    me       uuid := auth.uid();
    my_email text;
    target   uuid;
    normalized text := lower(trim(target_email));
begin
    if me is null then
        raise exception 'not_authenticated';
    end if;

    if normalized = '' or position('@' in normalized) = 0 then
        raise exception 'invalid_email';
    end if;

    select lower(email) into my_email from auth.users where id = me;
    if my_email = normalized then
        raise exception 'self_email';
    end if;

    select id into target from auth.users where lower(email) = normalized limit 1;

    -- 아직 가입하지 않은 이메일: 예약만 해 두고 똑같이 'requested' 를 돌려준다.
    if target is null then
        insert into public.email_invites (from_user, email)
        values (me, normalized) on conflict do nothing;
        return 'requested';
    end if;

    if exists (
        select 1 from public.friendships
        where user_id = me and friend_id = target
    ) then
        raise exception 'already_friend';
    end if;

    -- 상대가 먼저 나에게 요청해 둔 상태면 양쪽 동의가 확인된 것이다.
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

revoke execute on function public.request_friend_by_email(text) from public;
grant execute on function public.request_friend_by_email(text) to authenticated;

-- ---------------------------------------------------------------- 가입 시 예약된 요청 옮기기
--
-- 프로필을 만드는 기존 트리거 뒤에 돈다. 자기 이메일로 예약된 초대가 있으면
-- 정식 요청으로 바꾼다.

create or replace function public.claim_email_invites()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.friend_requests (from_user, to_user)
    select i.from_user, new.id
    from public.email_invites i
    where i.email = lower(new.email)
      and i.from_user <> new.id
    on conflict do nothing;

    delete from public.email_invites where email = lower(new.email);
    return new;
end;
$$;

drop trigger if exists on_auth_user_created_claim_invites on auth.users;
create trigger on_auth_user_created_claim_invites
    after insert on auth.users
    for each row execute function public.claim_email_invites();

-- ---------------------------------------------------------------- 요청 목록에 이메일 보이기
--
-- 받은 요청이 누구인지 이름만으로는 헷갈릴 수 있어 이메일을 함께 준다.
-- 요청을 보낸 쪽은 스스로 연락한 것이니 받는 쪽에 이메일이 보여도 된다.
-- 내가 보낸 요청은 상대 이메일을 돌려주지 않는다 (예약 초대와 구분되지 않게).

drop function if exists public.list_friend_requests();

create function public.list_friend_requests()
returns table (
    other_id     uuid,
    display_name text,
    email        text,
    direction    text,
    created_at   timestamptz
)
language sql
stable
security definer
set search_path = public
as $$
    select r.from_user, coalesce(p.display_name, ''), u.email, 'incoming', r.created_at
    from public.friend_requests r
    join public.profiles p on p.id = r.from_user
    join auth.users u on u.id = r.from_user
    where r.to_user = auth.uid()
    union all
    select r.to_user, coalesce(p.display_name, ''), null, 'outgoing', r.created_at
    from public.friend_requests r
    join public.profiles p on p.id = r.to_user
    where r.from_user = auth.uid()
    union all
    -- 아직 가입하지 않은 이메일로 보내 둔 요청도 "대기 중" 으로 보여 준다.
    select null, i.email, null, 'outgoing', i.created_at
    from public.email_invites i
    where i.from_user = auth.uid()
    order by 5 desc;
$$;

revoke execute on function public.list_friend_requests() from public;
grant execute on function public.list_friend_requests() to authenticated;

-- 예약 초대 취소용. dismiss_friend_request 는 uuid 를 받으므로 따로 둔다.
create or replace function public.cancel_email_invite(target_email text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'not_authenticated';
    end if;
    delete from public.email_invites
    where from_user = auth.uid() and email = lower(trim(target_email));
end;
$$;

revoke execute on function public.cancel_email_invite(text) from public;
grant execute on function public.cancel_email_invite(text) to authenticated;
