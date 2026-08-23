此目錄由 tools\decompile-live-plugin.ps1 從實機 JAR 反編譯產生,**不是**手寫原始碼。

來源 JAR   : AelornItems-3.2.2-AELORN-NEXO-folia-26.2.jar
SHA-256    : 2CDF1A8DD25A687718A029969336D89458A4ED2EF9361D8B3BA4C1F58D33C2E4
反編譯時間 : 2026-08-10 12:40:31
Java 檔    : 38
資源檔     : 35

注意:
- 反編譯產物缺少註解、泛型資訊與區域變數名稱,直接重新編譯未必位元相同。
- 修改前先確認上游是否其實有真原始碼(見 plugin-sources\SOURCES.md)。
- 重新產生:powershell -ExecutionPolicy Bypass -File tools\decompile-live-plugin.ps1 -JarPattern "AelornItems-*.jar" -Project AelornItems
