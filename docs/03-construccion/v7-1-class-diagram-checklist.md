# V7.1 — Checklist manual de diagramas de clases

## Preparación

- [ ] Iniciar PostgreSQL y el backend con las variables locales de CASEGen.
- [ ] Iniciar `web` y autenticarse como `SUPERUSER`, `OWNER` o `COLLABORATOR`.
- [ ] Abrir un proyecto y un modelo UML con varias clases, una interfaz y relaciones V6.

## Diagrama activo

- [ ] Abrir el modelo y comprobar la redirección a `/projects/{projectId}/models/{modelId}/diagrams/{diagramId}`.
- [ ] Confirmar que el breadcrumb muestra Proyectos, el modelo y el diagrama activo.
- [ ] Confirmar que se muestran el nombre del modelo y el nombre del diagrama.
- [ ] Crear dos diagramas con `+ Diagrama` y verificar que aparecen como hermanos en el selector.
- [ ] Cambiar entre ambos y confirmar que la URL cambia de `diagramId`.
- [ ] Intentar añadir dos veces la misma clase al mismo diagrama y comprobar el rechazo.
- [ ] Añadir la misma clase a los dos diagramas y comprobar que se permite.

## Layout y relaciones

- [ ] Añadir tres o más relaciones a una misma clase y confirmar que sus puntos de salida están distribuidos.
- [ ] Mover y redimensionar nodos; refrescar el navegador y comprobar posición y tamaño.
- [ ] Refrescar y comprobar que relaciones, lados y anchors se conservan.
- [ ] Confirmar que cada relación usa una sola línea recta, diagonal en cualquier ángulo y sin quiebres de 90 grados.
- [ ] Mover una clase alrededor de otra y confirmar que los extremos cambian al lado apropiado del contorno al soltarla.
- [ ] Verificar asociación sólida y flechas únicamente en extremos navegables.
- [ ] Verificar agregación con rombo hueco en el extremo whole.
- [ ] Verificar composición con rombo relleno en el extremo composite.
- [ ] Verificar generalización sólida con triángulo hueco apuntando al general.
- [ ] Verificar realización discontinua con triángulo hueco apuntando a la interfaz.
- [ ] Verificar role names con `+`, `-`, `#` o `~` según visibilidad.

## Separación semántica/notación y permisos

- [ ] Usar `Quitar del diagrama` y confirmar que el elemento continúa en el explorador semántico.
- [ ] Eliminar un elemento del modelo y confirmar que desaparece de todos sus diagramas.
- [ ] Confirmar que mover un nodo incrementa `Diagram.revision`, pero no `UmlModel.revision`.
- [ ] Acceder como `VIEWER` y confirmar lectura sin controles de edición.
