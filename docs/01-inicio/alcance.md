# Alcance

# Alcance del Proyecto CASEGen

## 1. Alcance general

El proyecto desarrollará una herramienta CASE
colaborativa orientada principalmente al modelado
estructural UML y a la generación de backend
Spring Boot.

## 2. Alcance funcional del primer parcial

CASEGen deberá permitir:

### Gestión de proyectos
- Crear proyectos.
- Consultar proyectos.
- Modificar datos básicos del proyecto.

### Modelado UML
Soporte inicial para un subconjunto UML 2.5.1:

- Package
- Class
- Interface
- Property
- Operation
- Parameter
- Association
- Association End
- Generalization
- Realization
- Multiplicity
- Visibility
- Primitive Types

### Edición gráfica
- Crear elementos.
- Modificar elementos.
- Eliminar elementos.
- Crear relaciones.
- Mover elementos.
- Guardar distribución gráfica.

### Interoperabilidad
- Importar XMI 2.5.1.
- Exportar XMI 2.5.1.
- Realizar pruebas de compatibilidad con Enterprise Architect.

### Colaboración
- Dos o más navegadores podrán observar cambios sobre
  un mismo modelo.
- Sincronización de modificaciones básicas.
- Presencia de usuarios si el tiempo del parcial lo permite.

### Validación
Antes de generar se validará:

- referencias existentes;
- nombres obligatorios;
- multiplicidades;
- relaciones;
- herencia;
- ausencia de ciclos inválidos;
- operaciones y parámetros compatibles.

### Versionado
- Crear una versión inmutable de un modelo validado.
- La generación se realizará desde una versión,
  no directamente desde el modelo editable.

### Generación
Generar una base de proyecto Spring Boot incluyendo
como mínimo:

- entidades/clases;
- relaciones;
- interfaces;
- repositories;
- services;
- controllers;
- DTOs;
- estructura Maven;
- configuración inicial.

### Aplicación móvil
La aplicación móvil permitirá como mínimo:

- listar proyectos;
- abrir un proyecto;
- consultar elementos;
- ejecutar comandos básicos sobre el modelo;
- posteriormente incorporar texto/voz.

## 3. Fuera del alcance inicial

No se pretende implementar la totalidad del metamodelo
UML 2.5.1.

No se pretende implementar en el primer incremento:

- todos los diagramas UML existentes;
- generación automática de algoritmos complejos;
- lógica empresarial completa inferida únicamente
  desde diagramas;
- despliegue empresarial de alta disponibilidad;
- sustitución completa de Enterprise Architect.

Los diagramas de comportamiento y otros tipos UML
podrán incorporarse progresivamente después de
estabilizar el núcleo estructural.