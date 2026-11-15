# 반디 (Pleos Connect 앱)

반딧불처럼 밤길을 밝혀, 지금 출발하면 아직 볼 수 있는 축제와 행사로 데려다주는 차량 앱이다.
차량의 주행 가능 거리, 내비게이션 도착 예정 시각, 행사 운영 시간, 함께 탄 사람을 함께 따져 판단한다.

현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect) 출품작이다. 기획서는 접수양식으로 따로 제출했고, 이 저장소에는 실제 Pleos Connect SDK로 구현한 안드로이드 앱이 있다.

**트래블젠과의 관계:** 반디는 이번 공모전을 위해 새로 만든 차량 앱이다. 판단 엔진, 차량 화면, Vehicle · NaviHelper · TextToSpeech · Gleo 연동은 모두 이번에 개발했다. 운영 중인 여행 정보 웹 서비스 트래블젠(travelgen.kr, 다른 공모전 수상작)은 축제 데이터, 행사 사진, 행사 질문 답변(RAG) 서버의 **데이터 출처**로만 쓴다.
화면은 운영 중인 트래블젠 웹앱을 기준으로 한 [디자인 시스템](docs/pleos-design-system.md)을 따르고, 쓴 그림과 아이콘의 라이선스는 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)에 있다.

## 제출 정보

| 항목 | 내용 |
|---|---|
| 앱 이름 | 반디 (패키지 `kr.bandi.car`) |
| 한 줄 소개 | 지금 출발하면 아직 볼 수 있는 축제·행사를 차량 데이터로 판단해 데려다주는 차량 앱 |
| APK | [`apk/이준혁_Bandi_release.apk`](apk/이준혁_Bandi_release.apk) (release, 서명됨, versionName 0.1.0) |
| 에뮬레이터 | Pleos Connect Emulator 2.1.0 (Android API 34 시스템 이미지, macOS arm64에서 확인) |
| 화면 규격 | 2560x1440, 160dpi |
| 사용 SDK | Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1, Gleo Interface(App Schema `app/src/main/assets/schema.json`) |

### 실행 방법

1. Android Studio에서 Pleos Connect Emulator 2.1.0을 만든다(아래 "개발 환경 준비").
2. 에뮬레이터를 켜고 APK를 설치한다: `adb install apk/이준혁_Bandi_release.apk`
3. 앱 목록에서 "반디"를 연다. 또는 Gleo와 같은 인텐트로 부른다(아래 "Gleo 없이 시험하기", 패키지 `kr.bandi.car`).
4. 기어, 주행 가능 거리, 배터리는 Virtual Console(`adb forward tcp:8088 tcp:8088` 뒤 `http://localhost:8088`)에서 바꾼다. 에뮬레이터를 새로 켜면 주행 가능 거리가 0이므로 먼저 넣는다.

### 시험 환경과 필요한 설정

- 소스로 빌드하려면 JDK 17, Gradle 8 이상(동봉 wrapper)이 필요하다. Pleos SDK는 `https://nexus-playground.pleos.ai/repository/maven-releases/`에서 받는다.
- 별도 키 없이 빌드·실행된다. 아래 값은 있을 때만 쓰며 `local.properties`에 넣는다(저장소에 올리지 않는다).
  - `pleos.clientId`, `pleos.clientSecret`: TextToSpeech 서버 모드. 없으면 온디바이스 모드로 말한다.
  - `travelgen.carKey`: 행사 질문 서버(RAG) 키. 제출 APK에는 넣지 않았다. 없으면 앱 안 자료로 답한다.
- **표본 축제 데이터는 2026년 7월 행사가 많다.** 실제 날짜에는 대부분 "오늘은 열리지 않아요"로 판단된다. 대기 화면 "지금 이 차" 카드의 **"시연 시각(7월 11일 오후 4시 30분)으로 보기"**를 누르면 시연 흐름을 볼 수 있다. 명령으로는 `adb shell am start -n kr.bandi.car/.MainActivity -a kr.bandi.car.DEV --ez demoClock true`
- 축제 데이터 출처: 한국관광공사 TourAPI(공공데이터포털), 트래블젠(travelgen.kr).

## 실제로 연동한 것

| 기능 | Pleos SDK, 인터페이스 | 코드 |
|---|---|---|
| 기어 상태(P단에서만 상세, QR) | Vehicle SDK `Gear.getCurrentGearSelectionState`, `registerGearSelection` | `vehicle/VehicleMonitor.kt` |
| 주행 가능 거리(도착 가능성, 충전 판단) | Vehicle SDK `Odometer.getRangeRemainingDistance`, `registerRangeRemainingListener` | 같은 파일 |
| 배터리 잔량 | Vehicle SDK `EvBattery.getEvBatteryStateOfCharge` | 같은 파일 |
| 이번 주행 시간(귀갓길 휴식 제안) | Vehicle SDK `Odometer.getTripCurrentDrivingTime` | 같은 파일 |
| 현재 위치 | NaviHelper `getCurrentLocationInfo` → `onCurrentLocationInfo` | `navi/NaviBridge.kt` |
| 안내 시작 | NaviHelper `requestRoute`, `requestRouteWithWaypoints` | 같은 파일 |
| 남은 시간, 거리로 다시 판단(플랜 B) | NaviHelper `onDestinationInfo`, `onDrivingInfo` | 같은 파일, `AppController.onNavi` |
| 도착 확인 | NaviHelper `onDestinationArrived`, `onWayPointArrived` | 같은 파일 |
| 앱이 먼저 하는 음성 안내 | Pleos TextToSpeech SDK(없으면 안드로이드 기본 TTS) | `voice/Speaker.kt` |
| 음성 명령 | Gleo Interface: `assets/schema.json`(App Schema), 인텐트 `ACTION_PARAM`, Callback 브로드캐스트 `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE` | `gleo/Gleo.kt`, `MainActivity.kt` |

Gleo 함수는 공식 규칙(최대 3개, 영어 설명)에 맞춰 세 개다.

| 함수 | 하는 일 | Callback status |
|---|---|---|
| `SUGGEST_FESTIVAL_NOW` | 지금 가면 아직 볼 수 있는 축제, 행사 한 곳 추천. 시간 예산, 귀가 시각, 동행, 분위기, 우천 여부, 다른 후보 요청 | 찾으면 `asking`("갈까요?"), 없으면 `fail` |
| `CHECK_FESTIVAL_ARRIVAL` | 이름을 말한 행사를 지금 가면 볼 수 있는지 판단. 늦으면 대안 제시. `question`이 있으면 그 행사에 대한 질문(입장료, 프로그램, 주차, 편의시설 등)에 트래블젠 서버 RAG로 답함 | `asking` 또는 `success` |
| `START_FESTIVAL_ROUTE` | 추천한 행사, 이름을 말한 행사, 집(`target: home`)으로 안내 시작 | `success` 또는 `fail` |

### 행사 질문 (트래블젠 서버 RAG)

Gleo는 LLM이라 질문 종류마다 함수를 나누지 않는다. 함수는 공식 제한(최대 3개)에 맞춰 세 개 그대로이고, `CHECK_FESTIVAL_ARRIVAL`의 `question`으로 질문 원문을 받는다.

1. 앱이 트래블젠 서버 `POST /api/car/ask`에 행사 id, 질문, 앱 자료를 보낸다(`data/CarAsk.kt`).
2. 서버가 행사 문서(RAG 저장소 `event_chunks`, 없으면 웹 축제 상세와 같은 행사 정보)와 앱 자료를 근거로 두 문장 안의 답을 만든다.
3. 앱이 그 답을 Callback으로 돌려주고 화면 아래 카드에 12초 동안 보여 준다. Gleo는 이 답을 바탕으로 최종 답을 말한다.

- 앱 자료(`FestivalText.appContext`): 기간, 운영 시간, 요금, 프로그램, 주차장, 편의시설. 가정 데이터 줄에는 "(확인 전)"을 붙이고, 서버는 그 줄을 쓰면 "아직 확인하는 중인 정보예요"를 덧붙인다.
- 서버를 쓸 수 없으면(키 없음, 8초 초과, 오류) 앱 자료를 Callback `data`에 담아 Gleo가 그걸로 답하게 한다.
- 모든 Callback `data`의 `details`에 같은 앱 자료를 넣는다. 추천 뒤 "거기 입장료 얼마야?" 같은 이어지는 질문은 Gleo가 대화 안의 자료로 답할 수 있다.
- 서버 주소와 차량 키는 `local.properties`로 정한다(저장소에 올리지 않는다). 키가 없으면 서버에 묻지 않는다.

```properties
travelgen.apiBase=https://travelgen.kr
travelgen.carKey=...   # 서버 환경 변수 CAR_API_KEY 와 같은 값
```

로컬 서버로 시험할 때(debug 빌드만 http 허용):

```bash
adb reverse tcp:3100 tcp:3100          # 에뮬레이터의 127.0.0.1:3100 → PC의 3100
# local.properties: travelgen.apiBase=http://127.0.0.1:3100
adb shell "am start -a CHECK_FESTIVAL_ARRIVAL -p kr.bandi.car --es ACTION_PARAM '{\"festivalName\":\"세미원\",\"question\":\"유모차 끌고 가도 돼?\"}'"
adb logcat -s Bandi/CarAsk Bandi/Gleo
```

## 아직 가정이거나 확인이 필요한 것

- **축제 데이터**: TourAPI 축제공연행사 표본 80건을 앱에 넣었다(`assets/festivals.json`). 다음 단계에서 TravelGen 서버 API로 바꾼다.
- **주차장, 편의시설**: TourAPI에 없어 시연용 가정 데이터다. 그래서 기본은 **행사장 좌표로 안내**하고, 주차장 정보는 "확인하는 중"으로 표시한다. 개발 도구의 "가정 주차장 데이터로 안내"를 켜면 가정 주차장 좌표로 안내한다.
- **충전소, 졸음쉼터**: 가짜 좌표를 넣지 않는다. 충전이 필요하면 "내비의 충전소 추천을 따라가 달라"고 안내하고, 귀갓길에는 쉬어 가라고만 말한다. 실제 POI 데이터를 붙이면 경유지로 넣는다.
- **후보별 도착 예정 시각**: NaviHelper는 설정한 목적지 하나의 남은 시간만 주므로, 후보 비교는 직선거리 x 1.3과 평균 속도로 어림한다. 안내를 시작한 뒤에는 NaviHelper의 남은 시간으로 다시 판단한다.
- **단위**: SDK 문서에 없어 Pleos Connect Emulator 2.1.0과 Virtual Console로 확인했다.
  - 주행 가능 거리: 미터. Virtual Console로 1,500m를 넣어 앱과 계기판 모두 1km로 표시되는 것을 확인했다.
  - NaviHelper `remainTime`은 초, `remainDistance`는 미터. 원값 1560, 22000이 내비 화면의 26분, 22km와 일치했다.
  - 이번 주행 시간: 분으로 본다. Virtual Console이 `TRIP_DRIVING_TIME`을 `usage_time_min`(분)으로 정의한다. 다만 에뮬레이터 이미지가 이 속성을 지원하지 않아(`ERROR_NOT_SUPPORTED_PROPERTY`) 실제 값은 받지 못했다. 실차에서 `adb logcat -s Bandi/Vehicle`의 원값 로그로 다시 확인한다.
- **행사 질문 응답 시간**: 로컬 시험에서 서버 답은 대부분 2~3초, 가끔 8초 걸렸다(Gemini 응답 시간). Gleo가 Callback을 얼마나 기다리는지는 문서에서 확인하지 못했다.
- **Gleo 연동 활성화**: App Schema는 Gleo 쪽 검토와 승인을 거쳐야 실제 음성 명령으로 불린다. 승인 전에는 아래 adb 명령으로 Gleo와 똑같은 인텐트를 보내 시험한다.
- **QR 코드 주소**: `https://travelgen.kr/ko/events/<contentid>?from=car`. TravelGen 웹에 이미 있는 축제 상세 페이지다. 표본 80건 모두 200으로 열리는 것을 확인했다. `from=car`는 아직 웹에서 집계하지 않는다.
- **에뮬레이터 확인**: Pleos Connect Emulator 2.1.0(macOS arm64, 화면 2560x1440, 160dpi)에서 화면, Vehicle SDK(기어, 주행 가능 거리, 배터리), NaviHelper(안내 시작, 남은 시간과 거리), Pleos TextToSpeech(Client ID 없이 온디바이스 모드), Gleo 함수 세 개의 인텐트 수신과 Callback 전송을 확인했다. 실차와 Gleo 실제 음성 호출은 아직 확인하지 않았다.
- **도착 신호**: 에뮬레이터에서는 내비의 차량 위치가 움직이지 않는다. Virtual Console의 속도와 `adb emu geo fix`로 시험했지만 내비 위치와 남은 거리는 그대로였고, 공식 문서에도 모의 주행 방법이 없다. 그래서 `onDestinationArrived`가 오지 않는다. 시연 때는 아래 개발 도구 인텐트(`arrive`)로 도착 신호를 넣는다. 이 신호는 Gleo 연동 기록에 "개발 도구로 넣은 도착 신호"로 따로 남는다.

## 개발 환경 준비

공식 문서 [Pleos Connect Emulator 설치 및 실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)을 따른다.

1. 최신 Android Studio 설치
2. Tools > SDK Manager > SDK Update Sites에 추가
   - 이름: Pleos Connect System Image
   - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - Windows, Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
3. SDK Platforms에서 Android API 34의 Pleos Connect 시스템 이미지 설치, Android Studio 재시작
4. Device Manager > Create device > Automotive > Pleos Connect v2.1.0, 고급 설정에서 Cold boot, RAM 4GB
5. 기어, 배터리, 주행 가능 거리는 [Virtual Console](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)로 바꾼다.

TextToSpeech 서버 모드를 쓰려면 Pleos Playground 프로젝트의 Client ID, Secret을 `local.properties`에 넣는다(저장소에 올리지 않는다).

```properties
pleos.clientId=...
pleos.clientSecret=...
```

없으면 온디바이스 모드, 그것도 안 되면 안드로이드 기본 TTS를 쓴다.

## 빌드, 실행

```bash
./gradlew testDebugUnitTest   # 판단 엔진, Gleo 인자 해석 테스트
./gradlew installDebug        # 에뮬레이터에 설치
```

SDK는 `https://nexus-playground.pleos.ai/repository/maven-releases/`에서 받는다(Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1).

## Gleo 없이 시험하기 (Gleo와 같은 인텐트)

```bash
# 3시간, 7살 아이와 갈 만한 축제
adb shell "am start -a SUGGEST_FESTIVAL_NOW -p kr.bandi.car --es ACTION_PARAM '{\"timeBudgetMinutes\":180,\"withChildAge\":7}'"

# 다른 후보
adb shell "am start -a SUGGEST_FESTIVAL_NOW -p kr.bandi.car --es ACTION_PARAM '{\"anotherOption\":true}'"

# 지금 가면 볼 수 있나
adb shell "am start -a CHECK_FESTIVAL_ARRIVAL -p kr.bandi.car --es ACTION_PARAM '{\"festivalName\":\"나오라쇼\"}'"

# 추천한 곳으로 안내, 집으로 안내
adb shell "am start -a START_FESTIVAL_ROUTE -p kr.bandi.car --es ACTION_PARAM '{\"target\":\"recommended\"}'"
adb shell "am start -a START_FESTIVAL_ROUTE -p kr.bandi.car --es ACTION_PARAM '{\"target\":\"home\"}'"

# Gleo로 보내는 Callback 확인
adb logcat -s Bandi/Gleo
```

표본 데이터는 2026년 7월에 열리는 행사가 많다. 대기 화면의 "시연 시각(7월 11일 오후 4시 30분)으로 보기"를 누르면(release 포함) 시연 시나리오를 그대로 재현할 수 있다. debug 빌드는 오른쪽 위 렌치 버튼에서도 켤 수 있다.

## 시연용 개발 도구 인텐트

화면을 누르지 않고 시연 영상을 스크립트로 녹화할 때 쓴다. release 빌드는 `demoClock`만 받고 나머지는 받지 않는다.

```bash
DEV="am start -n kr.bandi.car/.MainActivity -a kr.bandi.car.DEV"
adb shell $DEV --ez demoClock true   # 시연 시각(2026-07-11 16:30) 켜기
adb shell $DEV --ez rain true        # 비 오는 중
adb shell $DEV --ez homeHere true    # 지금 위치를 집으로
adb shell $DEV --ez arrive true      # 도착 신호 넣기(에뮬레이터는 내비 위치가 움직이지 않음)
```

기어, 속도, 주행 가능 거리는 Virtual Console(`adb forward tcp:8088 tcp:8088` 뒤 `http://localhost:8088`)에서 바꾼다. 에뮬레이터를 새로 켜면 주행 가능 거리가 0으로 돌아가니 시연 전에 다시 넣는다.

## 구조

```
app/src/main/
  assets/schema.json            Gleo App Schema(함수 3개)
  assets/festivals.json         TourAPI 축제 표본 + 시연용 편의 정보
  java/kr/travelgen/car/
    MainActivity.kt             런처, Gleo Action 인텐트 수신
    AppController.kt            상태, 흐름(추천 → 안내 → 플랜 B → 도착 → P단 QR → 귀갓길)
    engine/                     판단 엔진(안드로이드 의존 없음, 단위 테스트)
    gleo/Gleo.kt                ACTION_PARAM 해석, Callback 전송
    vehicle/VehicleMonitor.kt   Pleos Vehicle SDK
    navi/NaviBridge.kt          Pleos NaviHelper SDK
    voice/Speaker.kt            Pleos TextToSpeech SDK
    ui/BandiScreen.kt           Compose 화면
```
