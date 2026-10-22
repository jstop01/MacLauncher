# MacLauncher

갤럭시(One UI)용 macOS 스타일 홈 화면 위젯 (Kotlin).

## 위젯
- **시스템 모니터**: CPU 링 게이지 + 그래프, 메모리 사용량 + 그래프. 포그라운드 서비스로 화면이 켜져 있는 동안 1초마다 갱신 (알림창에 '시스템 모니터' 알림 표시, 화면 꺼지면 정지)
- **달력**: 월간 달력. ◀ ▶로 달 이동, 년·월 탭 → 이번 달, 날짜 탭 → 캘린더 앱. 자정에 자동 갱신

- **Finder**: 사이드바(즐겨찾는 앱·최근 사용 앱·최근 항목·응용 프로그램·다운로드·문서·사진·음악·동영상·내 폰) + 파일 격자. 폴더 탭 → 위젯 안에서 이동, ‹ 로 상위 폴더, 파일 탭 → 기본 앱으로 열기. 처음 한 번 '모든 파일 접근' 권한 필요 (위젯 탭 → 설정). '즐겨찾는 앱'은 ＋ 편집으로 앱을 고르고, 이름·색(macOS 태그 7색)을 정한 폴더로 나눠 담기
- **응용 프로그램**: 앱 전용 Finder. 전체/최근 설치/게임/소셜/사진·동영상/음악/생산성 분류(앱이 밝힌 카테고리 기준) + 앱 격자, 탭하면 실행
- **Dock**: 고른 앱(최대 10개)을 Mac Dock처럼 한 줄로, 탭하면 실행. 추가하면 바로 앱 고르기 화면, 나중엔 앱의 'Dock → 앱 고르기' 또는 위젯 설정에서 변경
- **저장 공간**: 'Macintosh HD'처럼 전체 사용량 + 앱·시스템/사진/동영상/음악/문서·기타 색 막대와 범례. 30분마다, 탭하면 즉시 갱신 (항목별은 '모든 파일 접근' 필요)
- **미리 알림**: 동그라미 탭 = 완료(취소선, 아래로), 글자 탭 = 수정/삭제, + = 추가, 완료 항목 한 번에 지우기. 폰에만 저장
- **배터리**: 폰 + 배터리를 알려주는 연결된 블루투스 기기(최대 3) 링. 20% 이하 빨강, 충전 중 ⚡. 30분마다 / 탭 / 블루투스 연결·해제 때 갱신 (시스템 모니터가 돌면 30초마다). 블루투스는 '근처 기기' 권한 필요, 숨은 API라 기기에 따라 안 보일 수 있음

모두 홈 화면에서 길게 눌러 크기 조절 / 이동 가능. 작게 줄이면 그 크기에 맞는 모양으로 바뀜 (시스템 모니터: 링+메모리만, 달력: 요일/날짜만, 저장 공간: 막대만, Finder: 사이드바 숨김).

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
- `widget/finder/` : Finder 위젯 (`FavoritesStore`/`FavoritesActivity` 즐겨찾기와 폴더, `FinderWidget` 상태/사이드바, `FinderItemsService` 격자 항목, `FinderOpenActivity` 항목 탭 처리, `FinderFiles` 위치·목록)
- `widget/apps/` : 응용 프로그램 위젯 (`AppCatalog` 분류, `AppsWidget`/`AppsItemsService` 격자, `AppLaunchActivity` 실행)
- `widget/dock/` : Dock 위젯 (`DockWidget` 아이콘 줄, `DockConfigActivity` 앱 고르기)
- `ui/AppPicker` : Dock과 즐겨찾기가 같이 쓰는 앱 고르기 화면
- `widget/StorageWidget` : 저장 공간 위젯 (StorageStatsManager 전체량 + MediaStore 항목별 합계)
- `widget/reminders/` : 미리 알림 위젯 (`ReminderStore` 저장, `RemindersWidget`/`RemindersService` 목록, `ReminderActivity` 탭·입력 처리)
- `widget/BatteryWidget` : 배터리 위젯
- `widget/WidgetSize` : 위젯 크기(dp)로 작은/큰 레이아웃 고르기
- `widget/BootReceiver` : 재부팅/업데이트 후 갱신 재시작
- `system/SystemStats` : CPU(/proc/stat → cpufreq 폴백), RAM
- `MainActivity` : 위젯 추가 안내 화면
