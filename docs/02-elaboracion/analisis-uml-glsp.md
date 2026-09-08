# Análisis técnico de reutilización: `uml-glsp`

Fecha: 2026-09-06  
Alcance: análisis de solo lectura del repositorio hermano `../uml-glsp`. No se copió código, no se instalaron dependencias y no se modificó ese repositorio.

## Resumen ejecutivo

`uml-glsp` es una demostración de editor UML de clases, basada en Eclipse GLSP, EMF.cloud Model Server, EMF/UML2 y Sprotty, integrada en Eclipse Theia. Su idea arquitectónica más valiosa para CASEGen es separar rigurosamente el modelo semántico del modelo de notación: el archivo `.uml` mantiene los elementos UML y el archivo adyacente `.unotation` mantiene la representación gráfica.

El soporte implementado es limitado a **Class, Property y Association**, con edición parcial de nombres, tipos, multiplicidades, posiciones y rutas. El repositorio no implementa interfaces, generalizaciones, realizaciones, operaciones/parámetros ni diagramas de secuencia, aunque su metamodelo de notación enumera `SEQUENCE` como una posibilidad futura.

CASEGen tiene objetivos más amplios y una arquitectura incompatible de forma directa: Spring Boot 4.1.1/Java 21/PostgreSQL/React/Flutter, persistencia por agregados, colaboración futura con Yjs, versionado y generación determinista. Por ello, el valor de `uml-glsp` es principalmente de **patrón y referencia**, no de código base.

## 1. Arquitectura general del repositorio

```text
Eclipse Theia browser app
  └─ extensión uml-theia-integration
       └─ GLSP Client + Sprotty (TypeScript)
            ⇅ protocolo GLSP por socket
          GLSP Server (Java / OSGi)
            ⇅ cliente EMF.cloud Model Server
          Model Server (Java / OSGi)
            ⇅ archivos del workspace
              modelo semántico .uml + notación .unotation
```

Consta de tres piezas ejecutables:

- **Model Server:** carga, modifica, guarda y notifica recursos EMF/UML2.
- **GLSP Server:** traduce operaciones gráficas a comandos contra el Model Server y reconstruye el grafo GLSP.
- **GLSP Client:** renderiza el grafo recibido, permite edición y se integra en Theia.

No es un backend HTTP Spring Boot ni persiste en PostgreSQL. El estado canónico reside en archivos UML/XMI del workspace.

## 2. Estructura de `client/`

`client/` es un monorepo Yarn 1 + Lerna 2 con Node mínimo 12.14.1:

- `browser-app/`: aplicación Theia y scripts de arranque; contiene dependencias de explorador, editor, workspace, terminal y plugins de Theia.
- `packages/uml-sprotty/`: cliente de diagrama reutilizable dentro del ecosistema GLSP/Sprotty. Declara tipos, elementos de modelo, vistas SVG, CSS, paleta y edición de etiquetas.
- `packages/uml-theia-integration/`: adaptación a Theia: gestor de diagramas, ciclo de vida, conexión al servidor Java y cliente del Model Server.
- `packages/common/`: lenguaje UML, iconos y estilos compartidos.
- `workspace/`: ejemplos de archivos `.uml` y `.unotation`.

La capa que más se aproxima a un cliente desacoplable es `uml-sprotty`; no obstante, sigue usando `@eclipse-glsp/client`, Sprotty e Inversify. El adaptador `uml-theia-integration` es específico de Theia y no debe trasladarse a CASEGen.

## 3. Estructura de `server/`

`server/` es un build Maven/Tycho con plugins Eclipse/OSGi:

- `com.eclipsesource.uml.modelserver`: Model Server, codec UML/XMI, recursos, rutas REST y comandos semánticos/de notación.
- `com.eclipsesource.uml.glsp`: GLSP Server, fábricas de grafo, paleta, operaciones y acceso al Model Server.
- `*-app`: aplicaciones y productos Eclipse para empaquetar/ejecutar ambos servidores.
- `targetplatform`: plataforma Eclipse objetivo.

El repositorio compila para Java 11 y usa artefactos OSGi (`Require-Bundle`, Tycho, target platform). Esta forma de empaquetado no encaja directamente con el Maven convencional de CASEGen.

## 4. Model Server

El Model Server es la fuente de verdad para recursos de archivo. `UmlModelResourceManager`:

1. descubre archivos `.uml` y crea un `ResourceSet` por modelo;
2. registra `UMLResource.Factory`, inicializa bibliotecas UML2 y carga tipos primitivos;
3. usa el mismo `ResourceSet` para el `.unotation` hermano;
4. guarda todos los recursos de archivo del conjunto, no las bibliotecas;
5. ofrece creación de un modelo/diagrama nuevo y el endpoint de tipos UML.

El `UmlCodec` serializa/deserializa recursos UML como XMI. Esto es una base válida para interoperabilidad UML, pero no prueba compatibilidad completa con XMI de Enterprise Architect: los perfiles, extensiones y subconjuntos de EA requieren pruebas y adaptadores propios.

## 5. GLSP Server

`UmlGLSPModule` registra la configuración del diagrama, la paleta, las fábricas de GModel y los handlers para crear, editar etiquetas, borrar, cambiar bounds, cambiar rutas y agrupar operaciones.

`UmlModelSourceLoader` obtiene el `sourceUri`, carga el `.uml` y el `.unotation` desde el Model Server y se suscribe a cambios. Al cambiar el modelo, GLSP reconstruye/actualiza el grafo de presentación. Es una arquitectura reactiva de modelo a diagrama, pero no un mecanismo de colaboración multiusuario con resolución de conflictos.

## 6. GLSP Client y uso de Sprotty

El cliente mapea tipos de protocolo a elementos Sprotty y vistas SVG:

- `node:class` → `LabeledNode` + `ClassNodeView`.
- `comp:property` → compartimento Sprotty.
- `edge:association` → `SEdge` + `PolylineEdgeView`.
- etiquetas de nombre, tipo y multiplicidad → `SEditableLabel`.

Sprotty aporta selección, hover, bounds, layout de hijos, edición de etiquetas y render SVG; GLSP agrega operaciones y comunicación cliente-servidor. La vista de clase dibuja rectángulo, cabecera y una línea separadora si hay propiedades. El editor usa layout manual en servidor y layout de hijos VBOX/HBOX en cliente.

## 7. EMF/UML2 y representación UML

El modelo semántico es una instancia de `org.eclipse.uml2.uml.Model`:

- una `uml:Class` se agrega a `Model.packagedElements`;
- una `Property` se agrega a `Class.ownedAttributes` con tipo por defecto `String`;
- una `Association` se agrega a `Model.packagedElements` con dos `ownedEnd`;
- nombres, tipo de propiedad y multiplicidad se modifican mediante comandos EMF.

Los identificadores base son fragmentos URI/XMI del `EObject` (`EcoreUtil.getURI(...).fragment()`), normalmente el `xmi:id` persistido. La indexación enlaza `id gráfico ↔ EObject semántico ↔ elemento de notación`.

### Qué cubre y qué no cubre

- Sí: Class, Property, Association, nombres de extremos y multiplicidad textual.
- Parcial: el modelo UML2 puede expresar más semántica, pero las fábricas y handlers no exponen Interface, Operation, Parameter, Generalization, Realization ni navegabilidad.
- No: Interaction, Lifeline, OccurrenceSpecification, Message ni un editor de secuencia.

## 8. Modelo de notación y relación `.uml` / `.unotation`

El Ecore `unotation.ecore` define:

- `Diagram`: tipo de representación y lista de elementos.
- `NotationElement`: `SemanticProxy` y tipo gráfico.
- `Shape`: posición `(x,y)` y tamaño `(width,height)`.
- `Edge`: `bendPoints`, source y target opcionales.
- `SemanticProxy`: URI/fragmento hacia el elemento UML; su referencia resuelta es transitoria.

Para un mismo nombre base, por ejemplo `course.uml`, el editor espera `course.unotation`. El primero contiene la semántica UML/XMI; el segundo referencia elementos mediante sus fragmentos XMI y conserva el layout. Al cargar, `UmlModelFactory` resuelve cada proxy contra el `Resource` UML; al guardar, el `ResourceSet` persiste ambos archivos.

Ejemplo observado: un `Shape` guarda posición/tamaño y referencia a una clase; un `Edge` referencia una asociación y guarda puntos de quiebre. Así, el editor no altera la semántica al mover una clase y no pierde su posición al recargar.

## 9. Operaciones implementadas

| Operación | Flujo en `uml-glsp` | Observación para CASEGen |
|---|---|---|
| Crear Class | Paleta → `CreateNodeOperation` → `CreateClassifierNodeOperationHandler` → comando compuesto `AddClassCommand` + `AddClassShapeCommand` | Patrón útil: mutación semántica y creación de notación en una operación atómica. |
| Crear Property | `CreateNodeOperation` dentro de la clase → `AddPropertyCommand` | Solo afecta semántica; se reconstruye el compartimento. |
| Crear Association | `CreateEdgeOperation` entre dos clases → `AddAssociationCommand` + `AddAssociationEdgeCommand` | Crea dos `ownedEnd`; el edge se representa a partir de ellos. |
| Renombrar | `ApplyLabelEditOperation` identifica el tipo/id de etiqueta y ejecuta `SetClassName`, `SetPropertyName` o cambios de extremo | La lógica depende de convenciones de sufijos de IDs. |
| Eliminar | `DeleteOperation` resuelve el `EObject` y ejecuta comandos de clase, propiedad o asociación; los compuestos retiran también notación cuando aplica | CASEGen deberá definir política explícita para relaciones huérfanas, auditoría y versionado. |
| Mover/redimensionar | `ChangeBoundsOperation` → `ChangeBoundsCommand` sobre `Shape` | Persiste posición y tamaño exclusivamente en notación. |
| Redirigir edge | `ChangeRoutingPointsOperation` → `ChangeRoutingPointsCommand` | Persiste bend points de asociación. |

## 10. Flujo completo UI → modelo → actualización

```text
UI Sprotty / paleta / edición de texto
  → GLSP Action u Operation (crear, editar, borrar, mover, enrutar)
  → handler del GLSP Server
  → UmlModelServerAccess
  → comando EMF o comando compuesto (semántica + notación)
  → Model Server modifica ResourceSet y guarda .uml/.unotation
  → suscripción del GLSP Server recibe cambio
  → GModelFactory reconstruye GGraph
  → GLSP envía actualización al cliente
  → Sprotty re-renderiza nodos, compartimentos y edges
```

Esto es un excelente patrón conceptual para CASEGen, cambiando el destino de los comandos: en lugar de comandos EMF sobre archivos, CASEGen debe aplicar comandos de aplicación al agregado `Project`/modelo persistido en PostgreSQL, validar y luego publicar eventos de actualización a React/Yjs.

## 11. Nodes, edges, posiciones, tamaños y compartments

### Identificación

El node o edge principal usa el fragmento URI/XMI del elemento semántico. Los hijos gráficos se derivan por sufijos: `_header`, `_header_label`, `_property`, `_property_label_name`, `_label_name` y otros. Esa derivación permite que la edición de una etiqueta encuentre su propietario semántico.

Para CASEGen, usar UUID estables del agregado y derivar identificadores de vista de forma documentada sería equivalente. No debe usar IDs de DOM ni posiciones como identidad.

### Posiciones y tamaños

- `Shape.position` / `Shape.size` se copian al `GNode` al construirlo.
- mover o redimensionar cambia el `Shape` correspondiente, no la clase UML.
- `Edge.bendPoints` se copian al `GEdge.routingPoints` y se persisten al modificar el trazado.
- la configuración declara `MANUAL`; no hay layout automático semánticamente relevante.

### Compartimentos de Class

`ClassifierNodeFactory` construye una clase con cabecera horizontal (icono + nombre) y un compartimento vertical de propiedades. Cada propiedad es una fila horizontal con icono, nombre, separador `:`, tipo y, si existe, multiplicidad entre corchetes. No existe compartimento de operaciones.

### Association edge

`RelationshipEdgeFactory` crea un `GEdge` Manhattan entre los tipos de los dos `memberEnd`. Coloca, en 10 % y 90 % de la ruta, etiquetas editables para nombre y multiplicidad de cada extremo. No dibuja ni persiste indicadores de navegabilidad y no trata generalización/realización.

## 12. Carga, guardado e interoperabilidad

- Carga: el cliente pasa una URI absoluta; el Model Server obtiene `.uml` y `.unotation` desde el workspace y resuelve referencias EMF.
- Guardado: `UmlModelResourceManager.save()` guarda los recursos de archivo del mismo `ResourceSet`.
- Creación: una ruta del Model Server crea ambos archivos y anota el tipo de diagrama.
- XMI: UML2 serializa `.uml` como XMI. El repositorio no contiene un importador/exportador específico de Enterprise Architect ni pruebas de round-trip con EA.

CASEGen ya validó fixtures UML/XMI de clase, asociaciones, generalización, realización, operaciones, modelo integrado y secuencia. Esos fixtures son un activo más alineado con CASEGen que el subconjunto de `uml-glsp`: deben convertirse en pruebas de los adaptadores `interoperability`, sin asumir que el editor de referencia los representa.

## 13. Dependencias y antigüedad

| Área | `uml-glsp` observado | Implicación |
|---|---|---|
| Java | Java 11 | CASEGen usa objetivo Java 21. |
| Build servidor | Maven + Tycho 1.6 + OSGi/Eclipse target platform | No es Spring Boot/Maven convencional. |
| GLSP/Sprotty | GLSP 0.10 prerelease, Sprotty 0.10 | API antigua respecto al ecosistema GLSP actual. |
| EMF.cloud Model Server | 0.7 | Cadena de integración antigua. |
| UML2 | 5.5.x | Debe evaluarse frente a XMI UML 2.5.1 y fixtures EA. |
| Cliente | Node >=12.14, TypeScript ~4.5, Yarn 1, Lerna 2 | Herramientas desactualizadas. |
| Theia | resoluciones 1.23.0 | Acoplamiento e incompatibilidades potenciales. |
| Logging | Log4j 1.2 aparece en el manifiesto GLSP | Dependencia especialmente desaconsejable. |

La propia estructura incluye workarounds temporales, por ejemplo un registro de handlers documentado como solución temporal para CreateOperations. Esto confirma que no es una base recomendable para copiar o modernizar incrementalmente.

## 14. Acoplamiento con Eclipse Theia y GLSP moderno sin Theia

El acoplamiento fuerte está en `browser-app` y `uml-theia-integration`: workspace/explorador de archivos, `GLSPDiagramManager`, `GLSPTheiaDiagramServer`, contribuciones frontend/backend, y arranque de JARs Java desde Theia. No son necesarios para el render puro de `uml-sprotty`, pero forman una gran parte de la aplicación actual.

Sí es posible usar GLSP moderno sin Theia. La documentación oficial indica que los editores GLSP se pueden integrar en cualquier aplicación web y que el cliente existe en modalidad *standalone*; Theia es una integración opcional. El cliente GLSP/Sprotty puede montarse desde un componente React y conectarse a un GLSP Server independiente mediante el protocolo GLSP. CASEGen tendría que desarrollar su propio puente para autenticación, selección de proyecto/diagrama, ciclo de vida WebSocket, guardado, errores y permisos. [GLSP: Getting Started](https://eclipse.dev/glsp/documentation/gettingstarted/) y [GLSP: integraciones](https://eclipse.dev/glsp/documentation/integrations/).

Para CASEGen se recomienda evaluar, en un spike separado, un GLSP cliente moderno standalone dentro de React. El servidor GLSP debería ser un adaptador separado sobre la API/dominio de CASEGen, no el dueño de la persistencia PostgreSQL. No se recomienda incrustar el antiguo Model Server OSGi en Spring Boot.

## 15. Patrones reutilizables conceptualmente

- Separación Semantic Model / Diagram Model y persistencia independiente del layout.
- Comandos explícitos de dominio: crear clase, crear atributo, crear relación, renombrar, eliminar, mover, redimensionar, enrutar.
- Operación compuesta atómica para combinar una mutación semántica y una de notación.
- Índice `semanticId ↔ diagramElement` para traducir eventos de UI a elementos de dominio.
- Fábricas deterministas que convierten el modelo semántico en un grafo de presentación.
- Contratos de tipos de node, edge, label y compartment compartidos entre cliente y servidor.
- Eventos posteriores a una mutación confirmada para refrescar otros clientes.

No son reutilizables directamente los comandos EMF, ResourceSet, `SemanticProxy` basado en URI de archivo, Theia integration, configuración Tycho/OSGi ni lógica basada en rutas del filesystem.

## 16. Riesgos técnicos para CASEGen

1. **Desalineación de plataforma:** trasladar OSGi/Tycho/EMF.cloud implicaría un segundo backend incompatible con el monolito Spring Boot.
2. **Alcance UML insuficiente:** el editor no cubre la mayor parte de los fixtures ya validados por CASEGen ni diagrama de secuencia.
3. **Interoperabilidad engañosa:** serializar UML2/XMI no garantiza compatibilidad con XMI de Enterprise Architect ni preservación de extensiones.
4. **Colaboración incompleta:** las suscripciones del Model Server actualizan cambios, pero no proporcionan CRDT, presencia, permisos, operaciones offline ni resolución de conflictos de Yjs.
5. **Persistencia incompatible:** archivos locales no cubren permisos, snapshots inmutables, auditoría, generación registrada ni PostgreSQL.
6. **Seguridad y operación:** el modelo actual presupone un workspace de archivos y servidores lanzados localmente desde Theia; CASEGen requiere autenticación, aislamiento por proyecto y despliegue web.
7. **Dependencias antiguas:** Node 12, Yarn/Lerna heredados, Java 11, GLSP 0.10 y Log4j 1.2 aumentan coste y riesgo de modernización.
8. **Licencia:** copiar archivos requiere conservar avisos y cumplir EPL-2.0; no mezclar código sin inventario de licencias de dependencias y revisión del alcance de distribución.

## 17. Matriz de comparación CASEGen vs `uml-glsp`

| Funcionalidad CASEGen | Soporte en `uml-glsp` | Reutilizable directamente | Reutilizable como patrón | Debe implementarse en CASEGen | Prioridad |
|---|---|---:|---:|---|---|
| Class | Sí: crear, mostrar, renombrar, borrar | No | Sí | Agregado/commands/UI React y mapeo propio | P0 |
| Interface | No | No | Sí, como classifier/node | Semántica, visual y XMI | P1 |
| Property | Sí: crear, nombre, tipo, multiplicidad, borrar | No | Sí | `ModelAttribute`, validación y UI | P0 |
| Operation | No | No | Sí, como compartment | Semántica, comandos y render | P1 |
| Parameter | No | No | Sí, como hijo de Operation | Semántica, comandos y XMI | P1 |
| Association | Sí: crear, extremos, borrar | No | Sí | `ModelRelationship`, reglas y UI | P0 |
| Multiplicity | Sí: etiquetas y edición textual | No | Sí | Estructura validada `lower/upper`, no solo texto | P0 |
| Navigability | No expuesta | No | Parcial | Propiedad por extremo, XMI y marca visual | P1 |
| Generalization | No | No | Sí, edge tipado | Semántica, validación y render | P1 |
| Realization | No | No | Sí, edge tipado | Semántica, validación y render | P1 |
| Class Diagram | Sí, único editor real | No | Sí | Diagrama/viewport/layout por proyecto | P0 |
| Interaction | No | No | Solo separación modelo/notación | Semántica UML y XMI | P2 |
| Lifeline | No | No | Sí, node con geometría propia | Semántica y render secuencia | P2 |
| Message | No | No | Sí, edge especializado | Semántica y render secuencia | P2 |
| Sequence Diagram | No; enum de notación sin editor | No | Sí | Fábrica, UI y comandos completos | P2 |
| Semantic Model | Sí: EMF/UML2 en `.uml` | No | Sí | Dominio CASEGen independiente de editor | P0 |
| Diagram Model | Sí: `.unotation` separado | No | Sí | Entidad/layout PostgreSQL y contratos UI | P0 |
| Import XMI | Carga UML2/XMI genérico | No | Sí | Adaptador EA para subconjunto validado | P1 |
| Export XMI | Serialización UML2/XMI | No | Sí | Adaptador EA, pruebas round-trip | P1 |
| Collaboration | Suscripción a cambios, sin CRDT | No | Parcial | Yjs, WebSocket, permisos y conflictos | P1 |
| Versioning | No | No | Sí, comandos auditables | snapshots inmutables `ModelVersion` | P0 |
| Voice/AI | No | No | Sí, comandos UI normalizados | intérprete → `ModelCommand` validado | P1 |
| Spring Boot generation | No | No | Sí, proyección determinista | `ModelVersion → GenerationModel → Templates → Source Code` | P0 |

## 18. Licencia y condiciones antes de copiar código

El repositorio está bajo Eclipse Public License 2.0; varios archivos incluyen encabezado `SPDX-License-Identifier: EPL-2.0 OR MIT`, pero la raíz declara EPL-2.0. Antes de copiar cualquier archivo se debe:

1. verificar la licencia exacta y avisos del archivo concreto;
2. conservar copyright, SPDX y textos requeridos;
3. revisar obligaciones de distribución de código fuente y compatibilidad con la licencia de CASEGen;
4. inventariar licencias transitivas (GLSP, Sprotty, EMF, Theia, etc.);
5. registrar una decisión ADR y revisión legal/académica si el producto se distribuye.

Usar ideas, arquitectura y comportamiento como referencia no requiere copiar su implementación. Si alguna vez se copia código, no debe hacerse sin esta revisión.

## Recomendación final

**C) Usarlo como referencia y comenzar desde GLSP moderno.**

La separación UML/notación, el flujo de comandos y las fábricas de presentación son patrones muy alineados con CASEGen. Sin embargo, la implementación concreta está anclada en Java 11, OSGi/Tycho, EMF.cloud 0.7, GLSP/Sprotty 0.10, Theia 1.23, Node 12/Yarn/Lerna y un editor de alcance reducido. Modernizar/forkear arrastraría una plataforma ajena, no resolvería por sí sola colaboración con Yjs, PostgreSQL, snapshots, autenticación, EA/XMI ni generación Spring Boot.

La siguiente evaluación —fuera del alcance de este análisis— debería ser un spike pequeño e independiente: GLSP standalone actual embebido en React, con un contrato de comandos que delegue al dominio Spring Boot de CASEGen. Si el coste de ese spike supera el valor del editor frente a React Flow, CASEGen puede conservar los patrones documentados y elegir React Flow; no debe adaptar `uml-glsp` actual.

## Fuentes de evidencia

- Repositorio local de referencia: `../uml-glsp/README.md`, `client/package.json`, `server/pom.xml`, manifiestos OSGi, `unotation.ecore`, fábricas y handlers enumerados en este informe.
- [Eclipse GLSP — repositorio y capacidades standalone](https://github.com/eclipse-glsp/glsp).
- [Eclipse GLSP — Getting Started](https://eclipse.dev/glsp/documentation/gettingstarted/).
- [Eclipse GLSP — Servers & Integrations](https://eclipse.dev/glsp/documentation/integrations/).
- [Eclipse GLSP Theia Integration — compatibilidades](https://github.com/eclipse-glsp/glsp-theia-integration).
