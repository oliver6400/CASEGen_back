# Casos de uso

# Casos de Uso de CASEGen

## Actores

### Modelador
Usuario que construye y modifica modelos UML.

### Colaborador
Usuario que participa en una sesión compartida.

### Desarrollador
Usuario interesado en generar backend.

### Usuario móvil
Usuario que consulta o modifica el modelo mediante
la aplicación móvil.

### Servicio IA
Componente externo que transforma lenguaje natural
en comandos estructurados.

## CU-01 Crear proyecto

Actor: Modelador

Flujo principal:

1. El usuario solicita crear proyecto.
2. Ingresa nombre y descripción.
3. CASEGen valida los datos.
4. CASEGen registra el proyecto.
5. El sistema muestra el nuevo proyecto.

## CU-02 Crear elemento UML

1. El usuario abre un proyecto.
2. Selecciona un elemento UML.
3. Lo agrega al diagrama.
4. CASEGen crea su representación semántica.
5. Se crea también la representación gráfica.
6. El cambio es guardado.

## CU-03 Importar XMI

1. El usuario selecciona un XMI.
2. CASEGen verifica formato.
3. XmiImporter analiza el archivo.
4. Se crean elementos semánticos.
5. Se resuelven referencias.
6. Se valida el resultado.
7. El modelo queda disponible para edición.

## CU-04 Editar colaborativamente

1. Dos usuarios abren el mismo modelo.
2. El usuario A modifica un elemento.
3. El cambio es sincronizado.
4. El usuario B recibe la actualización.
5. Ambos continúan trabajando sobre el modelo.

## CU-05 Validar modelo

1. El usuario solicita validación.
2. CASEGen analiza reglas estructurales.
3. Si existen errores, los reporta.
4. Si el modelo es válido, habilita versionado.

## CU-06 Crear versión

1. El usuario valida el modelo.
2. Solicita crear versión.
3. CASEGen crea un snapshot inmutable.
4. La versión queda disponible para generación.

## CU-07 Generar backend

1. El usuario selecciona una versión.
2. Solicita generación.
3. CASEGen transforma UML a modelo de generación.
4. Se genera el proyecto Spring Boot.
5. Se produce el artefacto final.

## CU-08 Modificar modelo desde móvil

1. El usuario selecciona un proyecto.
2. Ingresa un comando.
3. El móvil envía el comando.
4. CASEGen lo valida.
5. Se modifica el modelo.
6. Los clientes conectados reciben el cambio.