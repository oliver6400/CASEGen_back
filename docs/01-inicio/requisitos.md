# Requisitos

# Requisitos de CASEGen

## 1. Requisitos funcionales

### RF-001 Crear proyecto
El sistema deberá permitir crear un proyecto CASE.

### RF-002 Gestionar modelo UML
El sistema deberá permitir crear y modificar un
modelo UML asociado a un proyecto.

### RF-003 Crear clases
El usuario deberá poder crear elementos UML Class.

### RF-004 Crear interfaces
El usuario deberá poder crear elementos UML Interface.

### RF-005 Gestionar propiedades
El usuario deberá poder agregar, modificar y eliminar
propiedades de un clasificador.

### RF-006 Gestionar operaciones
El usuario deberá poder agregar operaciones y
parámetros.

### RF-007 Crear asociaciones
El usuario deberá poder crear asociaciones entre
clasificadores.

### RF-008 Configurar multiplicidades
El usuario deberá poder configurar lower y upper
en los extremos de una asociación.

### RF-009 Configurar navegabilidad
El usuario deberá poder especificar extremos
navegables.

### RF-010 Generalización
El usuario deberá poder crear relaciones de
generalización.

### RF-011 Realización
El usuario deberá poder relacionar una clase con
una interfaz mediante Realization.

### RF-012 Importar XMI
El sistema deberá poder importar un archivo
XMI 2.5.1 compatible con el subconjunto soportado.

### RF-013 Exportar XMI
El sistema deberá generar XMI compatible con
el subconjunto UML implementado.

### RF-014 Validar modelo
El sistema deberá validar el modelo antes de crear
una versión para generación.

### RF-015 Versionar modelo
El usuario deberá poder crear una versión inmutable
de un modelo válido.

### RF-016 Generar backend
CASEGen deberá generar un proyecto Spring Boot a
partir de una versión válida.

### RF-017 Colaboración
Dos clientes web deberán poder trabajar sobre el
mismo modelo y recibir modificaciones.

### RF-018 Asistente móvil
La aplicación Flutter deberá permitir ejecutar
operaciones básicas sobre el modelo.

### RF-019 Asistente inteligente
El sistema deberá admitir comandos estructurados
provenientes de un servicio de asistencia/IA.

## 2. Requisitos no funcionales

### RNF-001 Estándar UML
El modelo soportado deberá basarse en UML 2.5.1.

### RNF-002 Interoperabilidad
La interoperabilidad deberá utilizar XMI como formato
principal de intercambio.

### RNF-003 Persistencia
La información persistente deberá utilizar PostgreSQL.

### RNF-004 Backend
El núcleo de CASEGen utilizará Spring Boot.

### RNF-005 Generación
El backend generado deberá utilizar Spring Boot.

### RNF-006 Seguridad
Las credenciales y secretos no deberán almacenarse
directamente en el repositorio Git.

### RNF-007 Mantenibilidad
La solución deberá separar modelo semántico,
representación gráfica, interoperabilidad y generación.

### RNF-008 Trazabilidad
Los cambios y decisiones importantes deberán quedar
registrados en Git y en la bitácora del proyecto.

### RNF-009 Codificación
Los componentes internos y archivos generados por
CASEGen utilizarán UTF-8.

### RNF-010 Pruebas
El importador XMI deberá contar con pruebas automatizadas
utilizando fixtures de referencia.