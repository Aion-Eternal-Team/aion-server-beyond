@ECHO off
TITLE Aion Emu - Game Server Console

:START
CLS
JAVA -Xms512m -Xmx2560m -XX:+TieredCompilation -XX:+UseNUMA -server -ea -javaagent:libs/{javaagentlib} -cp "libs/*" com.aionemu.gameserver.GameServer
IF ERRORLEVEL 2 GOTO START
IF ERRORLEVEL 1 GOTO ERROR
IF ERRORLEVEL 0 GOTO END

:ERROR
ECHO.
ECHO Game Server has terminated abnormally!
ECHO.
PAUSE >nul
EXIT

:END
ECHO.
ECHO Game Server has shut down
ECHO.
PAUSE >nul
EXIT