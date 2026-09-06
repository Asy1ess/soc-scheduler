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
| 근무표 | 교대 패턴 기반 월간 근무 캘린더 자동 생성, 날짜별 근무/일정/인계 요약, 연차·대타 수동 변경 |
| 일정 | 개인 일정·할 일 등록, 지난/오늘/예정/완료 분류, 알림(정시~1일 전) |
| 인계 | 인수인계 및 업무일지 기록, 분류(침해시도·오탐·장애 등)·중요도, 전문 검색 |
| 점검 | 매일/평일/매주/매월 반복 점검 항목 자동 생성, 체크 및 특이사항 메모, 진행률 |
| 설정 | 교대 패턴 편집, 점검 항목 관리, 알림 권한, 근무 유형 확인 |

### 교대 패턴

`기준일`부터 사이클이 반복되고, `내 조` 번호만큼 오프셋을 적용해 날짜별 근무를 계산합니다.

기본 제공 프리셋:
- 4조 3교대 — 주 주 · 오 오 · 야 야 · 비 휴 (8일 주기)
- 3조 2교대 — 주 주 · 야 야 · 비 휴 (6일 주기)
- 4조 2교대 — 주 주 · 야 야 · 비 비 · 휴 휴 (8일 주기)
- 2조 2교대 — 주 야 · 비 휴 (4일 주기)
- 주간 전담 — 평일 주간, 주말 휴무 (7일 주기)

프리셋을 적용한 뒤 **설정 › 교대 패턴**에서 사이클의 각 날짜를 개별 수정할 수 있으므로, 사내 근무표가 프리셋과 달라도 그대로 맞출 수 있습니다.

## 홈 화면 위젯

위젯 4종을 제공합니다. 홈 화면 길게 누르기 → 위젯 → **관제 스케줄러**에서 추가하세요.

| 위젯 | 기본 크기 | 내용 |
| --- | --- | --- |
| 오늘 근무 | 2×1 | 오늘 날짜, 근무 유형, 근무 시간대. 근무 색상 막대 표시 |
| 이번 주 근무 | 4×2 | 일~토 7일간의 근무를 색상 칸으로 표시, 오늘은 진하게 강조 |
| 월간 근무표 | 4×4 | 이번 달 전체 달력. 주간/야간별 근무 일수 요약 |
| 오늘 할 일 | 4×2 | 오늘 근무 + 남은 일정 최대 4건 + 점검 진행률 |

- 모든 위젯은 탭하면 앱이 열립니다.
- 크기는 자유롭게 조절할 수 있습니다(resizeMode 지원).
- 갱신 시점: 앱에서 근무·일정·점검을 수정할 때 즉시, 자정 직후 날짜가 바뀔 때, 그리고 시스템 주기 갱신(30분).
- 다크 모드에 맞춰 배경과 글자색이 자동으로 바뀝니다.

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

### Gradle Wrapper 생성 (최초 1회)

이 저장소에는 바이너리 파일인 `gradle-wrapper.jar`가 포함되어 있지 않습니다. 둘 중 하나로 생성하세요.

- **Android Studio에서 프로젝트 폴더를 열기** → Gradle Sync 시 자동 생성 (권장)
- 또는 Gradle CLI가 설치돼 있다면:

```bash
gradle wrapper --gradle-version 9.6
```

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

## 알려진 제약

- 팀 공유 기능은 없습니다(개인용 로컬 저장 전용).
- 알림은 `AlarmManager` 기반이며, Android 12 이상에서 정확한 알람을 쓰려면 설정 › 알림에서 권한을 허용해야 합니다. 허용하지 않으면 근사 시각에 울립니다.
- 데이터 내보내기(CSV/이미지 공유)는 아직 없습니다.

## 스크린샷

<!-- 실제 기기에서 캡처한 이미지를 docs/ 폴더에 넣고 아래 경로를 채우세요. -->
| 근무표 | 일정 | 점검 | 위젯 |
| --- | --- | --- | --- |
| _준비 중_ | _준비 중_ | _준비 중_ | _준비 중_ |

## 라이선스

개인 사용 목적으로 작성된 프로젝트입니다.
