# PluginsManager 獨立建置腳本（Windows PowerShell 5.1 相容）
#
# 艾洛恩的完整建置走上一層的 build-all.ps1（它會依序建整個 plugin-sources）。
# 這一支是給「只 clone 了這個 repo」的情況用的:同樣的編譯規則,但只建這個專案。
#
# 用法:
#   powershell -ExecutionPolicy Bypass -File build.ps1
#   powershell -ExecutionPolicy Bypass -File build.ps1 -ServerRoot "D:\你的伺服器"
#
# 需要伺服器樹提供的東西（都是無法進版控的第三方二進位檔）:
#   <ServerRoot>\libraries\                       編譯用的 API 與函式庫
#   <ServerRoot>\versions\26.2\folia-26.2.jar     NMS adapter 的編譯目標
#   <ServerRoot>\versions\26.2\purpur-26.2.jar    可選:第二核心閘門
#   <ServerRoot>\plugins\AelornLib-*.jar         可選:核心委派層的編譯相依

param(
    [string] $ServerRoot = "",
    [string] $JavaHome   = "C:\Program Files\Java\jdk-26"
)

$ErrorActionPreference = "Stop"
$Project = Split-Path -Parent $MyInvocation.MyCommand.Path

# 預設值靠「往上找到含 libraries\ 的目錄」而不是數層數:這個 repo 可能被 clone 到
# 任何地方,也可能還待在 plugin-sources\ 底下,兩種情況的深度不一樣。
if ([string]::IsNullOrWhiteSpace($ServerRoot)) {
    $probe = $Project
    while ($null -ne $probe -and -not (Test-Path -LiteralPath (Join-Path $probe "libraries"))) {
        $probe = Split-Path -Parent $probe
    }
    if ($null -eq $probe -or $probe -eq "") {
        throw "從 $Project 往上找不到含 libraries\ 的伺服器目錄。請用 -ServerRoot 指定。"
    }
    $ServerRoot = $probe
}
Write-Host "   伺服器樹: $ServerRoot" -ForegroundColor DarkGray

# 伺服器 version.json 標 java_version 25,class 69 是上限;用 26 直編會產生
# class 70,插件會以 UnsupportedClassVersionError 載入失敗。
$Release = "25"
# 固定 JAR entry 時間,讓同一份 source 的離線重建可用 SHA-256 精確驗證。
$JarEntryDate = "2025-01-01T00:00:00Z"

$Javac = Join-Path $JavaHome "bin\javac.exe"
$Jar   = Join-Path $JavaHome "bin\jar.exe"
foreach ($tool in @($Javac, $Jar)) {
    if (-not (Test-Path -LiteralPath $tool)) { throw "找不到 $tool（用 -JavaHome 指定 JDK）" }
}

$Lib = Join-Path $ServerRoot "libraries"
if (-not (Test-Path -LiteralPath $Lib)) {
    throw "找不到 $Lib。這個 repo 只含原始碼;編譯需要伺服器的 libraries\ 樹（用 -ServerRoot 指定）。"
}

# libraries\ 樹裡常有同一個 artifact 的多個版本（adventure 4.x 與 5.x 並存等）。
# 依檔名反向排序取最高版:上一層的 build-all.ps1 是把版本號釘死,這裡不釘是因為
# 這支腳本要能跟著伺服器升版走,而選錯版本的症狀（"cannot access <某個新類別>"）
# 很難判讀,所以把實際選中的 jar 印出來。
function Find-Library([string] $pattern) {
    $hit = Get-ChildItem -Path $Lib -Recurse -Filter $pattern -File -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($null -eq $hit) { throw "libraries\ 裡找不到 $pattern" }
    Write-Host "   lib: $($hit.Name)" -ForegroundColor DarkGray
    return $hit.FullName
}

# 版本無關層的 classpath。刻意不含伺服器核心 —— 任何 net.minecraft /
# org.bukkit.craftbukkit 型別洩漏到這一層,這裡就會當場編不過。
$BaseCp = @(
    (Find-Library "folia-api-*.jar"),
    (Find-Library "adventure-api-*.jar"),
    (Find-Library "adventure-key-*.jar"),
    (Find-Library "adventure-text-minimessage-*.jar"),
    (Find-Library "adventure-text-serializer-legacy-*.jar"),
    (Find-Library "adventure-text-serializer-plain-*.jar"),
    (Find-Library "examination-api-*.jar"),
    (Find-Library "annotations-*.jar"),
    (Find-Library "guava-*.jar"),
    # 沒有直接用到,但 CommandSender.sendMessage 有一個吃 BaseComponent 的多載,
    # javac 解析多載時要看得到它,否則會報 "cannot access BaseComponent"。
    (Find-Library "bungeecord-chat-*.jar"),
    # 選用的 SQL 稽核連線池。執行期不在也沒關係,audit\ConnectionSources 會探測後退回。
    (Find-Library "HikariCP-*.jar"),
    (Find-Library "slf4j-api-*.jar")
)

# AelornLib 是 softdepend:只在編譯期需要（platform\CoreSched 與 CoreRenderer），
# 執行期不在就走內建回退。找不到就跳過那兩個檔案。
$corePattern = Join-Path $ServerRoot "plugins\AelornLib-*.jar"
$coreJar = Get-ChildItem -Path $corePattern -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -ne $coreJar) {
    Write-Host "   core: $($coreJar.Name)" -ForegroundColor DarkGray
    $BaseCp += $coreJar.FullName
} else {
    throw "找不到 AelornLib JAR（$corePattern）。它是編譯期相依,執行期才是選用的。"
}

$classes = Join-Path $Project "build\classes"
$libs    = Join-Path $Project "build\libs"
if (Test-Path -LiteralPath $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
New-Item -ItemType Directory -Force $classes | Out-Null
New-Item -ItemType Directory -Force $libs    | Out-Null

# javac 的 @argfile:反斜線在裡面是跳脫字元,一律改成正斜線;而且必須是 UTF-8 無 BOM,
# 否則第一個選項會被解析成 "﻿-encoding"。
function Write-ArgFile([string] $path, [string[]] $lines) {
    $text = (($lines | ForEach-Object { $_ -replace '\\', '/' }) -join "`n") + "`n"
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

function Invoke-Javac([string] $argFile, [string] $logFile, [string] $what) {
    # PowerShell 5.1 直接呼叫原生指令會吞掉 stderr,javac 的崩潰堆疊也會一起消失,
    # 所以透過 cmd 導向檔案再讀回來。
    cmd.exe /c "chcp 65001 >nul && `"$Javac`" @`"$argFile`" > `"$logFile`" 2>&1"
    $exit = $LASTEXITCODE
    $diagnostics = @(Get-Content -LiteralPath $logFile -Encoding UTF8 -ErrorAction SilentlyContinue)
    if ($diagnostics.Count -gt 0) { $diagnostics | ForEach-Object { Write-Host "   $_" } }
    if ($exit -ne 0) { throw "$what 編譯失敗" }
}

# ── 1. 版本無關層 ─────────────────────────────────────────────────────────
$adapterRoot = Join-Path $Project "src\main\java\tw\linsy\aelorn\plugins\nms\impl"
$sources = @(Get-ChildItem -Recurse -Filter *.java (Join-Path $Project "src\main\java") |
    Where-Object { -not $_.FullName.StartsWith($adapterRoot) } |
    ForEach-Object { $_.FullName })

Write-Host "== 編譯 PluginsManager（$($sources.Count) 個檔案, 無核心, --release $Release）" -ForegroundColor Cyan
$argFile = Join-Path $Project "build\javac.args"
Write-ArgFile $argFile (@(
    '-encoding', 'UTF-8', '-nowarn', '--release', $Release,
    '-cp', "`"$($BaseCp -join ';')`"", '-d', "`"$classes`""
) + ($sources | ForEach-Object { "`"$_`"" }))
Invoke-Javac $argFile (Join-Path $Project "build\javac.log") "PluginsManager"

# ── 2. NMS adapter：一版一編 ──────────────────────────────────────────────
# 整個 libraries\ 樹都要上 classpath:NMS 的方法簽章引用 datafixers、fastutil 等
# 伺服器自身相依,只挑幾個 jar 必定會漏。已釘版的 BaseCp 排最前面,靠 first-wins
# 避免撿到重複 artifact 的舊版本。org.jspecify 缺了的話 javac 會在格式化診斷訊息時
# 直接 crash,輸出只有一行沒有說明的 `1 error`。
function Get-NmsClasspath([string] $serverJar) {
    $pinned = @($serverJar) + $BaseCp
    $rest = Get-ChildItem -Path $Lib -Recurse -Filter *.jar -File |
        ForEach-Object { $_.FullName } |
        Where-Object { $pinned -notcontains $_ }
    return (($pinned + $rest) -join ';')
}

$adapterDir = Join-Path $adapterRoot "v26_2"
$adapterSources = @(Get-ChildItem -Recurse -Filter *.java $adapterDir | ForEach-Object { $_.FullName })

# 第一個核心編進正式輸出;其餘只是閘門,編到丟棄目錄。目的不是產生多份 class,
# 而是證明「同一份 adapter 對這些核心都成立」—— 有一邊搬動了簽章就在這裡當場失敗。
$serverJars = @(
    (Join-Path $ServerRoot "versions\26.2\folia-26.2.jar"),
    (Join-Path $ServerRoot "versions\26.2\purpur-26.2.jar")
)
$primaryDone = $false
for ($i = 0; $i -lt $serverJars.Count; $i++) {
    $serverJar = $serverJars[$i]
    if (-not (Test-Path -LiteralPath $serverJar)) {
        Write-Host "   略過缺少的核心: $(Split-Path -Leaf $serverJar)" -ForegroundColor DarkYellow
        continue
    }
    $isPrimary = -not $primaryDone
    $outDir = if ($isPrimary) { $classes } else { Join-Path $Project "build\nms-verify\26_2-$i" }
    if (-not $isPrimary) {
        if (Test-Path -LiteralPath $outDir) { Remove-Item -LiteralPath $outDir -Recurse -Force }
        New-Item -ItemType Directory -Force $outDir | Out-Null
    }
    $label = if ($isPrimary) { "輸出" } else { "閘門" }
    Write-Host "   NMS adapter 26_2（$($adapterSources.Count) 個檔案, $label）<- $(Split-Path -Leaf $serverJar)" -ForegroundColor DarkCyan

    $adapterArgFile = Join-Path $Project "build\javac-nms-26_2-$i.args"
    Write-ArgFile $adapterArgFile (@(
        '-encoding', 'UTF-8', '-nowarn', '--release', $Release,
        '-cp', "`"$((Get-NmsClasspath $serverJar) + ';' + $classes)`"", '-d', "`"$outDir`""
    ) + ($adapterSources | ForEach-Object { "`"$_`"" }))
    Invoke-Javac $adapterArgFile (Join-Path $Project "build\javac-nms-26_2-$i.log") "NMS adapter 26_2"
    $primaryDone = $true
}
if (-not $primaryDone) { throw "沒有任何可用的伺服器核心,NMS adapter 無法編譯" }

# ── 3. 打包 ───────────────────────────────────────────────────────────────
$version = (Select-String -LiteralPath (Join-Path $Project "src\main\resources\plugin.yml") `
    -Pattern '^version:\s*(.+)$').Matches[0].Groups[1].Value.Trim().Trim("'").Trim('"')
# plugin.yml 的 version 可能有引號（'1.0.0_26.2'）。不去引號的話 jar 會叫
# Name-'1.0.0_26.2'.jar —— 檔名裡帶引號在 Windows 上還真的建得出來,然後
# build-all 的一致性閘門與部署比對全部對不上。
$outJar = Join-Path $libs "PluginsManager-$version.jar"
& $Jar --create --file $outJar --date=$JarEntryDate `
    -C $classes . -C (Join-Path $Project "src\main\resources") .
if ($LASTEXITCODE -ne 0) { throw "打包失敗" }

Write-Host "   -> $outJar" -ForegroundColor Green
