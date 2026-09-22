# V12-B — presencia y actividad colaborativa

## Diseño

- La presencia es efímera y vive únicamente en memoria del backend.
- Cada pestaña envía heartbeat cada 15 segundos; una sesión sin actividad expira a los 45 segundos.
- Varias pestañas del mismo usuario se consolidan visualmente en un único colaborador.
- La selección, actividad y color son una proyección Web; nunca modifican `DiagramNode`.
- `collaboration_activity` conserva solamente mutaciones importantes publicadas después del commit.
- Los eventos REST/WebSocket y las revisiones semánticas o gráficas de V12-A no cambian.

Canales adicionales:

```text
SEND      /app/diagrams/{diagramId}/presence
SUBSCRIBE /topic/diagrams/{diagramId}/presence
GET       /api/diagrams/{diagramId}/collaboration/activity
```

## Checklist manual

1. Iniciar backend y Web; abrir el mismo diagrama como OWNER y COLLABORATOR.
2. Seleccionar `Product` en A y comprobar borde, nombre y color de A en B.
3. Seleccionar `Customer` en B y comprobar el indicador en A.
4. Enfocar el formulario de `Product` y comprobar `A está editando Product` sin bloquear a B.
5. Crear `price : Real`; comprobar refetch, resaltado temporal y actividad confirmada en B.
6. Crear/eliminar una Association y comprobar la proyección en la otra pestaña.
7. Arrastrar `Product`; comprobar un solo registro al finalizar el drag y que no cambia la revisión semántica.
8. Cerrar B; comprobar que desaparece inmediatamente o, ante una desconexión abrupta, antes de 45 segundos.
9. Repetir como VIEWER: puede observar presencia y actividad, pero no editar.
10. Refrescar la página y comprobar que la presencia se reconstruye y el historial confirmado permanece.
