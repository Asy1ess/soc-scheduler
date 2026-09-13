# 관제 스케줄러 — 웹

폰 앱이 Supabase 에 올려 둔 근무표를 브라우저에서 봅니다. 파일 하나(`index.html`)로
된 정적 페이지라 서버가 없고, GitHub Pages 에 그대로 올라갑니다.

## 할 수 있는 것

- 폰 앱과 같은 Google 계정으로 로그인
- 내 근무표 · 친구 근무표 월 달력 (근무 계산 규칙은 앱과 동일)
- 내 초대 코드 확인 · 복사
- 친구 요청 보내기 · 받은 요청 수락 / 거절 · 친구 삭제

## 할 수 없는 것

- **근무표 수정.** 앱이 로그인할 때마다 서버 근무표를 통째로 덮어쓰므로, 웹에서
  고쳐도 다음 동기화 때 사라집니다. 수정은 폰 앱에서 하세요.
- 일정 · 점검 · 알람. 애초에 서버에 올리지 않는 것들입니다.

## 배포

`.github/workflows/pages.yml` 이 `web/` 을 GitHub Pages 에 올립니다. 저장소에
Secrets 두 개가 있어야 합니다.

| 이름 | 값 |
| --- | --- |
| `SUPABASE_URL` | `https://<project>.supabase.co` |
| `SUPABASE_ANON_KEY` | anon (public) 키 |

그리고 Supabase 대시보드 → Authentication → URL Configuration 에서

- **Site URL** 을 Pages 주소로 (`https://<user>.github.io/soc-scheduler/`)
- **Redirect URLs** 에 같은 주소를 추가

해야 Google 로그인이 끝난 뒤 이 페이지로 돌아옵니다.

## 로컬에서 보기

```bash
cp web/config.example.js web/config.js   # 값 채우기
python -m http.server 8765 --directory web
```

`http://localhost:8765` 로 로그인까지 하려면 Supabase Redirect URLs 에
`http://localhost:8765/` 도 넣어야 합니다.
