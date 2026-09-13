-- ============================================================================
--  날짜별 근무 변경(shift_overrides)을 폰과 웹이 양방향으로 맞추는 마이그레이션
-- ============================================================================
--
--  바뀌는 점
--    이전: 폰이 로그인할 때마다 서버의 변경 기록을 통째로 지우고 다시 썼다.
--          그래서 웹에서 고친 것은 다음 동기화 때 사라졌다.
--    이후: 행마다 updated_at 을 두고 "나중에 저장한 쪽이 이긴다".
--          삭제는 진짜 지우지 않고 deleted 표시만 남긴다 (tombstone).
--          그래야 삭제 이벤트가 RLS 를 못 타는 Realtime 의 특성 때문에
--          남의 삭제 기록이 새어 나가지 않는다.
--
--  Supabase 대시보드 → SQL Editor 에 통째로 붙여넣고 Run.
--  여러 번 실행해도 안전하다.
-- ============================================================================

-- ---------------------------------------------------------------- 컬럼 추가

alter table public.shift_overrides
    add column if not exists updated_at timestamptz not null default now();

alter table public.shift_overrides
    add column if not exists deleted boolean not null default false;

-- ---------------------------------------------------------------- 합치기 함수
--
-- 클라이언트가 가진 변경 기록을 한꺼번에 보내면, 행마다 updated_at 을 비교해
-- 새 것만 반영하고, 그 사용자의 서버 기록 전체를 돌려준다.
-- 한 번의 왕복으로 보내기와 받기를 끝낸다.
--
-- rows 형식: [{"epoch_day": 20710, "label": "야", "deleted": false, "updated_at": "2026-09-13T01:02:03Z"}, ...]

create or replace function public.sync_overrides(rows jsonb)
returns setof public.shift_overrides
language plpgsql
security invoker
set search_path = public
as $$
declare
    me uuid := auth.uid();
begin
    if me is null then
        raise exception 'not_authenticated';
    end if;

    insert into public.shift_overrides (user_id, epoch_day, label, deleted, updated_at)
    select me,
           (r->>'epoch_day')::bigint,
           coalesce(r->>'label', ''),
           coalesce((r->>'deleted')::boolean, false),
           coalesce((r->>'updated_at')::timestamptz, now())
    from jsonb_array_elements(coalesce(rows, '[]'::jsonb)) as r
    on conflict (user_id, epoch_day) do update
        set label      = excluded.label,
            deleted    = excluded.deleted,
            updated_at = excluded.updated_at
        where excluded.updated_at > public.shift_overrides.updated_at;

    return query
        select * from public.shift_overrides
        where user_id = me
        order by epoch_day;
end;
$$;

revoke execute on function public.sync_overrides(jsonb) from public;
grant execute on function public.sync_overrides(jsonb) to authenticated;

-- ---------------------------------------------------------------- 실시간
--
-- 이 두 테이블의 변경을 웹소켓으로 흘려보낸다. 구독한 쪽은 RLS 가 허용하는 행만 받는다.
-- (같은 테이블을 두 번 넣으면 오류가 나므로 있는지 보고 넣는다)

do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and tablename = 'shift_overrides'
    ) then
        alter publication supabase_realtime add table public.shift_overrides;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and tablename = 'shift_shares'
    ) then
        alter publication supabase_realtime add table public.shift_shares;
    end if;
end $$;

-- UPDATE 이벤트에 바뀌기 전 값까지 실으려면 필요하다.
alter table public.shift_overrides replica identity full;
alter table public.shift_shares    replica identity full;
