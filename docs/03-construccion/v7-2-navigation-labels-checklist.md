# V7.2 — Navegación y labels UML

- [ ] Crear «Diagrama Principal» y «Diagrama Seguridad» en el mismo modelo mediante el botón de la sección DIAGRAMAS.
- [ ] Comprobar la lista, el indicador Activo y el breadcrumb Proyecto > Modelo > Diagrama; verificar tipo CLASS y revisión gráfica.
- [ ] Cambiar de diagrama: cambia diagramId en la URL y el canvas muestra únicamente sus nodes/edges.
- [ ] Añadir Customer y Sale; crear una asociación Customer 1 — 0..* Sale.
- [ ] Editar el extremo Customer: roleName customer, visibility PUBLIC; editar Sale: roleName sales, visibility PRIVATE.
- [ ] Guardar: comprobar +customer / 1 y -sales / 0..* en lados opuestos de la línea cerca de sus respectivos extremos.
- [ ] Cambiar visibility a PROTECTED/PACKAGE y verificar #/~; borrar roleName y comprobar que la multiplicidad sigue visible.
- [ ] Mover nodos horizontal, vertical y diagonalmente: comprobar que los labels siguen los extremos sin combinarse.
- [ ] Verificar que flechas de navegabilidad, rombos, triángulos y líneas rectas siguen sin cambios.
- [ ] F5 en el segundo diagrama: conserva diagrama activo, layout, relaciones, role names y visibilidad.
- [ ] VIEWER puede navegar y leer, pero no crear diagramas ni guardar extremos.

No hay nuevas migraciones: la visibilidad se guarda en UmlProperty.visibility existente.
