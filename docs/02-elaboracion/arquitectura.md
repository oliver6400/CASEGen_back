# Arquitectura

# Arquitectura de CASEGen

## 1. Principios arquitectónicos

CASEGen separará:

1. modelo semántico UML;
2. representación gráfica;
3. colaboración;
4. persistencia;
5. interoperabilidad;
6. versionado;
7. generación;
8. asistencia inteligente.

## 2. Arquitectura general

Web Client
        |
        | REST / WebSocket
        v
Spring Boot CASEGen Core
        |
        +---- PostgreSQL
        |
        +---- XMI Import/Export
        |
        +---- Model Validator
        |
        +---- Versioning
        |
        +---- Spring Generator

Web Editor
        |
        +---- GLSP / alternativa React Flow
        |
        +---- Yjs collaboration

Flutter Mobile
        |
        +---- CASEGen API

AI Service
        |
        +---- Python / FastAPI
        |
        +---- structured commands

## 3. Backend principal

Spring Boot será responsable de:

- gestión de proyectos;
- modelo semántico;
- persistencia;
- validación;
- XMI;
- versiones;
- generación.

## 4. Servicio IA

La IA se mantendrá desacoplada del núcleo.

Tecnología prevista:

Python + FastAPI.

La IA no modificará directamente PostgreSQL.

Transformará lenguaje natural en comandos
estructurados que serán validados por CASEGen.

## 5. Cliente web

El editor web será responsable de:

- canvas;
- visualización;
- selección;
- movimiento;
- edición;
- conexiones.

Se evaluará GLSP como primera opción debido a
la experiencia obtenida del repositorio UML-GLSP.

React Flow se mantendrá como alternativa de menor
riesgo si la adaptación de GLSP resulta demasiado
costosa para el tiempo disponible.

## 6. Colaboración

Yjs será utilizado como componente de sincronización
colaborativa.

Yjs no será la base de datos principal.

PostgreSQL continuará siendo la persistencia
autoritativa de modelos versionados.

## 7. Cliente móvil

Flutter será utilizado debido a experiencia previa
con la tecnología y al tiempo limitado del parcial.

El móvil funcionará principalmente como asistente
del modelo y no como diagramador completo en la
primera versión.

## 8. Interoperabilidad

XMI 2.5.1 será el formato principal.

Enterprise Architect se utilizará como herramienta
externa de referencia y validación.

## 9. Separación Semantic Model / Diagram Model

El modelo semántico será la fuente de verdad para:

- validación;
- XMI;
- versionado;
- generación.

El modelo de diagramas contendrá únicamente:

- posiciones;
- dimensiones;
- conexiones visuales;
- información de presentación.

## 10. Backend CASEGen y backend generado

El Backend CASEGen es el monolito modular que administra proyectos, usuarios,
modelos UML, validación, versiones, colaboración y ejecuciones de generación.
Su código y su ciclo de despliegue pertenecen al producto CASEGen.

Un Generated Backend es un producto independiente derivado de una versión
inmutable de un modelo. No forma parte de los módulos ejecutables de CASEGen, no
comparte su base de datos y no se escribe dentro de su árbol de fuentes.

Durante una ejecución, el backend generado existe como un directorio normal en
un workspace temporal. Solo después de compilar, probar y verificar ese
directorio se empaqueta como un ZIP descargable. El ZIP no se versiona en el
repositorio de CASEGen.

## 11. Semantic Core y canales de interacción

Todos los canales modifican el modelo mediante los mismos comandos de
aplicación y reglas del Semantic Core:

```text
Web / GLSP o React Flow / Voice / Flutter / AI
                    |
                    v
        comandos semánticos validados
                    |
                    v
              Semantic Core
                    |
                    +--> Diagram Model
                    +--> Validation
                    +--> ModelVersion
                    +--> Generation Pipeline
```

GLSP o React Flow traduce interacciones gráficas; Yjs sincroniza colaboración;
Flutter, voz y chat presentan otros canales; la IA propone comandos
estructurados. Ninguno de ellos modifica tablas directamente, define una
semántica UML paralela ni genera el código Java final.

## 12. Generation Pipeline

El flujo oficial es:

```text
UML Semantic Model
  -> Validation
  -> ModelVersion
  -> LogicalModel
  -> SpringGenerationModel
  -> Templates
  -> Compile + Tests + Verification
  -> ZIP
```

La entrada es siempre una `ModelVersion` inmutable y validada. `LogicalModel`
proyecta la estructura relacional; `SpringGenerationModel` resuelve las
decisiones concretas de Java, Spring y API; las plantillas se limitan a emitir
el modelo resuelto. Para una misma versión, perfil, mappings, reglas y versión
del generador, el contenido generado debe ser equivalente.

Los conceptos tecnológicos se mantienen fuera del metamodelo UML.
`GenerationProfile` y `GenerationElementMapping` contienen decisiones como
nombres físicos, exposición API y proyecciones a persistencia.

El contrato detallado se define en
`generation/spring-backend-contract.md`.

## 13. Ejecución asíncrona y aislamiento

Cada solicitud crea un `GenerationRun` con un `generationId` UUID. Un worker
procesa la ejecución de forma asíncrona en un workspace temporal exclusivo. El
workspace, procesos de compilación, entorno PostgreSQL de verificación y logs
de una ejecución no se comparten con otras ejecuciones.

El aislamiento por `generationId` permite atender múltiples usuarios sin que
una generación sobrescriba, inspeccione o descargue artefactos de otra. Las
operaciones de inicio, consulta, cancelación y descarga deben aplicar permisos
del proyecto y límites de recursos.

## 14. ArtifactStorage y descarga

`ArtifactStorage` abstrae el almacenamiento de artefactos y evita acoplar el
pipeline al disco local o a un proveedor de objetos. PostgreSQL conserva
metadatos, estado, checksum, propiedad y retención; el almacenamiento de
artefactos conserva los bytes del ZIP.

El cliente recibe primero el identificador de una ejecución aceptada. Consulta
o recibe cambios de estado y solo obtiene una descarga cuando la ejecución ha
terminado correctamente. Un resultado que solo escribió archivos, pero no
superó compilación, pruebas y verificación, no se publica.

## 15. Separación de bases de datos

PostgreSQL de CASEGen persiste usuarios, proyectos, modelos semánticos,
diagramas, versiones, colaboración y metadatos de generación.

Cada Generated Backend declara y administra su propia base PostgreSQL mediante
sus propias migraciones Flyway. No conoce el esquema ni las credenciales de
CASEGen. En ambos productos Flyway es la fuente ejecutable del esquema y
Hibernate utiliza `ddl-auto=validate`.
