export interface HelpSection {
  id: string
  title: string
  summary: string
  steps: string[]
  recommendations: string[]
  screen: string
  keywords: string[]
}

export const helpSections: HelpSection[] = [
  section('primeros-pasos', 'Primeros pasos', 'Cree un proyecto y organice allí sus modelos y diagramas.',
    ['Abra Proyectos.', 'Cree un proyecto con nombre y descripción.', 'Entre al proyecto para crear o importar un modelo.'],
    ['Empiece con un modelo pequeño y amplíelo después.'], 'Proyectos', ['inicio', 'proyecto']),
  section('proyectos-colaboradores', 'Proyectos y colaboradores', 'Comparta un proyecto asignando permisos acordes al trabajo de cada persona.',
    ['Abra el proyecto.', 'Revise Miembros e Invitaciones.', 'Invite como Colaborador o Viewer.'],
    ['Use Viewer para revisión sin edición.', 'Solo el Owner debe administrar miembros.'], 'Proyecto > Miembros', ['miembro', 'invitar', 'colaborador', 'viewer', 'owner']),
  section('crear-modelos', 'Crear modelos UML', 'Un modelo semántico contiene classifiers, características y relaciones UML.',
    ['Abra un proyecto.', 'Cree un modelo UML.', 'Cree o seleccione un diagrama de clases.'],
    ['Separe modelos por dominio o contexto cuando crezcan demasiado.'], 'Proyecto > Modelos UML', ['modelo', 'diagrama']),
  section('clases-interfaces', 'Clases e interfaces', 'Las clases representan conceptos del dominio; las interfaces representan contratos.',
    ['Abra un diagrama.', 'Use Clase o «interface» en la toolbar.', 'Asigne un nombre claro y edite sus detalles.'],
    ['Use sustantivos para clases y nombres de contrato para interfaces.'], 'Editor UML > Toolbar', ['classifier', 'clase', 'interfaz']),
  section('atributos-operaciones', 'Atributos y operaciones', 'Complete cada classifier con propiedades, operaciones, parámetros y retorno.',
    ['Seleccione una clase o interfaz.', 'Use Nueva propiedad o Nueva operación.', 'Defina visibilidad, tipo y multiplicidad.'],
    ['El retorno de una operación se representa como parámetro RETURN.', 'Revise los tipos UNKNOWN antes de validar.'], 'Editor UML > Propiedades', ['atributo', 'propiedad', 'operación', 'parámetro', 'return']),
  section('relaciones', 'Relaciones UML', 'Conecte classifiers mediante asociación, agregación, composición, generalización o realización.',
    ['Seleccione la herramienta de relación.', 'Elija el classifier origen y el destino.', 'Revise roles, navegabilidad y multiplicidades en Propiedades.'],
    ['Use composición solo cuando la parte dependa del ciclo de vida del todo.'], 'Editor UML > Toolbar y Propiedades', ['relación', 'asociación', 'agregación', 'composición', 'generalización', 'realización']),
  section('validacion', 'Validación del modelo', 'La validación detecta inconsistencias antes de crear una versión inmutable.',
    ['Pulse Validar modelo.', 'Revise errores y advertencias.', 'Corrija el modelo y vuelva a validar.'],
    ['Valide siempre la revisión semántica actual.'], 'Editor UML > Validación y versiones', ['validar', 'error', 'warning']),
  section('versiones', 'Versiones', 'Una versión captura un snapshot validado e inmutable del modelo.',
    ['Valide la revisión actual.', 'Pulse Crear versión.', 'Seleccione la versión que usará en los pasos posteriores.'],
    ['Cree una nueva versión después de cambios semánticos importantes.'], 'Editor UML > Validación y versiones', ['versión', 'snapshot', 'revisión']),
  section('modelo-logico', 'Modelo lógico', 'Transforme una versión UML y su perfil en tablas, columnas, claves y restricciones.',
    ['Seleccione ModelVersion y perfil.', 'Revise mappings e identificadores.', 'Pulse Construir modelo lógico.'],
    ['Compruebe las decisiones 1:1 ambiguas antes de construir.'], 'Editor UML > Modelo lógico relacional', ['lógico', 'tabla', 'mapping', 'perfil']),
  section('normalizacion', 'Normalización', 'Analice de forma independiente el modelo relacional hasta 3FN.',
    ['Seleccione un modelo lógico.', 'Pulse Analizar normalización.', 'Revise issues y recomendaciones.'],
    ['Una advertencia de normalización no bloquea la generación.'], 'Editor UML > Normalización', ['normalización', '1fn', '2fn', '3fn']),
  section('xmi', 'Importar/exportar XMI', 'Intercambie el subconjunto UML soportado con Enterprise Architect y otras herramientas.',
    ['Para importar, abra el proyecto y seleccione Importar XMI.', 'Revise el resumen y abra el diagrama creado.', 'Para exportar, use Exportar XMI en el editor.'],
    ['Conserve el archivo original para comparar el round-trip.'], 'Proyecto > Importar / Editor > Exportar XMI', ['xmi', 'enterprise architect', 'importar', 'exportar']),
  section('imagen', 'Importar desde imagen', 'Convierta una fotografía o imagen UML en una propuesta editable antes de crear el modelo.',
    ['Abra Importar desde imagen.', 'Analice PNG, JPG o WEBP.', 'Revise y corrija la propuesta.', 'Pulse Crear modelo.'],
    ['Compruebe nombres, relaciones y multiplicidades de baja confianza.'], 'Proyecto > Importar > Imagen', ['imagen', 'foto', 'gemini', 'ocr']),
  section('generar-backend', 'Generar backend', 'Genere un proyecto Spring Boot únicamente desde un LogicalModel válido.',
    ['Seleccione un LogicalModel válido.', 'Pulse Generar backend.', 'Observe las etapas hasta SUCCESS o revise el error.'],
    ['No cierre el historial: la generación continúa en el backend.'], 'Editor UML > Generaciones backend', ['backend', 'spring', 'generar', 'generation']),
  section('descargar-backend', 'Descargar y probar backend', 'Descargue el ZIP verificado y ejecútelo de forma aislada.',
    ['Abra una generación SUCCESS.', 'Pulse Descargar backend.', 'Extraiga el ZIP.', 'Siga README o ejecute docker compose up --build -d.'],
    ['Use el .env del proyecto generado; no reutilice credenciales de CASEGen.'], 'Generación > Ver generación', ['descargar', 'zip', 'docker', 'postman', 'swagger']),
]

export const tourSteps = [
  { title: '1. Proyecto', text: 'Cree el espacio que agrupa modelos, miembros e importaciones.', screen: 'Proyectos' },
  { title: '2. Modelo UML', text: 'Cree un modelo semántico y uno o varios diagramas de clases.', screen: 'Proyecto' },
  { title: '3. Classifier', text: 'Añada clases e interfaces; después complete atributos y operaciones.', screen: 'Editor UML' },
  { title: '4. Relaciones', text: 'Conecte classifiers y revise extremos, multiplicidades y navegabilidad.', screen: 'Toolbar UML' },
  { title: '5. Validación', text: 'Valide la revisión semántica actual antes de crear una versión.', screen: 'Validación y versiones' },
  { title: '6. Modelo lógico', text: 'Configure mappings y transforme una versión inmutable al modelo relacional.', screen: 'Modelo lógico relacional' },
  { title: '7. Generación', text: 'Genere, verifique y descargue el backend Spring Boot.', screen: 'Generaciones backend' },
  { title: '8. Importar/Exportar', text: 'Use XMI para interoperabilidad o una imagen para crear una propuesta revisable.', screen: 'Proyecto / Editor UML' },
]

function section(id: string, title: string, summary: string, steps: string[], recommendations: string[], screen: string, keywords: string[]): HelpSection {
  return { id, title, summary, steps, recommendations, screen, keywords }
}
