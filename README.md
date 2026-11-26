# Arquitectura C4

Portal Angular con servicios Java para consulta de contribuyentes, matrícula vehicular y puntos de licencia.

## Diagramas C4 en IcePanel

- [Nivel 1 · Contexto del sistema](https://app.icepanel.io/landscapes/y352icjztovz2NAdbN7m/versions/latest/diagrams/editor?diagram=RSvgnesaaZ&model=VJWShGeWIb)
- [Nivel 2 · Contenedores del portal](https://app.icepanel.io/landscapes/y352icjztovz2NAdbN7m/versions/latest/diagrams/editor?diagram=5g711LZrkg&model=XxN5S4e8Kr)

## Ejecutar desde IntelliJ IDEA

1. Instala JDK 21 o superior y Node.js 22 LTS con npm.
2. Abre la carpeta raíz del repositorio en IntelliJ e importa el `pom.xml` como proyecto Maven.
3. En el selector superior de IntelliJ, elige **Servicios Java** y pulsa **▶**. Se inician el SRI en el puerto 8081, la ANT en el 8082 y Angular en el 4200. En el primer arranque se instalan las dependencias de Angular si faltan.
4. Angular abre el navegador predeterminado al terminar de compilar. La interfaz queda disponible en [el portal local](http://127.0.0.1:4200/). Para cerrar el portal, detén la configuración desde IntelliJ.

## Caché y servicios externos

Para habilitar Redis, crea `servicio-ant/redis-local.properties` desde el directorio del servicio:

```properties
ant.redis.url=URL_REST_DE_UPSTASH
ant.redis.token=TOKEN_REST_DE_UPSTASH
```

Git ignora ese archivo. Reinicia `servicio-ant` para cargarlo. Sin las credenciales, el portal puede iniciar y la consulta a la ANT se realiza sin caché. La disponibilidad de los resultados reales depende del SRI y de la ANT.

El repositorio de GitHub es privado. Para que el docente pueda abrirlo, debe tener acceso a ese repositorio.
