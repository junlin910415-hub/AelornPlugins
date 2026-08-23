# 艾洛恩插件建置腳本(Windows PowerShell 5.1 相容)
# 完全離線建置 —— 但需要一棵 Minecraft 伺服器樹提供無法進版控的第三方二進位檔。
#
# 用法:
#   powershell -ExecutionPolicy Bypass -File build-all.ps1 -ServerRoot "D:\你的伺服器"
#   powershell -ExecutionPolicy Bypass -File build-all.ps1 -ServerRoot "..." -Only AelornLib
#
# 伺服器樹要有:
#   <ServerRoot>\libraries\                     編譯用的 API 與函式庫
#   <ServerRoot>\versions\26.2\purpur-26.2.jar  NMS adapter 的編譯目標
#   <ServerRoot>\versions\26.2\folia-26.2.jar   可選:第二核心閘門
#   <ServerRoot>\plugins\                       ProtocolLib、PacketEvents、PlaceholderAPI…
#
# 這些是第三方著作,不進這個倉庫。ServerRoot 也可以用環境變數 AELORN_SERVER_ROOT 指定。

param(
    [string] $ServerRoot = "",
    [string[]] $Only = @()
)

$ErrorActionPreference = "Stop"
# 專案在 plugins\ 底下,不像原本那樣與腳本同層。
$Root = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "plugins"

if ([string]::IsNullOrWhiteSpace($ServerRoot)) { $ServerRoot = $env:AELORN_SERVER_ROOT }
if ([string]::IsNullOrWhiteSpace($ServerRoot)) {
    throw "需要 -ServerRoot（或環境變數 AELORN_SERVER_ROOT）指向一棵 Minecraft 伺服器樹。檔頭有說明要有哪些東西。"
}
$Server = (Resolve-Path -LiteralPath $ServerRoot).Path
$Lib = Join-Path $Server "libraries"
$Plugins = Join-Path $Server "plugins"
foreach ($required in @($Lib, $Plugins)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "伺服器樹缺少 $required。這個倉庫只含原始碼;第三方 JAR 要由伺服器樹提供。"
    }
}

# 伺服器跑 JDK 25,所以編譯器也用 25。用 26 的 javac 搭 --release 25 也編得出 class 69,
# 但那時 javac 是拿「26 隨附的 25 API 對照表」在判斷,而那份表是隨 JDK 走的。
# 同版編譯把這層不確定性整個拿掉。找不到 25 時往上退,並且明說退到哪一版。
$JavaHome = "C:\Program Files\Java\jdk-25.0.3"
if (-not (Test-Path -LiteralPath $JavaHome)) {
    $fallback = @(Get-ChildItem -Path "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "jdk-2*" } | Sort-Object Name -Descending)
    if ($fallback.Count -eq 0) { throw "找不到任何 JDK。" }
    $JavaHome = $fallback[0].FullName
    Write-Host "找不到 JDK 25,改用 $(Split-Path -Leaf $JavaHome)" -ForegroundColor DarkYellow
}
# Kotlin 編譯器取自 IntelliJ 隨附的那一份。這台機器沒有獨立安裝 kotlinc，
# 而 IntelliJ 的版本(2.3.10)與 libraries\ 裡的 stdlib(2.3.0)同一個 minor，相容。
# 只有宣告了 src\main\kotlin\ 的專案才會用到它，所以缺席不影響其他專案。
$KotlinCompiler = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\Kotlin\kotlinc\bin\kotlinc.bat"
$Javac = Join-Path $JavaHome "bin\javac.exe"
$Jar = Join-Path $JavaHome "bin\jar.exe"

# 伺服器以 JDK 25 執行(class 檔上限 69)。用更新的 JDK 直接編譯會產生
# class 版本 70 以上,插件會以 UnsupportedClassVersionError 載入失敗。
# --release 同時鎖定語法層級與 JDK API 範圍。
#
# 預設 21(class 65)是既有插件的相容基準,不要為了語法糖整批往上推 ——
# 那等於把每支插件的回歸風險綁在一起。個別專案用 Release 屬性各自決定。
# AelornLib 走 25(class 69,正好是伺服器 JDK 的上限)。
$DefaultRelease = "21"
# 固定 JAR entry 時間，讓同一份 source/resources 的離線重建可用 SHA-256 精確驗證。
$JarEntryDate = "2025-01-01T00:00:00Z"

$BaseCp = @(
    (Join-Path $Lib "dev\folia\folia-api\26.2.build.1-beta\folia-api-26.2.build.1-beta.jar"),
    (Join-Path $Lib "net\kyori\adventure-api\5.2.0\adventure-api-5.2.0.jar"),
    (Join-Path $Lib "net\kyori\adventure-key\5.2.0\adventure-key-5.2.0.jar"),
    (Join-Path $Lib "net\kyori\adventure-text-minimessage\5.2.0\adventure-text-minimessage-5.2.0.jar"),
    (Join-Path $Lib "net\kyori\adventure-text-serializer-legacy\5.2.0\adventure-text-serializer-legacy-5.2.0.jar"),
    (Join-Path $Lib "net\kyori\examination-api\1.3.0\examination-api-1.3.0.jar"),
    (Join-Path $Lib "org\jetbrains\annotations\26.0.1\annotations-26.0.1.jar"),
    (Join-Path $Lib "com\google\guava\guava\33.6.0-jre\guava-33.6.0-jre.jar"),
    (Join-Path $Lib "net\md-5\bungeecord-chat\1.21-R0.2-deprecated+build.21\bungeecord-chat-1.21-R0.2-deprecated+build.21.jar"),
    # RPGCore 需要:HUD 寬度計算用 plain serializer,HUD 資源包用 gson
    (Join-Path $Lib "net\kyori\adventure-text-serializer-plain\5.2.0\adventure-text-serializer-plain-5.2.0.jar"),
    (Join-Path $Lib "com\google\code\gson\gson\2.14.0\gson-2.14.0.jar")
) -join ";"

# ── NMS(伺服器內部類別)編譯支援 ──────────────────────────────────────
# Folia 26.2 的 versions\ jar 是 paperclip 修補後的完整伺服器,內部為 Mojang-mapped
# 且 CraftBukkit 無版本後綴,所以可以直接編譯,不需要 paperweight 或 remap 步驟。
#
# 兩個非顯而易見的必要條件:
#  1. 整個 libraries\ 樹都要上 classpath。NMS 的方法簽章引用 datafixers、fastutil 等
#     伺服器自身相依,只挑幾個 jar 必定會漏。已釘版的 BaseCp 排最前面,靠 first-wins
#     避免撿到重複 artifact 的舊版本。
#  2. org.jspecify 一定要在。缺了它 javac 會在「格式化診斷訊息」時直接 crash,
#     輸出只有一行沒有說明的 `1 error`,極難判讀。
$ServerJar = Join-Path $Server "versions\26.2\folia-26.2.jar"

function Get-NmsClasspath([string]$serverJar = $ServerJar) {
    if (-not (Test-Path -LiteralPath $serverJar)) {
        throw "找不到伺服器 JAR: $serverJar（NMS 專案無法編譯）"
    }
    $pinned = @($serverJar) + ($BaseCp -split ';')
    $rest = Get-ChildItem -Path $Lib -Recurse -Filter *.jar -File |
        ForEach-Object { $_.FullName } |
        Where-Object { $pinned -notcontains $_ }
    return (($pinned + $rest) -join ';')
}

# javac 的 @argfile:反斜線在裡面是跳脫字元,一律改成正斜線;而且必須是 UTF-8 無 BOM,
# 否則第一個選項會被解析成 "﻿-encoding"。
function Write-ArgFile([string]$path, [string[]]$lines) {
    $text = (($lines | ForEach-Object { $_ -replace '\\', '/' }) -join "`n") + "`n"
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

# 名稱 = 專案資料夾 / 輸出檔名 / 額外 classpath
# ExtraCp 用「檔名前綴萬用字元」而非固定版本號,伺服器插件升版後不需改這支腳本。
# Nms = $true 會把伺服器 jar 與整個 libraries\ 掛上 classpath(見 Get-NmsClasspath)。
# AelornLib 必須排在最前面:其他插件對它有編譯期相依。
$Projects = @(
    # Adapters：每個 Minecraft 版本家族一個 NMS 介接層，各自對「自己那版」的
    # 伺服器核心編譯。26.2.X 這類修補版共用 26_2，修補更新完全不必動這裡。
    # 支援 26.3 的做法：伺服器核心放進 versions/26.3/，下面 Adapters 多一行，並新增
    # AelornLib/src/main/java/tw/linsy/aelorn/lib/nms/impl/v26_3/Bridge26_3.java。
    # 刻意沒有 Nms = $true:版本無關層編譯時 classpath 不含伺服器核心,型別洩漏會當場
    # 編不過。伺服器內部只有下面的 Adapters 摸得到。
    #
    # ServerJars 有兩個核心是刻意的:同一份 adapter 要同時服務 LightingLuminol 26.2
    # 與 Purpur 26.2。第一個是產出用,其餘每個都會再編一次當作閘門 ——
    # 哪天有一邊搬動了成員簽章,這裡當場失敗,而不是等玩家在實機上踩到。
    # 兩個封包函式庫是編譯期相依、執行期選配:後端類別只有在 marker 類別確認存在後才會被載入,
    # 兩者都沒安裝時核心照常啟用(送包走自己的 NMS,只有攔截停用)。
    # LibCp:internal\data\HikariDatabase 直接使用 HikariCP,所以它是編譯期相依。
    # 伺服器的 libraries\ 樹裡已經有,離線建置不需要連網。
    # netty-* 是自建封包後端要往 Netty pipeline 插 handler 用的。Netty 不是伺服器內部
    # 型別（每個核心都有、4.1 與 4.2 的 Channel/ChannelDuplexHandler 是同一份 class），
    # 所以這個相依不會把版本無關層綁死在某一版核心上。
    #
    # ServerJars 的順序是有意義的:第一個編進正式輸出,其餘只當閘門。Purpur 排第一,
    # 因為出貨那份 class 要對著正式服的簽章編出來;Folia 留在後面擋「只在單執行緒核心
    # 上成立」的寫法。
    @{ Name = "AelornLib";           Jar = "AelornLib-1.0.0_26.2";                                 ExtraCp = @("ProtocolLib*.jar", "PacketEvents-*.jar"); Release = "25"; LibCp = @("com\zaxxer\HikariCP\6.3.2\HikariCP-6.3.2.jar", "org\slf4j\slf4j-api\2.0.18\slf4j-api-2.0.18.jar", "com\github\ben-manes\caffeine\caffeine\3.2.4\caffeine-3.2.4.jar", "io\netty\netty-transport\4.1.118.Final\netty-transport-4.1.118.Final.jar", "io\netty\netty-common\4.1.118.Final\netty-common-4.1.118.Final.jar", "io\netty\netty-buffer\4.1.118.Final\netty-buffer-4.1.118.Final.jar"); Adapters = @(@{ Family = "26_2"; ServerJars = @("versions\26.2\purpur-26.2.jar", "versions\26.2\folia-26.2.jar") }) },
    # AelornWorlds 取代 WorldLoaderX(同一支插件改名),以 AelornLib 為執行期相依。
    # Kotlin 擴充面。Java 進入點 + Kotlin 擴充,同時也是混合編譯的實證。
    @{ Name = "AelornLibKt";         Jar = "AelornLibKt-1.0.0_26.2";                            ExtraCp = @(); Core = $true },
    # 純 Kotlin 參考實作。不部署到正式服,存在的意義是證明純 Kotlin 專案建得起來、
    # 並當作 Kotlin 版的 CONVENTIONS 參考。
    @{ Name = "AelornKotlinRef";     Jar = "AelornKotlinRef-1.0.0_26.2";                      ExtraCp = @(); Core = $true; KotlinExt = $true },
    @{ Name = "AelornWorlds";        Jar = "AelornWorlds-2.0.0_26.2";                    ExtraCp = @(); Core = $true },
    # 由反編譯的 1.0.0 重寫,套件改為 tw.linsy.aelorn.worldevents,結構依 CONVENTIONS.md。
    @{ Name = "AelornWorldEvents";   Jar = "AelornWorldEvents-2.0.0_26.2";               ExtraCp = @(); Core = $true },
    @{ Name = "AelornBackpack";      Jar = "AelornBackpack-1.5.0_26.2";                  ExtraCp = @(); Core = $true },
    # Core = $true 起手只是把核心掛上 classpath;實際改用核心的 Messages/ConfigParse
    # 取代自家 config\TextBundle 是後續的事,分開做才看得出哪一步弄壞了什麼。
    # joml 走 LibCp:物品的位移/旋轉計算用到,但它不在無核心的基礎 classpath 裡。
    @{ Name = "AelornItems";         Jar = "AelornItems-3.2.2-AELORN-NEXO_26.2";                    ExtraCp = @("MythicCore-*.jar", "RPGCore-*.jar"); Core = $true; LibCp = @("org\joml\joml\1.10.9\joml-1.10.9.jar") },
    @{ Name = "RPGCoreMythicBridge"; Jar = "RPGCoreMythicBridge-0.2.0_26.2";                       ExtraCp = @("MythicCore-*.jar", "RPGCore-*.jar") },
    @{ Name = "AelornDiscordBridge"; Jar = "AelornDiscordBridge-3.0.0_26.2";           ExtraCp = @("DiscordSRV-*.jar") },
    @{ Name = "AelornQuestBridge";   Jar = "AelornQuestBridge-3.1.0_26.2";             ExtraCp = @("PlaceholderAPI-*.jar"); Core = $true },
    @{ Name = "AelornHolograms";     Jar = "AelornHolograms-1.1.0_26.2";                 ExtraCp = @(); Core = $true },
    # AelornStore 只與 Vault 編譯期相依；JDBC 驅動走 java.sql + Class.forName，不進 classpath。
    @{ Name = "AelornStore";         Jar = "AelornStore-1.1.0_26.2";                     ExtraCp = @("Vault-*.jar"); Core = $true },
    @{ Name = "ServerBackup";        Jar = "ServerBackup-1.6.0_26.2";                    ExtraCp = @("LuckPerms-Bukkit-*.jar"); Core = $true },
    # PluginsManager 自己有一層版本綁定的介接層:它要動 Paper 的 plugin manager 與指令樹,
    # 那是這支插件專屬的需求,不該塞進共用核心。同樣對兩個核心各編一次當閘門。
    # 2.0.0 重寫為分層結構(model / config / platform / nms / service / command / audit)。
    #  Release 25 — 伺服器 version.json 標 java_version 25,class 69 是上限。
    #  Core       — 只是編譯期相依:platform\CoreSched 與 CoreRenderer 是 softdepend
    #               的委派層,核心不在時走內建回退,不會 NoClassDefFoundError。
    #  LibCp      — HikariCP 供選用的 SQL 稽核連線池,同樣「在就用、不在就退回」。
    @{ Name = "PluginsManager";      Jar = "PluginsManager-2.0.0_26.2";                        ExtraCp = @(); Core = $true; Release = "25"; LibCp = @("com\zaxxer\HikariCP\6.3.2\HikariCP-6.3.2.jar", "org\slf4j\slf4j-api\2.0.18\slf4j-api-2.0.18.jar"); AdapterRoot = "src\main\java\tw\linsy\aelorn\plugins\nms\impl"; Adapters = @(@{ Family = "26_2"; ServerJars = @("versions\26.2\folia-26.2.jar", "versions\26.2\purpur-26.2.jar") }) },
    @{ Name = "RPGCore";             Jar = "RPGCore-0.26.0-SNAPSHOT_26.2"; ExtraCp = @("Nexo-*.jar", "AeloriaHUD-*.jar", "MythicMobsPremium-*.jar", "MythicCore-*.jar", "ModelEngine-*.jar", "PlaceholderAPI-*.jar", "Citizens*.jar") }
)
$OnlySet = @($Only | ForEach-Object { $_ -split ',' } | Where-Object { $_ })

# 從伺服器的 libraries/ 樹取一個固定路徑的 JAR(給 LibCp 用)。
# 與 ExtraCp 分開:那個找 plugins/ 下的插件 JAR,這個是伺服器自己的相依樹。
function Resolve-LibraryJar([string]$relativePath) {
    $full = Join-Path $Lib $relativePath
    if (-not (Test-Path -LiteralPath $full)) { throw "找不到相依 JAR: $full" }
    return $full
}

# 依前綴在 plugins/ 找出最新(修改時間最新)的相符 JAR。
function Resolve-PluginJar([string]$pattern) {
    $match = Get-ChildItem -Path $Plugins -Filter $pattern -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $match) { throw "找不到相依 JAR: $pattern (在 $Plugins)" }
    return $match.FullName
}

foreach ($project in $Projects) {
    $name = $project.Name
    if ($OnlySet.Count -gt 0 -and $name -notin $OnlySet) { continue }
    $projectDir = Join-Path $Root $name
    $classes = Join-Path $projectDir "build\classes"
    $libs = Join-Path $projectDir "build\libs"
    if (Test-Path -LiteralPath $classes) {
        Remove-Item -LiteralPath $classes -Recurse -Force
    }
    New-Item -ItemType Directory -Force $classes | Out-Null
    New-Item -ItemType Directory -Force $libs | Out-Null

    # 無伺服器核心為主:一般程式碼一律以「classpath 沒有伺服器核心」編譯,
    # 只有 Adapters 拿得到伺服器內部。這不只是慣例——它就是隔離的證明:
    # 任何 net.minecraft / CraftBukkit 型別洩漏到版本無關層,這一段會直接編不過,
    # 不必再靠人記得去跑額外檢查。
    #
    # Nms = $true 是逃生門,給「整支插件本來就綁死某一版」的舊專案用;
    # 新程式碼不應該使用,要碰伺服器內部請走 AelornLib 的 NmsBridge。
    $useNms = [bool]$project.Nms
    $cp = if ($useNms) { Get-NmsClasspath } else { $BaseCp }
    # Core = $true:對剛建置出來的 AelornLib 編譯,不必先部署到 plugins\。
    # 路徑由上面的專案表推導,核心改版號時不會有第二處要記得跟著改。
    if ($project.Core) {
        $coreEntry = $Projects | Where-Object { $_.Name -eq "AelornLib" } | Select-Object -First 1
        $coreJar = Join-Path $Root ("AelornLib\build\libs\" + $coreEntry.Jar + ".jar")
        if (-not (Test-Path -LiteralPath $coreJar)) {
            throw "$name 需要 AelornLib,但找不到 $coreJar（請先建置 AelornLib）"
        }
        Write-Host "   core: $(Split-Path -Leaf $coreJar)" -ForegroundColor DarkGray
        $cp = $cp + ";" + $coreJar
    }

    # KotlinExt = $true:同樣的道理,但相依的是 Kotlin 擴充面。分開兩個旗標而不是
    # 讓 Core 一併帶上,是因為絕大多數專案是純 Java,不該把 Kotlin 擴充面塞進它們的
    # classpath —— 那會讓「不小心用到 Kotlin API」變成編得過、執行期才炸。
    if ($project.KotlinExt) {
        $ktEntry = $Projects | Where-Object { $_.Name -eq "AelornLibKt" } | Select-Object -First 1
        $ktJar = Join-Path $Root ("AelornLibKt/build/libs/" + $ktEntry.Jar + ".jar")
        if (-not (Test-Path -LiteralPath $ktJar)) {
            throw "$name 需要 AelornLibKt,但找不到 $ktJar（請先建置 AelornLibKt）"
        }
        Write-Host "   kt:   $(Split-Path -Leaf $ktJar)" -ForegroundColor DarkGray
        $cp = $cp + ";" + $ktJar
    }
    foreach ($extra in $project.ExtraCp) {
        $resolved = Resolve-PluginJar $extra
        Write-Host "   dep: $(Split-Path -Leaf $resolved)" -ForegroundColor DarkGray
        $cp = $cp + ";" + $resolved
    }
    # LibCp:伺服器 libraries/ 樹裡的相依,只在編譯期需要。
    # 執行期由伺服器或插件自己的 library loader 提供;不在的話程式碼必須降級,
    # 不能假設它存在(見 audit/ConnectionSources 的 class-presence 探測)。
    foreach ($libEntry in $project.LibCp) {
        $resolvedLib = Resolve-LibraryJar $libEntry
        Write-Host "   lib: $(Split-Path -Leaf $resolvedLib)" -ForegroundColor DarkGray
        $cp = $cp + ";" + $resolvedLib
    }

    # adapter 另外編譯（見下）：它們各自引用不同版的伺服器內部，混在一起編一定壞一邊。
    # @($null) 在 PowerShell 會得到「含一個 null 的陣列」而不是空陣列;不過濾的話,
    # 沒宣告 Adapters 的專案也會跑進下面的 adapter 迴圈,拿到空的 Family 然後炸掉。
    $adapters = @($project.Adapters | Where-Object { $_ })
    # adapter 根目錄預設是 AelornLib 的位置。插件若有自己的版本綁定層(PluginsManager
    # 要碰 Paper 的 plugin manager,那不屬於核心該提供的能力),用 AdapterRoot 指定。
    $adapterRelative = if ($project.AdapterRoot) { $project.AdapterRoot } else { "src\main\java\tw\linsy\aelorn\lib\nms\impl" }
    $adapterRoot = Join-Path $projectDir $adapterRelative

    # 磁碟上的 adapter 目錄必須全部登記在 Adapters。
    # 沒有這道檢查時,照 NmsBridge 文件的升版步驟做到一半 —— 複製出 v26_3\Bridge26_3.java
    # 但忘了在上面的表加一行 —— 建置會成功、不出任何警告,而產出的 JAR 裡沒有那個 adapter。
    # 執行期只會看到一行「本次建置未包含 26.3 的直編 adapter」的資訊,伺服器安靜地跑在
    # 較慢的 MethodHandle 層,作者完全不會知道自己寫的東西沒被編進去。
    if (Test-Path -LiteralPath $adapterRoot) {
        $declared = @($adapters | ForEach-Object { "v" + $_.Family })
        $onDisk = @(Get-ChildItem -LiteralPath $adapterRoot -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { $_.Name })
        $undeclared = @($onDisk | Where-Object { $declared -notcontains $_ })
        if ($undeclared.Count -gt 0) {
            throw "$name 的 $adapterRelative 底下有未登記的 adapter 目錄(" + ($undeclared -join '、') +
                  ")。請在 build-all.ps1 的 Adapters 表補上對應的 Family,否則它不會被編進 JAR。"
        }
    }

    # 純 Kotlin 專案沒有 src\main\java\。Get-ChildItem 對不存在的路徑會丟例外,
    # 而 $ErrorActionPreference = Stop 會讓整個建置停在這裡 —— 所以先問再取。
    $javaRoot = Join-Path $projectDir "src\main\java"
    $sources = @()
    if (Test-Path -LiteralPath $javaRoot) {
        $sources = @(Get-ChildItem -Recurse -Filter *.java $javaRoot |
            Where-Object { $adapters.Count -eq 0 -or -not $_.FullName.StartsWith($adapterRoot) } |
            ForEach-Object { $_.FullName })
    }

    # ExcludeSources:暫時擋掉「還在寫、編不過」的套件,讓其餘部分仍能建置與部署。
    # 這是逃生門,不是設計 —— 所以每次建置都會大聲印出來,不會被默默忘記。
    # 那個套件寫完後,請把專案表裡的 ExcludeSources 那一段刪掉。
    foreach ($excluded in $project.ExcludeSources) {
        $excludedRoot = Join-Path $projectDir (Join-Path "src\main\java" $excluded)
        $before = $sources.Count
        $sources = @($sources | Where-Object { -not $_.StartsWith($excludedRoot) })
        $skipped = $before - $sources.Count
        if ($skipped -gt 0) {
            Write-Host "   !! 暫時排除 $excluded（$skipped 個檔案）—— 記得補完後移除" -ForegroundColor Yellow
        }
    }
    # ── API / internal 隔離閘門 ───────────────────────────────────────────
    # AelornLib 分兩層:lib.* 是契約(其他插件只 import 這裡),lib.internal.* 是實作。
    # 這條規則靠檢查而不是靠記性 —— 實作型別一旦出現在契約層,依賴插件就被迫連實作
    # 一起綁,分層等於白做,而且是那種要等到重構時才會發現的白做。
    #
    # AelornLibPlugin 是唯一豁免:它是接線的地方,本來就要認識實作。
    #
    # 三個曾經漏掉、現在補上的洞:
    #  1. 只掃 $sources 會跳過 adapter —— 它們被排除在主編譯之外,卻拿得到 $classes
    #     裡已編好的 internal 類別,是最可能伸手去用的那個套件。改掃「全部原始碼」。
    #  2. 只比對 `^import ` 會放過 `import static` 與「完全限定名稱直接寫在型別位置」。
    #     改成比對 FQN 本身,並排除註解行,免得 javadoc 提到就誤判。
    #  3. 磁碟上有 adapter 目錄卻沒登記在 Adapters,會被靜默丟掉(見下方 adapter 迴圈後)。
    $internalPattern = '^(?!\s*(\*|//|/\*)).*tw\.linsy\.aelorn\.lib\.internal\.'
    $allSources = @()
    if (Test-Path -LiteralPath (Join-Path $projectDir "src\main\java")) {
        $allSources = @(Get-ChildItem -Recurse -Filter *.java (Join-Path $projectDir "src\main\java") |
            ForEach-Object { $_.FullName })
    }
    $leaks = @()
    foreach ($file in $allSources) {
        if ($file -like "*\lib\internal\*") { continue }
        if ($name -eq "AelornLib" -and $file -like "*\AelornLibPlugin.java") { continue }
        if (Select-String -LiteralPath $file -Pattern $internalPattern -Quiet -ErrorAction SilentlyContinue) {
            $leaks += (Split-Path -Leaf $file)
        }
    }
    if ($leaks.Count -gt 0) {
        throw "$name 有 $($leaks.Count) 個檔案引用了 tw.linsy.aelorn.lib.internal(" +
              ($leaks -join '、') + ")。internal 是實作層,契約層、adapter 與其他插件都不得引用。"
    }

    $release = if ($project.Release) { $project.Release } else { $DefaultRelease }
    $mode = if ($useNms) { "NMS(整支綁版)" } elseif ($adapters.Count -gt 0) { "無核心 + $($adapters.Count) adapter" } else { "無核心" }
    Write-Host "== Compiling $name ($($sources.Count) files, $mode, --release $release)" -ForegroundColor Cyan

    # javac 沒有輸入檔就會報 "no source files" 並以非零離開。純 Kotlin 專案是合法的,
    # 所以這裡不是錯誤,是「這一段沒事可做」。
    if ($sources.Count -eq 0) {
        Write-Host "   （沒有 Java 原始碼，跳過 javac）" -ForegroundColor DarkGray
    } else {

    # NMS 的 classpath 有近 300 個 JAR,遠超過 Windows 的命令列長度上限,所以一律走 @argfile。
    $argFile = Join-Path $projectDir "build\javac.args"
    $logFile = Join-Path $projectDir "build\javac.log"
    Write-ArgFile $argFile (@(
        '-encoding', 'UTF-8', '-nowarn', '--release', $release,
        '-cp', "`"$cp`"", '-d', "`"$classes`""
    ) + ($sources | ForEach-Object { "`"$_`"" }))
    # PowerShell 5.1 直接呼叫原生指令會吞掉 stderr,javac 的崩潰堆疊也會一起消失,
    # 所以透過 cmd 導向檔案再讀回來。
    cmd.exe /c "chcp 65001 >nul && `"$Javac`" @`"$argFile`" > `"$logFile`" 2>&1"
    $compileExit = $LASTEXITCODE
    $diagnostics = @(Get-Content -LiteralPath $logFile -Encoding UTF8 -ErrorAction SilentlyContinue)
    if ($diagnostics.Count -gt 0) { $diagnostics | ForEach-Object { Write-Host "   $_" } }
    if ($compileExit -ne 0) { throw "$name compile failed" }
    }

    # ── Kotlin（可選）─────────────────────────────────────────────────────
    # 專案有 src\main\kotlin\ 就編它，沒有就整段跳過。順序是「Java 先、Kotlin 後」:
    # kotlinc 讀得懂已編好的 .class，而 javac 讀不懂 .kt，所以 Kotlin 可以呼叫 Java，
    # 反向不行。這對一個「Java 核心 + Kotlin 擴充面」的專案正好是對的方向。
    #
    # -no-stdlib 是必要的:預設 kotlinc 會把自己那份 stdlib 塞進 classpath，
    # 而伺服器上跑的是 libraries\ 裡的那一份。兩份混用會在執行期出現
    # NoSuchMethodError，且只在用到差異 API 時才炸。
    $kotlinRoot = Join-Path $projectDir "src\main\kotlin"
    if (Test-Path -LiteralPath $kotlinRoot) {
        if (-not (Test-Path -LiteralPath $KotlinCompiler)) {
            throw "$name 有 src\main\kotlin\ 但找不到 Kotlin 編譯器（$KotlinCompiler）。"
        }
        $ktSources = @(Get-ChildItem -Recurse -Filter *.kt $kotlinRoot | ForEach-Object { $_.FullName })
        if ($ktSources.Count -gt 0) {
            $stdlib = Get-ChildItem -Path (Join-Path $Server "libraries\org\jetbrains\kotlin\kotlin-stdlib") `
                -Recurse -Filter "kotlin-stdlib-*.jar" -File -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | Select-Object -First 1
            if ($null -eq $stdlib) { throw "libraries\ 裡找不到 kotlin-stdlib，無法編譯 $name 的 Kotlin 程式碼。" }
            Write-Host "   Kotlin ($($ktSources.Count) files, jvm-target $release) <- $($stdlib.Name)" -ForegroundColor DarkCyan
            $ktLog = Join-Path $projectDir "build\kotlinc.log"
            $ktCp = "$cp;$classes;$($stdlib.FullName)"
            $ktArgs = @("-nowarn", "-no-stdlib", "-jvm-target", $release,
                        "-classpath", "`"$ktCp`"", "-d", "`"$classes`"") +
                      ($ktSources | ForEach-Object { "`"$_`"" })
            cmd.exe /c "chcp 65001 >nul && `"$KotlinCompiler`" $($ktArgs -join ' ') > `"$ktLog`" 2>&1"
            $ktExit = $LASTEXITCODE
            $ktDiag = @(Get-Content -LiteralPath $ktLog -Encoding UTF8 -ErrorAction SilentlyContinue |
                Where-Object { $_ -notmatch '^WARNING: ' -and $_ -notmatch 'sun\.misc\.Unsafe' })
            if ($ktDiag.Count -gt 0) { $ktDiag | ForEach-Object { Write-Host "   $_" } }
            if ($ktExit -ne 0) { throw "$name Kotlin 編譯失敗" }
        }
    }

    # ── NMS 介接層：一版一編 ──────────────────────────────────────────────
    # 版本無關層先編好，adapter 再以「它 + 自己那版核心」為 classpath。任何伺服器
    # 內部型別若從 NmsBridge 洩漏出去，這裡就會編不過——版本隔離是編譯器證明的。
    foreach ($adapter in $adapters) {
        $family = $adapter.Family
        $adapterDir = Join-Path $adapterRoot "v$family"
        if (-not (Test-Path -LiteralPath $adapterDir)) {
            throw "$name 宣告了 adapter $family，但找不到 $adapterDir"
        }
        $adapterSources = @(Get-ChildItem -Recurse -Filter *.java $adapterDir | ForEach-Object { $_.FullName })

        # 一個 family 可以列多個伺服器核心。第一個編進正式輸出;其餘只是閘門,
        # 編到丟棄目錄。目的不是產生多份 class,而是證明「同一份 adapter 對這些
        # 核心都成立」—— 有一邊搬動了簽章就在這裡當場失敗。
        $serverJars = @()
        if ($adapter.ServerJars) { $serverJars = @($adapter.ServerJars) }
        elseif ($adapter.ServerJar) { $serverJars = @($adapter.ServerJar) }
        else { throw "$name 的 adapter $family 沒有指定 ServerJar/ServerJars" }

        for ($i = 0; $i -lt $serverJars.Count; $i++) {
            $adapterJar = Join-Path $Server $serverJars[$i]
            $isPrimary = ($i -eq 0)
            $outDir = if ($isPrimary) { $classes } else { Join-Path $projectDir "build\nms-verify\$family-$i" }
            if (-not $isPrimary) {
                if (Test-Path -LiteralPath $outDir) { Remove-Item -LiteralPath $outDir -Recurse -Force }
                New-Item -ItemType Directory -Force $outDir | Out-Null
            }
            $adapterCp = (Get-NmsClasspath $adapterJar) + ";" + $classes
            $role = if ($isPrimary) { "輸出" } else { "閘門" }
            Write-Host "   NMS adapter $family ($($adapterSources.Count) files, $role) <- $(Split-Path -Leaf $adapterJar)" -ForegroundColor DarkCyan
            $adapterArgFile = Join-Path $projectDir "build\javac-nms-$family-$i.args"
            $adapterLog = Join-Path $projectDir "build\javac-nms-$family-$i.log"
            Write-ArgFile $adapterArgFile (@(
                '-encoding', 'UTF-8', '-nowarn', '--release', $release,
                '-cp', "`"$adapterCp`"", '-d', "`"$outDir`""
            ) + ($adapterSources | ForEach-Object { "`"$_`"" }))
            cmd.exe /c "chcp 65001 >nul && `"$Javac`" @`"$adapterArgFile`" > `"$adapterLog`" 2>&1"
            $adapterExit = $LASTEXITCODE
            $adapterDiag = @(Get-Content -LiteralPath $adapterLog -Encoding UTF8 -ErrorAction SilentlyContinue)
            if ($adapterDiag.Count -gt 0) { $adapterDiag | ForEach-Object { Write-Host "   $_" } }
            if ($adapterExit -ne 0) {
                throw "$name NMS adapter $family 對 $(Split-Path -Leaf $adapterJar) 編譯失敗"
            }
        }
    }

    # ── 檔名與 plugin.yml 版本一致閘門 ────────────────────────────────────
    # 這兩個曾經漂移過,而且是無聲的:jar 檔名說 1.5.0、plugin.yml 說 1.4.1,
    # /plugins 顯示一個版本、管理員手上的檔案叫另一個版本,對帳時沒有一方是可信的。
    # 兩者都是人手維護、又沒有任何東西比對過,所以漂移是遲早的事而不是意外。
    #
    # 這裡不自動改任何一邊 —— 版本號是宣告,不是推導出來的。閘門只要求兩邊講同一件事,
    # 升版時改哪一邊都行,忘了改另一邊就當場建置失敗。
    $ymlPath = Join-Path $projectDir "src/main/resources/plugin.yml"
    if (Test-Path -LiteralPath $ymlPath) {
        $declared = (Select-String -LiteralPath $ymlPath -Pattern "^version:\s*(.+)$" |
            Select-Object -First 1)
        if ($null -ne $declared) {
            $declaredVersion = $declared.Matches[0].Groups[1].Value.Trim().Trim("'").Trim('"')
            $expectedJar = "$name-$declaredVersion"
            if ($project.Jar -ne $expectedJar) {
                throw ("$name 的 jar 檔名與 plugin.yml 版本不一致:" +
                       "build-all.ps1 寫 `"$($project.Jar)`"、plugin.yml 寫 `"$declaredVersion`"(應為 `"$expectedJar`")。" +
                       "升版時兩邊都要改。")
            }
        }
    }

    $outJar = Join-Path $libs ($project.Jar + ".jar")
    & $Jar --create --file $outJar --date=$JarEntryDate -C $classes . -C (Join-Path $projectDir "src\main\resources") .
    if ($LASTEXITCODE -ne 0) { throw "$name packaging failed" }
    Write-Host "   -> $outJar" -ForegroundColor Green
}

Write-Host "`nAll plugins built." -ForegroundColor Green
