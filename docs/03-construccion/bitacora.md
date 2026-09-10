# Bitácora de construcción
## 01/09/2026

### Objetivo
Inicializar la infraestructura backend de CASEGen.

### Implementado
- Configuración Maven.
- PostgreSQL 17.
- Usuario casegen_user.
- Base de datos casegen.
- Spring Boot.
- Flyway.
- Migración V1.
- Entidad Project.
- Repository.
- Service.
- API REST Project.
- Pruebas Postman.

### Resultado
Primer flujo vertical completo del sistema funcionando.

### Evidencias
- POST /api/v1/projects
- GET /api/v1/projects
- tabla projects en PostgreSQL

### Commit
feat: implement project management foundation

## 08/09/2026

### Objetivo

Implementar V3 como primera vertical funcional del UML Semantic Core.

### Implementado

- Migración Flyway V3 para `uml_models`, `uml_elements`, `uml_packages` y
  `uml_classifiers`.
- Composición JPA con clave primaria compartida mediante `@MapsId`.
- Revisión semántica separada de `@Version` para optimistic locking.
- Casos de uso transaccionales para crear modelos, paquetes y clasificadores.
- Validación de modelo editable, pertenencia de paquetes y regla de Interface
  no activa.
- API de dominio para creación y consulta.
- Errores semánticos estructurados.
- Colección Postman con flujo completo V3.

### Verificación

- Flyway validó y aplicó V1, V2 y V3 en PostgreSQL 17.
- Spring Boot inició con Hibernate `ddl-auto=validate`.
- Maven ejecutó 41 pruebas: 41 correctas, 0 fallos, 0 errores.

### Seguridad pendiente

CASEGen aún no tiene autenticación real. V3 mantiene `created_by` nulo y no
acepta IDs de usuario ni headers de autenticación inventados. La autorización
por membresía de Project se implementará cuando exista un Principal real.
