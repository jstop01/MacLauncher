# MacLauncher

갤럭시(One UI)용 macOS 스타일 홈 화면 위젯 (Kotlin).

## 위젯
- **시스템 모니터**: CPU 링 게이지 + 그래프, 메모리 사용량 + 그래프. 포그라운드 서비스로 화면이 켜져 있는 동안 1초마다 갱신 (알림창에 '시스템 모니터' 알림 표시, 화면 꺼지면 정지)
- **달력**: 월간 달력. ◀ ▶로 달 이동, 년·월 탭 → 이번 달, 날짜 탭 → 캘린더 앱. 자정에 자동 갱신

둘 다 홈 화면에서 길게 눌러 크기 조절 / 이동 가능.

## 빌드 / 설치
```
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
앱을 열고 "홈 화면에 추가"를 누르거나, 홈 화면 길게 누르기 → 위젯 → Mac 위젯.

## 구조
- `widget/MonitorWidget` · `MonitorService` · `MonitorGraphics` : 시스템 모니터 위젯, 1초 갱신 서비스, 링/그래프 비트맵
- `widget/CalendarWidget` : 달력 위젯 (RemoteViews 42칸)
- `widget/BootReceiver` : 재부팅/업데이트 후 갱신 재시작
- `system/SystemStats` : CPU(/proc/stat → cpufreq 폴백), RAM
- `MainActivity` : 위젯 추가 안내 화면
