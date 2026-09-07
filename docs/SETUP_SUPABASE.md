# 친구 근무표 공유 설정 가이드

Supabase 계정 로그인과 친구 공유 기능을 켜는 전체 절차입니다.
순서대로 따라가면 되고, 각 단계 끝에 **확인 방법**을 적어 뒀습니다.

## 진행 전 확인

| 항목 | 값 |
| --- | --- |
| Supabase 프로젝트 | `soc-scheduler` |
| 프로젝트 ID | `vdpmaquruygynrazjejd` |
| 리전 | Northeast Asia (Seoul) |
| **OAuth 콜백 주소** | `https://vdpmaquruygynrazjejd.supabase.co/auth/v1/callback` |
| **앱 복귀 딥링크** | `socscheduler://login-callback` |

두 주소는 아래에서 반복해서 씁니다. 헷갈리기 쉬우니 역할을 구분해 두세요.

- **콜백 주소**: 구글/카카오가 로그인 결과를 Supabase로 보내는 곳
- **딥링크**: Supabase가 로그인 완료 후 우리 앱으로 돌려보내는 곳

## 완료된 것

- [x] Supabase 프로젝트 생성
- [x] 스키마 실행 (테이블 4 / 정책 9 / 함수 4, RLS 전부 활성)

> **⚠️ 스키마를 한 번 더 실행해야 합니다.**
> 근무 시작일(수습 제외) 기능이 추가되면서 `shift_shares` 에 컬럼이 하나 늘었습니다.
> SQL Editor 에서 `docs/supabase_schema.sql` 을 **다시 전체 실행**해 주세요.
> 여러 번 실행해도 안전하도록 작성되어 있습니다 (기존 데이터는 유지됩니다).

## 남은 것

- [ ] ① 딥링크 등록
- [ ] ② Google 로그인 연결
- [ ] ③ 키를 `local.properties` 에 입력
- [ ] ④ 빌드 후 실기기 로그인 테스트
- [ ] ⑤ 카카오 로그인 추가 (④가 성공한 뒤에)

---

## ① 딥링크 등록 (2분)

로그인이 끝나고 앱으로 돌아오려면 이 주소가 허용 목록에 있어야 합니다.

1. Supabase 대시보드 → 좌측 **Authentication**
2. **URL Configuration** 메뉴
3. **Redirect URLs** 항목의 **Add URL** 클릭
4. 아래 값을 그대로 입력하고 저장

```
socscheduler://login-callback
```

**확인**: Redirect URLs 목록에 위 값이 보이면 완료.

> 이 단계를 빠뜨리면 로그인은 되는데 앱으로 안 돌아오고 브라우저에 멈춰 있게 됩니다.

---

## ② Google 로그인 연결 (15분)

Supabase와 Google Cloud 양쪽을 오가야 합니다. **콜백 주소를 먼저 복사해 두세요.**

### 2-1. Supabase에서 콜백 주소 확인

1. **Authentication** → **Sign In / Providers**
2. 목록에서 **Google** 클릭
3. 토글을 **ON**
4. 화면에 표시되는 **Callback URL (for OAuth)** 를 복사

> 값은 `https://vdpmaquruygynrazjejd.supabase.co/auth/v1/callback` 입니다.
> 이 화면은 닫지 말고 그대로 두세요. 곧 돌아옵니다.

### 2-2. Google Cloud Console에서 OAuth 클라이언트 만들기

[console.cloud.google.com](https://console.cloud.google.com) 접속.

**(a) 프로젝트 생성**

상단 프로젝트 선택기 → **새 프로젝트** → 이름 `soc-scheduler` → 만들기
생성 후 그 프로젝트가 선택돼 있는지 확인하세요.

**(b) OAuth 동의 화면 설정**

좌측 메뉴 **API 및 서비스** → **OAuth 동의 화면**
(최근 콘솔에서는 **Google Auth Platform** → **브랜딩** 으로 이름이 바뀌었을 수 있습니다. 둘 다 같은 것입니다.)

- User Type: **외부(External)**
- 앱 이름: `관제 스케줄러`
- 사용자 지원 이메일 / 개발자 연락처: 본인 이메일
- 나머지는 비워도 됩니다. 저장하며 끝까지 진행

> 게시 상태가 **테스트** 로 남아 있어도 됩니다. 다만 테스트 모드에서는
> **테스트 사용자로 등록한 계정만 로그인 가능**합니다.
> 본인과 친구의 Google 계정을 **테스트 사용자**에 추가해 두세요.
> (외부 공개하려면 나중에 **앱 게시** 를 하면 됩니다.)

**(c) OAuth 클라이언트 ID 발급**

좌측 **사용자 인증 정보(Credentials)** → **사용자 인증 정보 만들기** → **OAuth 클라이언트 ID**

- 애플리케이션 유형: **웹 애플리케이션** ← 안드로이드 아님. 반드시 웹으로 선택
- 이름: `supabase`
- **승인된 리디렉션 URI** → **URI 추가** → 2-1에서 복사한 콜백 주소 붙여넣기

```
https://vdpmaquruygynrazjejd.supabase.co/auth/v1/callback
```

- **만들기** 클릭 → **클라이언트 ID** 와 **클라이언트 보안 비밀번호** 가 표시됨

> 앱 유형을 "Android"로 만들면 안 됩니다. 로그인 흐름이 Supabase 서버를 거치므로
> 웹 애플리케이션 클라이언트가 맞습니다.

### 2-3. Supabase에 입력

2-1의 Google provider 화면으로 돌아와서

- **Client ID**: 위에서 발급받은 클라이언트 ID
- **Client Secret**: 클라이언트 보안 비밀번호

입력 후 **Save**.

**확인**: Providers 목록에서 Google 옆에 `Enabled` 표시가 뜨면 완료.

---

## ③ 키를 앱에 넣기 (2분)

1. Supabase 좌측 하단 **Project Settings** (톱니바퀴) → **API**
2. 두 값을 복사

| 항목 | 설명 |
| --- | --- |
| **Project URL** | `https://vdpmaquruygynrazjejd.supabase.co` |
| **anon public** | `eyJ...` 로 시작하는 긴 문자열 |

3. `C:\Users\user1\Desktop\스케쥴러\local.properties` 를 열어 빈칸을 채웁니다.

```properties
supabase.url=https://vdpmaquruygynrazjejd.supabase.co
supabase.anonKey=eyJhbGciOiJIUzI1NiIs...여기에_긴_문자열_전체
```

> **`service_role` 키는 절대 넣지 마세요.** RLS를 전부 무시하는 관리자 키라서
> 앱에 들어가면 모든 사용자의 데이터가 노출됩니다. 반드시 `anon public` 쪽입니다.
>
> `local.properties` 는 `.gitignore` 에 있어 GitHub에 올라가지 않습니다.

---

## ④ 빌드 후 테스트

폰을 USB로 연결한 뒤 (USB 디버깅 ON) 아래를 실행합니다.

```bash
./gradlew installDebug
```

앱에서 **설정 → 친구 근무표 → Google로 로그인**

**정상 흐름**
1. 크롬 커스텀 탭이 열리며 Google 계정 선택 화면
2. 계정 선택 후 자동으로 앱에 복귀
3. **내 초대 코드**(예: `A3KM-7PQR`)가 표시됨
4. 내 근무표가 자동으로 서버에 올라감

**확인**: Supabase 대시보드 → **Table Editor** → `profiles` 테이블에 내 계정 1행,
`shift_shares` 에 근무표 1행이 들어와 있으면 성공입니다.

---

## ⑤ 카카오 로그인 추가

④가 성공한 뒤에 진행하세요.

### 5-1. 카카오 앱 만들기

[developers.kakao.com](https://developers.kakao.com) 로그인 →
**내 애플리케이션** → **애플리케이션 추가하기**

- 앱 이름: `관제 스케줄러`
- 사업자명: 개인이면 본인 이름

### 5-2. 플랫폼 등록

**앱 설정 → 플랫폼** → **Web 플랫폼 등록**

사이트 도메인:

```
https://vdpmaquruygynrazjejd.supabase.co
```

### 5-3. 카카오 로그인 활성화

**제품 설정 → 카카오 로그인**

- 활성화 설정: **ON**
- **Redirect URI 등록** → 콜백 주소 입력

```
https://vdpmaquruygynrazjejd.supabase.co/auth/v1/callback
```

### 5-4. Client Secret 발급

**제품 설정 → 카카오 로그인 → 보안**

- **Client Secret** 코드 생성
- 활성화 상태를 **사용함** 으로 변경 (생성만 하고 활성화를 안 하면 실패합니다)

### 5-5. 키 확인

**앱 설정 → 앱 키** 에서 **REST API 키** 복사.
이것이 Supabase에서 말하는 Client ID 입니다. (JavaScript 키 아님)

### 5-6. Supabase에 입력

Authentication → Sign In / Providers → **Kakao** → ON

- **Client ID**: REST API 키
- **Client Secret**: 5-4에서 만든 값

Save 후 앱에서 **카카오로 로그인** 테스트.

### 카카오 관련 알아둘 점

카카오는 **이메일 제공에 제약**이 있습니다. 개인 개발자 앱에서는 사용자 이메일을
받으려면 비즈니스 앱 전환(사업자등록번호 필요)이 요구될 수 있습니다.
이 앱은 이메일을 쓰지 않고 계정 식별자만 쓰므로 문제없을 것으로 보이지만,
로그인 단계에서 이메일 관련 오류가 나면 알려 주세요. 대응 방법을 찾아 드리겠습니다.

동의항목은 **닉네임(프로필 정보)** 만 선택 동의로 켜 두면 충분합니다.
표시 이름이 비어 있으면 앱에서 직접 설정할 수 있습니다.

---

## 문제가 생기면

| 증상 | 원인과 해결 |
| --- | --- |
| `redirect_uri_mismatch` (구글) | Google Console의 **승인된 리디렉션 URI** 오타. 콜백 주소를 다시 복사해 붙여넣기. 끝에 `/` 가 붙거나 빠지지 않았는지 확인 |
| 로그인 후 브라우저에 멈춤, 앱으로 안 돌아옴 | ① 단계의 `socscheduler://login-callback` 누락 |
| `앱이 확인되지 않았습니다` 경고 (구글) | OAuth 동의 화면이 테스트 모드. **테스트 사용자**에 해당 계정 추가, 또는 앱 게시 |
| `permission denied for table ...` | 스키마의 GRANT 구문 누락. `docs/supabase_schema.sql` 을 다시 실행 (여러 번 실행해도 안전합니다) |
| `KOE006` 등 카카오 오류 코드 | Redirect URI 불일치. 5-3 확인 |
| 카카오 로그인 후 실패 | 5-4의 Client Secret **활성화** 여부 확인 |
| 앱에 친구 메뉴가 "비활성화됨" 으로 뜸 | `local.properties` 키가 비었거나 오타. 입력 후 **다시 빌드** 필요 |
| 친구 추가 시 `그런 초대 코드는 없습니다` | 코드 오타. 대소문자는 자동 처리되고 `-` 도 무시되므로 글자만 정확하면 됩니다 |

## 되돌리고 싶으면

`local.properties` 의 두 줄을 비우고 다시 빌드하면 친구 기능이 사라지고
앱이 완전한 로컬 전용으로 돌아갑니다. 기기에 저장된 근무표·일정·점검 기록은
그대로 유지됩니다.

서버 데이터까지 지우려면 Supabase 대시보드에서 프로젝트를 삭제하면 됩니다.
