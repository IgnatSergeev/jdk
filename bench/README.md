Папка `cached/res_stdout` содержит сырые замеры производительности:
- папка `latest` - результаты замеров наборов renaissance версии [OpenJDK с изменениями](https://github.com/IgnatSergeev/jdk/pull/1/commits/534532a9c193aafa22d5e6e36789cbb4be04aaaa)
- папка `upstream` - результаты замеров наборов renaissance версии [OpenJDK ветки master 01.04](https://github.com/openjdk/jdk/commit/53824cf2a97adbc25d32bec0acaff24d105081f9)

`summary.json` содержит сводку в виде относительного прироста медианы, 95-го и 99-го процентиля времени выполнения каждого из наборов в версии с отдельными профилями от ветки master
