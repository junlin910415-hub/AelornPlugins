此目錄由 tools\decompile-live-plugin.ps1 從實機 JAR 反編譯產生,**不是**手寫原始碼。

來源 JAR   : MythicCore-2.1.1-AELORN-folia-26.2.jar
SHA-256    : 90878D64BABFB8CB0BA070DCC3AF78BFF7A2670C949EDC2CEECC63990B494ACE
反編譯時間 : 2026-08-10 12:40:33
Java 檔    : 22
資源檔     : 3

注意:
- 反編譯產物缺少註解、泛型資訊與區域變數名稱,直接重新編譯未必位元相同。
- 修改前先確認上游是否其實有真原始碼(見 plugin-sources\SOURCES.md)。
- 重新產生:powershell -ExecutionPolicy Bypass -File tools\decompile-live-plugin.ps1 -JarPattern "MythicCore-*.jar" -Project MythicCore
