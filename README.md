# 스마트 QR

[스마트 QR 생성기](https://smj1740.github.io/dddff/)를 전체 화면으로 보여주는 안드로이드 앱입니다.

## APK 받기

안드로이드 스튜디오 없이 GitHub Actions가 APK를 만듭니다.

1. 이 폴더를 새 GitHub 저장소의 루트로 올립니다. `main` 브랜치에 `push`하면 빌드가 시작됩니다.
2. 저장소의 **Actions** 탭에서 **Build APK** 실행이 끝날 때까지 기다립니다.
3. 완료된 실행을 열고, 아래 **Artifacts**에서 `smart-qr-apk`를 받습니다.
4. 압축을 풀면 `app-release.apk`가 있습니다. 휴대폰으로 옮겨 설치합니다.

직접 다시 빌드하려면 Actions 탭에서 **Build APK** → **Run workflow**를 누르면 됩니다.

휴대폰에서 설치가 막히면 설정에서 이 출처의 앱 설치를 허용하면 됩니다. 이 APK는 개인 설치용으로, 디버그 키로 서명되어 있습니다. Play 스토어 배포용 서명은 아닙니다.
