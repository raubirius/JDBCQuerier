@echo off
rem if not DEFINED IS_MINIMIZED set IS_MINIMIZED=1 && start "" /min "%~dpnx0" %* && exit
chcp 65001>nul
cd %userprofile%\Documents\JDBCQuerier
::java -jar JDBCQuerier.jar
::if errorlevel 1 (
	echo Jar error level: %errorlevel%
	java -Dfile.encoding=UTF-8 -cp jtds-1.3.1-dist\jtds-1.3.1.jar;c:\_jEdit\GRobot.jar;.\ JDBCQuerier %*
	if errorlevel 1 echo Class error level: %errorlevel%
::)
::pause>nul
exit