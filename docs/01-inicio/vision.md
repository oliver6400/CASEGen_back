# Visión

# Visión del Proyecto CASEGen

## 1. Introducción

CASEGen es una herramienta CASE orientada al modelado
UML y a la generación de estructuras backend.

El sistema permitirá construir modelos UML mediante
una aplicación web colaborativa, validarlos,
versionarlos e iniciar la generación de un backend
Spring Boot a partir de un modelo estable.

También dispondrá de una aplicación móvil que actuará
como asistente para consultar y modificar el modelo
mediante comandos estructurados, texto y posteriormente
voz.

## 2. Problema

Las herramientas de modelado tradicionales permiten
crear diagramas UML, pero normalmente existe una
separación importante entre el modelo diseñado y el
código que finalmente desarrolla el programador.

Además, la edición colaborativa, asistencia mediante
IA, uso móvil y generación controlada de backend no
siempre forman parte de un mismo flujo de trabajo.

CASEGen busca reducir esta separación mediante un
modelo UML semántico que pueda utilizarse como fuente
para validación, colaboración, interoperabilidad y
generación.

## 3. Visión del producto

CASEGen busca proporcionar una plataforma donde:

- varios usuarios puedan trabajar sobre un mismo modelo;
- el modelo respete un subconjunto verificable de UML 2.5.1;
- se pueda importar y exportar mediante XMI;
- el usuario pueda validar el modelo antes de generar;
- una versión estable del modelo pueda producir un backend Spring Boot;
- una aplicación móvil pueda actuar como asistente del modelo.

## 4. Usuarios

### Modelador
Crea y modifica elementos UML.

### Desarrollador
Utiliza el modelo para generar una base de backend.

### Colaborador
Participa simultáneamente en un modelo compartido.

### Usuario móvil
Consulta o modifica el modelo mediante el asistente móvil.

## 5. Objetivo general

Desarrollar una herramienta CASE colaborativa capaz
de manejar un subconjunto UML 2.5.1, interoperar
mediante XMI y generar una estructura backend
Spring Boot a partir de modelos previamente validados
y versionados.

## 6. Objetivos específicos

- Construir un modelo UML semántico independiente de su representación gráfica.
- Permitir modelado visual mediante navegador.
- Incorporar colaboración entre usuarios.
- Importar y exportar modelos UML mediante XMI 2.5.1.
- Validar coherencia del modelo.
- Crear versiones inmutables.
- Generar un backend Spring Boot.
- Incorporar un asistente móvil.
- Preparar integración posterior con servicios de IA.

## 7. Criterios de éxito

El proyecto se considerará funcional cuando sea posible:

1. crear un proyecto;
2. construir un modelo UML;
3. guardar y recuperar dicho modelo;
4. importar un XMI compatible;
5. exportar nuevamente el modelo;
6. validar el modelo;
7. crear una versión estable;
8. generar un backend Spring Boot;
9. visualizar cambios colaborativos entre dos clientes;
10. realizar al menos operaciones básicas desde el móvil.