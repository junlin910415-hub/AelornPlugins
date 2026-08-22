# AelornLib 獨立建置腳本（Windows PowerShell 5.1 相容）
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
#   <ServerRoot>\plugins\ProtocolLib*.jar         封包後端的編譯相依
#   <ServerRoot>\plugins\PacketEvents-*.jar       封包後端的編譯相依
#
# 兩個封包函式庫是「編譯期需要、執行期選配」:後端類別只有在 marker 類別確認
# 存在後才會被載入,兩者都沒安裝時核心照常啟用,只是失去封包攔截。

param(
    [string] $ServerRoot = "",
    [string] $JavaHome   = ""
)

$ErrorActionPreference = "Stop"
$Project = Split-Path -Parent $MyInvocation.MyCommand.Path

# 伺服器跑 JDK 25,所以編譯器也用 25 —— 用 26 的 javac 搭 --release 25 雖然也編得出
# class 69,但 javac 26 會拿 26 的 API 簽章去比對「25 有沒有這個方法」,而那份對照表
# 是隨 JDK 走的。同版編譯把這層不確定性整個拿掉。
# 找不到 25 時才往上退,並且明說退到哪一版 —— 靜默用別版是最難查的那種問題。
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $jdkRoot = "C:\Program Files\Java"
    $preferred = @(Get-ChildItem -Path $jdkRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "jdk-25*" } | Sort-Object Name -Descending)
    if ($preferred.Count -gt 0) {
        $JavaHome = $preferred[0].FullName
    } else {
        $fallback = @(Get-ChildItem -Path $jdkRoot -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "jdk-2*" } | Sort-Object Name -Descending)
        if ($fallback.Count -eq 0) { throw "$jdkRoot 底下找不到任何 JDK。請用 -JavaHome 指定。" }
        $JavaHome = $fallback[0].FullName
        Write-Host "   找不到 JDK 25,改用 $(Split-Path -Leaf $JavaHome)（--release 25 仍會產生 class 69）" -ForegroundColor DarkYellow
    }
}
Write-Host "   JDK: $JavaHome" -ForegroundColor DarkGray

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
# class 70,插件會以 UnsupportedClassVersionError 載入失敗 —— 而且是靜默失敗,
# 伺服器照常起來,只有日誌裡一行 ERROR。
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

function Find-Plugin([string] $pattern) {
    $hit = Get-ChildItem -Path (Join-Path $ServerRoot "plugins") -Filter $pattern -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $hit) { throw "plugins\ 裡找不到 $pattern（封包後端的編譯相依）" }
    Write-Host "   dep: $($hit.Name)" -ForegroundColor DarkGray
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
    # 資料層:internal\data\HikariDatabase 與 CaffeineCache 直接使用它們。
    (Find-Library "HikariCP-*.jar"),
    (Find-Library "slf4j-api-*.jar"),
    (Find-Library "caffeine-*.jar"),
    # 自建封包後端要往 Netty pipeline 插一個 handler。
    # Netty 不是伺服器內部型別 —— 它是每個核心都有、四年沒動過的第三方函式庫,
    # 用到的 Channel / ChannelPipeline / ChannelDuplexHandler 在 4.1 與 4.2 是同一份
    # class（byte 數相同）。所以這個相依不會把版本無關層綁死在某一版核心上。
    (Find-Library "netty-transport-4*.jar"),
    (Find-Library "netty-common-4*.jar"),
    (Find-Library "netty-buffer-4*.jar"),
    # 封包層後端。執行期選配,編譯期必須。
    (Find-Plugin "ProtocolLib*.jar"),
    (Find-Plugin "PacketEvents-*.jar")
)

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

# ── 0. 分層閘門 ───────────────────────────────────────────────────────────
# lib.* 是契約層,lib.internal.* 是實作層。實作型別一旦出現在契約層,依賴插件
# 就被迫連實作一起綁。比對 FQN 本身而非只比對 import,所以 import static 與
# 完全限定名稱都擋得到;排除註解行,免得 javadoc 提到規則本身就被誤判。
# AelornLibPlugin 是唯一豁免:它是接線的地方,本來就要認識實作。
$internalPattern = '^(?!\s*(\*|//|/\*)).*tw\.linsy\.aelorn\.lib\.internal\.'
$allSources = @(Get-ChildItem -Recurse -Filter *.java (Join-Path $Project "src\main\java") |
    ForEach-Object { $_.FullName })
$leaks = @()
foreach ($file in $allSources) {
    if ($file -like "*\lib\internal\*") { continue }
    if ($file -like "*\AelornLibPlugin.java") { continue }
    if (Select-String -LiteralPath $file -Pattern $internalPattern -Quiet -ErrorAction SilentlyContinue) {
        $leaks += (Split-Path -Leaf $file)
    }
}
if ($leaks.Count -gt 0) {
    throw "契約層有 $($leaks.Count) 個檔案引用了 lib.internal（" + ($leaks -join '、') + "）。"
}

# ── 1. 版本無關層 ─────────────────────────────────────────────────────────
$adapterRoot = Join-Path $Project "src\main\java\tw\linsy\aelorn\lib\nms\impl"
$sources = @($allSources | Where-Object { -not $_.StartsWith($adapterRoot) })

Write-Host "== 編譯 AelornLib（$($sources.Count) 個檔案, 無核心, --release $Release）" -ForegroundColor Cyan
$argFile = Join-Path $Project "build\javac.args"
Write-ArgFile $argFile (@(
    '-encoding', 'UTF-8', '-nowarn', '--release', $Release,
    '-cp', "`"$($BaseCp -join ';')`"", '-d', "`"$classes`""
) + ($sources | ForEach-Object { "`"$_`"" }))
Invoke-Javac $argFile (Join-Path $Project "build\javac.log") "AelornLib"

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
#
# Purpur 排第一是因為它是正式服跑的核心:出貨的那份 class 檔要對著正式服的簽章編出來。
# Folia 留在後面當閘門 —— 它的 region 執行緒讓某些內部型別長得不一樣,一起編才擋得住
# 「只在單執行緒核心上成立」的寫法。兩者順序調換不會改變輸出內容(同一份原始碼),
# 但會改變「哪一版的簽章先被驗證」,而先驗證的那一版就是我們保證跑得起來的那一版。
$serverJars = @(
    (Join-Path $ServerRoot "versions\26.2\purpur-26.2.jar"),
    (Join-Path $ServerRoot "versions\26.2\folia-26.2.jar")
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
$outJar = Join-Path $libs "AelornLib-$version.jar"
& $Jar --create --file $outJar --date=$JarEntryDate `
    -C $classes . -C (Join-Path $Project "src\main\resources") .
if ($LASTEXITCODE -ne 0) { throw "打包失敗" }

Write-Host "   -> $outJar" -ForegroundColor Green
