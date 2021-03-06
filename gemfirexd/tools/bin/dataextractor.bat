@setlocal EnableDelayedExpansion
@setlocal enableextensions
@set scriptdir=%~dp0
@set gfxd=%scriptdir:\bin\=%
@if exist "%gfxd%\lib\gemfirexd-__VERSION__.jar" @goto gfxdok
@echo Could not determine GemFire XD location
@verify other 2>nul
@goto done
:gfxdok

@set GFXD_JARS=%gfxd%\lib\gemfirexd-__VERSION__.jar;%gfxd%\lib\gemfirexd-tools-__VERSION__.jar;%gfxd%\lib\gemfirexd-client-__VERSION__.jar;%gfxd%\lib\pulse-dependencies.jar

@if defined CLASSPATH set GFXD_JARS=%GFXD_JARS%;%CLASSPATH%

@rem add all jars in ext-lib if available, so admin can drop external jars in there
@for %%J IN (%gfxd%\ext-lib\*.jar) do @set GFXD_JARS=!GFXD_JARS!;%%J

@if not defined GFXD_JAVA (
@REM %GFXD_JAVA% is not defined, assume it is on the PATH
@set GFXD_JAVA=java
)
@set GEMFIREXD=%gfxd%

@"%GFXD_JAVA%" %JAVA_ARGS% -classpath "%GFXD_JARS%" com.pivotal.gemfirexd.tools.dataextractor.GemFireXDDataExtractor %*
:done
@set scriptdir=
@set gfxd=
@set GFXD_JARS=
