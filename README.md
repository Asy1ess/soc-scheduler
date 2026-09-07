# 관제 스케줄러 (SOC Scheduler)

보안관제(SOC) 근무자를 위한 안드로이드 스케줄 관리 앱입니다.
교대 근무표를 자동으로 생성하고, 일정·인수인계·정기 점검을 한곳에서 관리합니다.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Storage](https://img.shields.io/badge/storage-Room%20(local%20only)-FF6F00)

> 모든 데이터는 기기 내부 DB에만 저장되며, 서버로 전송되지 않습니다.
> 계정도 네트워크 권한도 필요 없습니다.

## 기능

| 탭 | 내용 |
| --- | --- |
| 근무표 | 교대 패턴 기반 월간 근무 캘린더 자동 생성, 날짜별 근무/일정/인계 요약, 연차·대타 수동 변경(원래 근무 함께 표시) |
| 일정 | 개인 일정·할 일 등록, 지난/오늘/예정/완료 분류, 알림(정시~1일 전) |
| 인계 | 인수인계 및 업무일지 기록, 분류(침해시도·오탐·장애 등)·중요도, 전문 검색 |
| 점검 | 매일/평일/매주/매월 반복 점검 항목 자동 생성, 체크 및 특이사항 메모, 진행률 |
| 설정 | 초기 설정 다시 하기, 교대 패턴 편집, 점검 항목 관리, 위젯 추가, 아침 근무 알림, 알림 권한 |

### 아침 근무 알림

**설정 › 아침 근무 알림**을 켜면 매일 지정한 시각(기본 07:30)에 그날 근무·점검·일정을 알림으로 보여 줍니다.
알림은 잠금화면에도 표시되므로, 잠금화면 위젯을 지원하지 않는 기기에서도 아침에 근무를 확인할 수 있습니다.
휴무·비번인 날에는 알리지 않도록 끌 수 있습니다.

### 첫 실행 초기 설정

앱을 처음 켜면 4단계 설정 화면이 먼저 나옵니다. 여기서 정한 값이 앱의 기본 설정이 되며,
**설정 › 근무 설정 다시 하기**에서 언제든 다시 바꿀 수 있습니다.

1. **근무 형태** — 프리셋 5종 중 선택하거나 직접 만들기
2. **근무 주기** — 주기 길이와 각 일차의 근무를 편집하고, "오늘이 몇 일차인지" 지정 (기준일이 자동 계산됨)
3. **근무 시간** — 주기에 쓰인 근무의 시작·종료 시각 조정
4. **점검 루틴** — 자동 생성할 정기 점검 항목 선택

### 교대 패턴

`기준일`부터 사이클이 반복되고, `내 조` 번호만큼 오프셋을 적용해 날짜별 근무를 계산합니다.

기본 제공 프리셋:
- 4조 3교대 — 주 주 · 오 오 · 야 야 · 비 휴 (8일 주기)
- 3조 2교대 — 주 주 · 야 야 · 비 휴 (6일 주기)
- 4조 2교대 — 주 주 · 야 야 · 비 비 · 휴 휴 (8일 주기)
- 2조 2교대 — 주 야 · 비 휴 (4일 주기)
- 주간 전담 — 평일 주간, 주말 휴무 (7일 주기)

프리셋을 적용한 뒤 **설정 › 교대 패턴**에서 사이클의 각 날짜를 개별 수정할 수 있으므로, 사내 근무표가 프리셋과 달라도 그대로 맞출 수 있습니다.

**근무 시작일** — 수습 기간처럼 교대 근무를 하지 않은 구간이 있으면 실제 근무 시작일을 지정할 수 있습니다.
그 이전 날짜는 근무표에서 비워집니다. 초기 설정 2단계 또는 설정 › 교대 패턴에서 지정합니다.

**근무 변경** — 특정 날짜를 눌러 연차·대타 등으로 바꿀 수 있습니다.
선택지에는 **현재 패턴에서 실제로 쓰는 근무**와 비번·휴무·연차 같은 비근무 유형만 나옵니다
(예: 4조 2교대로 바꾸면 3교대 전용인 "오후"는 빠집니다). 필요하면 "다른 근무 유형도 보기"로 전체를 볼 수 있습니다.
변경한 날은 달력에 표시되고, 상세 카드에서 **원래 근무가 무엇이었는지** 확인할 수 있습니다.

## 홈 화면 위젯

위젯 4종을 제공합니다. 홈 화면 길게 누르기 → 위젯 → **관제 스케줄러**에서 추가하세요.

| 위젯 | 기본 크기 | 내용 |
| --- | --- | --- |
| 오늘 근무 | 2×1 | 오늘 날짜, 근무 유형, 근무 시간대. 근무 색상 막대 표시 |
| 이번 주 근무 | 4×2 | 일~토 7일간의 근무를 색상 칸으로 표시, 오늘은 진하게 강조 |
| 월간 근무표 | 4×4 | 이번 달 전체 달력. 주간/야간별 근무 일수 요약 |
| 오늘 할 일 | 4×2 | 오늘 근무 + 점검 항목(앱과 동일한 목록) + 일정. **항목을 탭하면 앱을 열지 않고 바로 체크** |

- 위젯은 **설정 › 위젯 추가**에서 홈 화면에 바로 올릴 수 있습니다.
- 모든 위젯은 탭하면 앱이 열립니다 (점검 항목만 예외 — 그 자리에서 체크).
- 크기는 자유롭게 조절할 수 있습니다(resizeMode 지원).
- 갱신 시점: 앱에서 근무·일정·점검을 수정할 때 즉시, 자정 직후 날짜가 바뀔 때, 그리고 시스템 주기 갱신(30분).
- 다크 모드에 맞춰 배경과 글자색이 자동으로 바뀝니다.

## 친구 근무표 공유 (선택 기능)

계정 로그인 후 초대 코드로 친구를 추가하면 서로의 근무표를 볼 수 있습니다.
**이 기능은 기본적으로 꺼져 있습니다.** 아래 설정을 하지 않으면 앱은 예전처럼 완전한 로컬 전용으로 동작합니다.

### 공유되는 것 / 안 되는 것

| 서버로 올라감 | 기기에만 남음 |
| --- | --- |
| 근무 주기(패턴), 근무 유형과 시각, 날짜별 근무 변경(연차·대타) | 일정·할 일, 인수인계·업무일지, 정기 점검 기록과 메모 |

업무 내용은 어떤 경우에도 전송되지 않습니다. 코드상으로도 `remote/` 패키지가 근무표 외의 테이블을 건드리지 않습니다.

> 근무표는 "언제 관제 인원이 적은지"를 드러내는 정보입니다. 사내 정책상 외부 클라우드 반출이 가능한지 먼저 확인하세요.

### 설정 방법

전체 절차와 화면별 안내는 **[docs/SETUP_SUPABASE.md](docs/SETUP_SUPABASE.md)** 에 단계별로 정리되어 있습니다.
아래는 요약입니다.

**1. Supabase 프로젝트 생성**

[supabase.com](https://supabase.com) 에서 프로젝트를 만듭니다.

**2. 스키마 적용**

대시보드 › SQL Editor 에 [`docs/supabase_schema.sql`](docs/supabase_schema.sql) 전체를 붙여넣고 실행합니다.
테이블·RLS 정책·초대 코드 함수가 한 번에 만들어집니다.

**3. 소셜 로그인 활성화**

대시보드 › Authentication › Providers 에서:
- **Google** — Google Cloud Console에서 OAuth 클라이언트를 만들고 Client ID/Secret 입력
- **Kakao** — [Kakao Developers](https://developers.kakao.com) 에서 앱을 만들고 REST API 키와 Client Secret 입력

**4. 리디렉션 URL 등록**

Authentication › URL Configuration › Redirect URLs 에 다음을 추가합니다.

```
socscheduler://login-callback
```

**5. 키 넣기**

프로젝트 루트의 `local.properties` 에 추가합니다. 이 파일은 `.gitignore` 에 있어 저장소에 올라가지 않습니다.

```properties
supabase.url=https://<프로젝트ID>.supabase.co
supabase.anonKey=<anon public key>
```

다시 빌드하면 **설정 › 친구 근무표** 메뉴가 활성화됩니다.

### 보안 설계

- 모든 테이블에 RLS(Row Level Security)가 걸려 있어 **친구가 아니면 아무것도 읽을 수 없습니다.**
- 초대 코드로 남을 검색할 수 없습니다. 코드 대조는 `SECURITY DEFINER` 함수 안에서만 이뤄지므로, 코드를 무작위로 대입해 남의 계정을 찾아내는 것이 불가능합니다.
- 초대 코드는 헷갈리는 문자(0/O, 1/I)를 뺀 32자 알파벳에서 8자리를 뽑습니다.

## 기술 스택

| 항목 | 버전 |
| --- | --- |
| Android Gradle Plugin | 9.4.0 |
| Gradle | 9.6 |
| Kotlin | AGP 내장 (built-in Kotlin) |
| Compose Compiler 플러그인 | 2.3.21 |
| KSP | 2.3.11 |
| Compose BOM | 2026.08.00 |
| Room | 2.8.4 |
| supabase-kt | 3.8.0 (친구 기능용, 선택) |
| compileSdk / targetSdk | 37 |
| minSdk | 26 (Android 8.0) |
| JDK | 17 (Gradle 실행은 Studio 번들 JDK 25) |

AGP 9부터 Kotlin 지원이 AGP에 내장되어 `org.jetbrains.kotlin.android` 플러그인을 적용하지 않습니다.
아이콘은 `material-icons-core` 세트만 사용합니다.

## 빌드 방법

### 준비물 (에디터와 무관하게 필수)

1. **Android Studio** (번들 JDK 25 포함) — Gradle 실행에 JDK 17 이상 필요
2. **Android SDK Platform 37**, **Build-Tools 36.0.0**, Platform-Tools

가장 간단한 확보 방법은 [Android Studio](https://developer.android.com/studio)를 한 번 설치하는 것입니다.
설치 후 실제 코딩은 VS Code에서 해도 되고, Android Studio를 다시 열 필요는 없습니다.

### VS Code로 개발하기

권장 확장:
- `fwcd.kotlin` (Kotlin 언어 지원)
- `vscjava.vscode-gradle` (Gradle 태스크 실행)

`local.properties` 파일을 프로젝트 루트에 만들고 SDK 경로를 지정합니다 (Android Studio로 한 번 열었다면 자동 생성됨):

```
sdk.dir=C\:\\Users\\user1\\AppData\\Local\\Android\\Sdk
```

### 프로젝트 경로 주의

AGP는 프로젝트 경로에 비ASCII 문자(한글 등)가 있으면 빌드를 거부합니다.
이 저장소에는 우회 옵션이 `gradle.properties`에 들어 있습니다.

```properties
android.overridePathCheck=true
```

경로에 한글이 없는 위치(예: `C:\dev\soc-scheduler`)에 두면 이 옵션은 지워도 됩니다.

### 빌드 & 설치

```bash
./gradlew assembleDebug
```

APK 위치: `app/build/outputs/apk/debug/app-debug.apk`

USB 디버깅을 켠 실제 기기에 바로 설치하려면:

```bash
./gradlew installDebug
```

## 프로젝트 구조

```
app/src/main/java/com/soc/scheduler/
├─ Graph.kt                 DB/Repository 싱글턴
├─ SchedulerApp.kt          Application, 알림 채널
├─ MainActivity.kt          Compose 진입점
├─ data/
│  ├─ Entities.kt           Room 엔티티 및 상수
│  ├─ Daos.kt               DAO
│  ├─ AppDatabase.kt        DB 정의 + 최초 실행 시 기본 데이터 시드
│  └─ Repository.kt         패턴 적용, 점검 인스턴스 생성
├─ domain/ShiftEngine.kt    날짜 → 근무 계산, 패턴 프리셋
├─ notify/Reminders.kt      알람 예약, 알림 표시, 부팅 후 재등록
├─ widget/
│  ├─ WidgetData.kt        위젯 렌더링용 데이터 로딩
│  ├─ WidgetUpdater.kt     위젯 갱신 브로드캐스트, 자정 알람
│  └─ Widgets.kt           위젯 4종 Provider
└─ ui/
   ├─ AppRoot.kt            하단 탭 + 네비게이션
   ├─ common/Common.kt      날짜 유틸, 공용 컴포저블
   ├─ shift/                근무표 캘린더, 교대 패턴 편집
   ├─ task/                 일정·할 일
   ├─ handover/             인수인계·업무일지
   ├─ checklist/            정기 점검, 항목 관리
   └─ settings/             설정
```

## 빌드 검증 상태

`./gradlew assembleDebug` 통과 확인 (Gradle 9.6.0 / AGP 9.4.0 / JDK 25 / SDK 37).
컴파일 경고 0건, `app-debug.apk` 약 12.8MB 생성.

실기기(Galaxy S24+, Android 16) 검증 완료:
- 초기 설정 4단계 전 구간 동작, 결과가 근무표·점검에 정확히 반영
- 교대 주기 계산 검증 (8일 주기가 월 전체에 올바르게 반복)
- 위젯 렌더링 및 앱↔위젯 양방향 동기화 (위젯에서 체크 → 앱에 즉시 반영)

## 알려진 제약

- 친구 공유는 근무표에 한정됩니다. 팀 단위 근무표 편성·교대 요청 같은 기능은 없습니다.
- Supabase 키를 넣으면 `INTERNET` 권한이 사용됩니다. 키가 없으면 네트워크를 전혀 쓰지 않습니다.
- 친구 근무표는 조회 시점에 받아 오며, 오프라인 캐시는 아직 없습니다.
- 알림은 `AlarmManager` 기반이며, Android 12 이상에서 정확한 알람을 쓰려면 설정 › 알림에서 권한을 허용해야 합니다. 허용하지 않으면 근사 시각에 울립니다.
- 데이터 내보내기(CSV/이미지 공유)는 아직 없습니다.
- 삼성 Now Bar 표시는 지원하지 않습니다. Now Bar는 Android 16 Live Updates API 기반이며
  배차·배달처럼 진행 중인 활동을 대상으로 하므로, 정적인 일일 안내 알림은 대상이 아닙니다.
  아침 근무 알림은 일반 알림으로 잠금화면에 표시됩니다.

## 스크린샷

<!-- 실제 기기에서 캡처한 이미지를 docs/ 폴더에 넣고 아래 경로를 채우세요. -->
| 근무표 | 일정 | 점검 | 위젯 |
| --- | --- | --- | --- |
| _준비 중_ | _준비 중_ | _준비 중_ | _준비 중_ |

## 라이선스

개인 사용 목적으로 작성된 프로젝트입니다.
