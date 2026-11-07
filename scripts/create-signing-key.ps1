# MorningCaller 릴리스 서명 키 생성 (Windows PowerShell 5.1+)
#
#  1) keytool 로 서명 키(.jks)를 만든다
#  2) %USERPROFILE%\.gradle\gradle.properties 에 키 경로/비밀번호를 기록한다
#     → 저장소 밖이라 Git 에 올라가지 않음
#
# 보통은 같은 폴더의 create-signing-key.bat 를 더블클릭해서 실행.

param(
    [string]$KeystorePath = (Join-Path $env:USERPROFILE 'keystores\morningcaller.jks'),
    [string]$Alias = 'morningcaller'
)

$ErrorActionPreference = 'Stop'

function Find-Keytool {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\keytool.exe') }
    $candidates += (Join-Path $env:USERPROFILE 'android-toolchain\jdk\bin\keytool.exe')
    $candidates += 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    $cmd = Get-Command keytool.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Read-PlainPassword([string]$prompt) {
    $secure = Read-Host $prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

Write-Host ''
Write-Host '=== MorningCaller 서명 키 만들기 ===' -ForegroundColor Cyan
Write-Host ''

$keytool = Find-Keytool
if (-not $keytool) {
    Write-Host 'keytool 을 찾을 수 없습니다. JDK(17 이상) 또는 Android Studio 를 설치하세요.' -ForegroundColor Red
    exit 1
}
Write-Host "keytool : $keytool"
Write-Host "키 파일 : $KeystorePath"
Write-Host ''

if (Test-Path $KeystorePath) {
    Write-Host '이미 키 파일이 있습니다. 덮어쓰면 기존 앱을 업데이트할 수 없게 되므로 중단합니다.' -ForegroundColor Yellow
    Write-Host '새로 만들려면 기존 파일을 다른 곳으로 옮긴 뒤 다시 실행하세요.'
    exit 1
}

# 비밀번호 (PKCS12 키스토어는 키 비밀번호 = 스토어 비밀번호)
while ($true) {
    $pw = Read-PlainPassword '비밀번호 (6자 이상, 입력 내용은 표시되지 않음)'
    if ($pw.Length -lt 6) { Write-Host '6자 이상이어야 합니다.' -ForegroundColor Yellow; continue }
    $pw2 = Read-PlainPassword '비밀번호 확인'
    if ($pw -ne $pw2) { Write-Host '비밀번호가 일치하지 않습니다.' -ForegroundColor Yellow; continue }
    break
}

$name = Read-Host '인증서에 넣을 이름 (Enter = MorningCaller)'
if ([string]::IsNullOrWhiteSpace($name)) { $name = 'MorningCaller' }
$name = $name -replace '[,=+<>#;"\\]', ' '

New-Item -ItemType Directory -Force -Path (Split-Path $KeystorePath) | Out-Null

# 비밀번호는 명령줄이 아닌 환경 변수로 넘김 (프로세스 목록에 노출 방지)
$env:MC_KS_PW = $pw
try {
    & $keytool -genkeypair -keystore $KeystorePath -storetype PKCS12 `
        -alias $Alias -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=$name" -storepass:env MC_KS_PW -keypass:env MC_KS_PW
    if ($LASTEXITCODE -ne 0) { throw "keytool 실패 (exit $LASTEXITCODE)" }
} finally {
    Remove-Item Env:MC_KS_PW -ErrorAction SilentlyContinue
}

# ---- gradle.properties 에 기록 ----
$gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
New-Item -ItemType Directory -Force -Path $gradleHome | Out-Null
$propsPath = Join-Path $gradleHome 'gradle.properties'

function Escape-Prop([string]$v) { return $v.Replace('\', '\\') }

$keys = @('MC_STORE_FILE', 'MC_STORE_PASSWORD', 'MC_KEY_ALIAS', 'MC_KEY_PASSWORD')
$lines = @()
if (Test-Path $propsPath) {
    $lines = @(Get-Content $propsPath -Encoding UTF8 | Where-Object {
        $line = $_
        -not ($keys | Where-Object { $line -match "^\s*$_\s*=" })
    })
}
$lines += '# MorningCaller release signing (scripts/create-signing-key.ps1)'
$lines += 'MC_STORE_FILE=' + ($KeystorePath -replace '\\', '/')
$lines += 'MC_STORE_PASSWORD=' + (Escape-Prop $pw)
$lines += 'MC_KEY_ALIAS=' + $Alias
$lines += 'MC_KEY_PASSWORD=' + (Escape-Prop $pw)
[IO.File]::WriteAllLines($propsPath, [string[]]$lines, (New-Object Text.UTF8Encoding($false)))
$pw = $null; $pw2 = $null

Write-Host ''
Write-Host '완료!' -ForegroundColor Green
Write-Host "  키 파일   : $KeystorePath"
Write-Host "  설정 파일 : $propsPath"
Write-Host ''
Write-Host '※ 키 파일과 비밀번호를 잃어버리면 앱을 업데이트할 수 없습니다.' -ForegroundColor Yellow
Write-Host '  키 파일을 USB·클라우드 등 다른 곳에 꼭 백업하세요.' -ForegroundColor Yellow
Write-Host ''
Write-Host '이제 프로젝트 폴더에서 release 빌드를 하면 이 키로 서명됩니다:'
Write-Host '  gradlew.bat assembleRelease'
