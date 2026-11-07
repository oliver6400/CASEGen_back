import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../models/project_summary.dart';
import '../../models/uml_model_summary.dart';
import '../../widgets/connectivity_badge.dart';
import '../auth/auth_controller.dart';
import '../offline/connectivity_controller.dart';
import '../projects/workspace_controller.dart';
import 'diagram_viewer_controller.dart';
import 'models/diagram_observation.dart';
import 'widgets/diagram_canvas.dart';

class DiagramViewerScreen extends StatefulWidget {
  const DiagramViewerScreen({super.key});

  @override
  State<DiagramViewerScreen> createState() => _DiagramViewerScreenState();
}

class _DiagramViewerScreenState extends State<DiagramViewerScreen> {
  final _transformController = TransformationController();
  final _searchController = TextEditingController();
  final _viewerKey = GlobalKey();
  Size _viewportSize = Size.zero;
  DiagramCanvasGeometry? _canvasGeometry;
  bool _isFullScreen = false;
  bool _fitScheduled = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadWorkspace());
  }

  @override
  void dispose() {
    _transformController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadWorkspace() async {
    final workspace = context.read<WorkspaceController>();
    await workspace.load();
    if (!mounted) return;
    await _loadSelectedModel(workspace);
  }

  Future<void> _loadSelectedModel(WorkspaceController workspace) async {
    final project = workspace.selectedProject;
    final model = workspace.selectedModel;
    if (project == null || model == null) {
      context.read<DiagramViewerController>().clearWorkspace();
      return;
    }
    _resetCamera();
    await context.read<DiagramViewerController>().loadDiagrams(
      projectId: project.id,
      modelId: model.id,
    );
  }

  Future<void> _selectProject(ProjectSummary? project) async {
    final workspace = context.read<WorkspaceController>();
    await workspace.selectProject(project);
    if (!mounted) return;
    await _loadSelectedModel(workspace);
  }

  Future<void> _selectModel(UmlModelSummary? model) async {
    final workspace = context.read<WorkspaceController>();
    await workspace.selectModel(model);
    if (!mounted) return;
    await _loadSelectedModel(workspace);
  }

  Future<void> _logout() async {
    await context.read<WorkspaceController>().reset();
    if (!mounted) return;
    context.read<DiagramViewerController>().clearWorkspace();
    await context.read<AuthController>().logout();
  }

  void _resetCamera() {
    _fitScheduled = false;
    _transformController.value = Matrix4.identity();
  }

  void _fitToScreen(DiagramCanvasGeometry geometry, {double? requestedScale}) {
    if (_viewportSize.isEmpty) return;
    final fitScale = math.min(
      (_viewportSize.width - 40) / geometry.width,
      (_viewportSize.height - 40) / geometry.height,
    );
    final scale = (requestedScale ?? fitScale).clamp(0.2, 3.0).toDouble();
    _transformController.value = Matrix4.identity()
      ..translateByDouble(
        (_viewportSize.width - geometry.width * scale) / 2,
        (_viewportSize.height - geometry.height * scale) / 2,
        0,
        1,
      )
      ..scaleByDouble(scale, scale, 1, 1);
  }

  void _focusNode(DiagramNode node) {
    final geometry = _canvasGeometry;
    if (geometry == null || _viewportSize.isEmpty) return;
    const scale = 1.15;
    final centerX = node.x + geometry.originX + math.max(220, node.width) / 2;
    final centerY = node.y + geometry.originY + math.max(128, node.height) / 2;
    _transformController.value = Matrix4.identity()
      ..translateByDouble(
        _viewportSize.width / 2 - centerX * scale,
        _viewportSize.height / 2 - centerY * scale,
        0,
        1,
      )
      ..scaleByDouble(scale, scale, 1, 1);
  }

  void _searchClass(DiagramObservation observation) {
    final query = _searchController.text.trim().toLowerCase();
    if (query.isEmpty) return;
    for (final node in observation.nodes) {
      final modelClass = observation.classFor(node.umlElementId);
      if (modelClass != null && modelClass.name.toLowerCase().contains(query)) {
        _focusNode(node);
        return;
      }
    }
    if (observation.nodes.isEmpty ||
        observation.nodes.every(
          (node) => observation.classFor(node.umlElementId) == null,
        )) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(content: Text('Este diagrama no contiene clases.')),
        );
      return;
    }
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(
          content: Text('No se encontró esa clase en este diagrama.'),
        ),
      );
  }

  void _showClassDetails(
    BuildContext context,
    DiagramObservation observation,
    DiagramClass modelClass,
  ) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) =>
          _ClassDetailsSheet(observation: observation, modelClass: modelClass),
    );
  }

  Future<void> _showAiConversation(
    BuildContext context,
    DiagramViewerController controller,
  ) async {
    await controller.refreshAiConversation();
    if (!context.mounted) return;
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => _AiConversationSheet(controller: controller),
    );
  }

  @override
  Widget build(BuildContext context) {
    final workspace = context.watch<WorkspaceController>();
    final viewer = context.watch<DiagramViewerController>();
    final connectivity = context.watch<ConnectivityController>();
    final observation = viewer.observation;
    final selectedModel = workspace.selectedModel;
    final selectedProject = workspace.selectedProject;

    return Scaffold(
      appBar: _isFullScreen
          ? null
          : AppBar(
              title: const Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('CASEGen'),
                  Text(
                    'Consulta de solo lectura',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w400),
                  ),
                ],
              ),
              actions: [
                ConnectivityBadge(isOnline: connectivity.isOnline),
                IconButton(
                  tooltip: 'Chat IA (solo lectura)',
                  onPressed: () => _showAiConversation(context, viewer),
                  icon: const Icon(Icons.chat_bubble_outline),
                ),
                IconButton(
                  tooltip: 'Cerrar sesión',
                  onPressed: _logout,
                  icon: const Icon(Icons.logout),
                ),
              ],
            ),
      body: SafeArea(
        child: Column(
          children: [
            if (_isFullScreen)
              Align(
                alignment: Alignment.topRight,
                child: IconButton.filledTonal(
                  tooltip: 'Salir de pantalla completa',
                  onPressed: () => setState(() => _isFullScreen = false),
                  icon: const Icon(Icons.fullscreen_exit),
                ),
              )
            else
              _WorkspacePicker(
                workspace: workspace,
                viewer: viewer,
                onProjectSelected: _selectProject,
                onModelSelected: _selectModel,
                onDiagramSelected: (diagram) {
                  _resetCamera();
                  viewer.selectDiagram(diagram);
                },
              ),
            if (!_isFullScreen && viewer.selectedDiagram != null)
              _PresenceBar(
                participants: viewer.participants,
                connected: viewer.isRealtimeConnected,
                onFollow: (participant) {
                  final id = participant.selectedElementId;
                  if (id == null || observation == null) return;
                  for (final node in observation.nodes) {
                    if (node.umlElementId == id) {
                      _focusNode(node);
                      return;
                    }
                  }
                },
              ),
            if (!_isFullScreen && viewer.realtimeErrorMessage != null)
              _InfoBanner(
                message: viewer.realtimeErrorMessage!,
                icon: Icons.sync_problem_outlined,
              ),
            if (workspace.errorMessage != null)
              _InfoBanner(
                message: workspace.errorMessage!,
                icon: Icons.error_outline,
              ),
            if (viewer.errorMessage != null)
              _InfoBanner(
                message: viewer.errorMessage!,
                icon: Icons.error_outline,
                onRetry: viewer.reloadDiagram,
              ),
            if (!_isFullScreen && observation != null)
              _DiagramToolbar(
                searchController: _searchController,
                onSearch: () => _searchClass(observation),
                onFit: () {
                  final geometry = _canvasGeometry;
                  if (geometry != null) _fitToScreen(geometry);
                },
                onFullscreen: () => setState(() => _isFullScreen = true),
              ),
            Expanded(
              child: _buildCanvas(
                viewer,
                observation,
                selectedProject?.name,
                selectedModel?.name,
              ),
            ),
            if (!_isFullScreen && observation != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 6),
                child: Text(
                  'Diagrama ${observation.name} · revisión ${observation.revision} · '
                  '${observation.nodes.length} clases · ${observation.edges.length} relaciones',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelSmall,
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildCanvas(
    DiagramViewerController viewer,
    DiagramObservation? observation,
    String? projectName,
    String? modelName,
  ) {
    if (viewer.isLoadingDiagrams && viewer.diagrams.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (viewer.isLoadingDiagram && observation == null) {
      return const Center(child: CircularProgressIndicator());
    }
    if (viewer.selectedDiagram == null) {
      final message = modelName == null
          ? 'Selecciona un proyecto y un modelo UML.'
          : 'Este modelo todavía no tiene diagramas.';
      return _EmptyState(
        icon: Icons.account_tree_outlined,
        title: 'No hay un diagrama para mostrar',
        message: message,
      );
    }
    if (observation == null) {
      return _EmptyState(
        icon: Icons.cloud_off_outlined,
        title: 'No se pudo cargar el diagrama',
        message:
            viewer.errorMessage ?? 'Revisa la conexión e inténtalo de nuevo.',
        onRetry: viewer.reloadDiagram,
      );
    }

    return LayoutBuilder(
      key: _viewerKey,
      builder: (context, constraints) {
        _viewportSize = Size(constraints.maxWidth, constraints.maxHeight);
        final geometry = DiagramCanvasGeometry.from(observation);
        _canvasGeometry = geometry;
        if (!_fitScheduled) {
          _fitScheduled = true;
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted && identical(viewer.observation, observation)) {
              _fitToScreen(geometry);
            }
          });
        }
        return Stack(
          children: [
            Positioned.fill(
              child: InteractiveViewer(
                transformationController: _transformController,
                minScale: 0.2,
                maxScale: 3,
                constrained: false,
                boundaryMargin: const EdgeInsets.all(400),
                child: DiagramCanvas(
                  observation: observation,
                  geometry: geometry,
                  participants: viewer.participants,
                  onClassTap: (modelClass) =>
                      _showClassDetails(context, observation, modelClass),
                ),
              ),
            ),
            if (viewer.isLoadingDiagram && viewer.observation != null)
              const Positioned(
                top: 10,
                right: 12,
                child: SizedBox.square(
                  dimension: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
          ],
        );
      },
    );
  }
}

class _WorkspacePicker extends StatelessWidget {
  const _WorkspacePicker({
    required this.workspace,
    required this.viewer,
    required this.onProjectSelected,
    required this.onModelSelected,
    required this.onDiagramSelected,
  });

  final WorkspaceController workspace;
  final DiagramViewerController viewer;
  final ValueChanged<ProjectSummary?> onProjectSelected;
  final ValueChanged<UmlModelSummary?> onModelSelected;
  final ValueChanged<DiagramSummary?> onDiagramSelected;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 8, 12, 2),
    child: Column(
      children: [
        Row(
          children: [
            Expanded(
              child: DropdownButtonFormField<ProjectSummary>(
                key: ValueKey(workspace.selectedProject?.id),
                initialValue: workspace.selectedProject,
                isExpanded: true,
                decoration: const InputDecoration(
                  labelText: 'Proyecto',
                  prefixIcon: Icon(Icons.folder_outlined),
                  contentPadding: EdgeInsets.symmetric(
                    vertical: 10,
                    horizontal: 12,
                  ),
                ),
                items: workspace.projects
                    .map(
                      (project) => DropdownMenuItem(
                        value: project,
                        child: Text(
                          project.name,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    )
                    .toList(),
                onChanged: workspace.isLoading ? null : onProjectSelected,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: DropdownButtonFormField<UmlModelSummary>(
                key: ValueKey(
                  '${workspace.selectedProject?.id}-${workspace.selectedModel?.id}',
                ),
                initialValue: workspace.selectedModel,
                isExpanded: true,
                decoration: const InputDecoration(
                  labelText: 'Modelo UML',
                  prefixIcon: Icon(Icons.account_tree_outlined),
                  contentPadding: EdgeInsets.symmetric(
                    vertical: 10,
                    horizontal: 12,
                  ),
                ),
                items: workspace.models
                    .map(
                      (model) => DropdownMenuItem(
                        value: model,
                        child: Text(
                          model.name,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    )
                    .toList(),
                onChanged: workspace.isLoading ? null : onModelSelected,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        DropdownButtonFormField<DiagramSummary>(
          key: ValueKey(
            '${workspace.selectedModel?.id}-${viewer.selectedDiagram?.id}',
          ),
          initialValue: viewer.selectedDiagram,
          isExpanded: true,
          decoration: const InputDecoration(
            labelText: 'Diagrama',
            prefixIcon: Icon(Icons.schema_outlined),
            contentPadding: EdgeInsets.symmetric(vertical: 10, horizontal: 12),
          ),
          items: viewer.diagrams
              .map(
                (diagram) => DropdownMenuItem(
                  value: diagram,
                  child: Text(diagram.name, overflow: TextOverflow.ellipsis),
                ),
              )
              .toList(),
          onChanged: viewer.isLoadingDiagrams ? null : onDiagramSelected,
        ),
      ],
    ),
  );
}

class _PresenceBar extends StatelessWidget {
  const _PresenceBar({
    required this.participants,
    required this.connected,
    required this.onFollow,
  });

  final List<PresenceParticipant> participants;
  final bool connected;
  final ValueChanged<PresenceParticipant> onFollow;

  @override
  Widget build(BuildContext context) => SizedBox(
    height: 48,
    child: Row(
      children: [
        const SizedBox(width: 12),
        Icon(
          Icons.circle,
          size: 9,
          color: connected ? const Color(0xFF16A34A) : Colors.grey,
        ),
        const SizedBox(width: 6),
        Text(
          connected ? 'En vivo' : 'Sin conexión en vivo',
          style: Theme.of(context).textTheme.labelSmall,
        ),
        const SizedBox(width: 8),
        Expanded(
          child: ListView(
            scrollDirection: Axis.horizontal,
            children: [
              for (final participant in participants)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 3),
                  child: ActionChip(
                    avatar: CircleAvatar(
                      backgroundColor: participant.activity == 'EDITING'
                          ? const Color(0xFFDCFCE7)
                          : const Color(0xFFDBEAFE),
                      child: Text(
                        participant.displayName.characters.first.toUpperCase(),
                        style: const TextStyle(fontSize: 11),
                      ),
                    ),
                    label: Text(
                      '${participant.displayName} · '
                      '${participant.activity == 'EDITING' ? 'Editando' : 'Viendo'}',
                      style: const TextStyle(fontSize: 11),
                    ),
                    onPressed: participant.selectedElementId == null
                        ? null
                        : () => onFollow(participant),
                  ),
                ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _DiagramToolbar extends StatelessWidget {
  const _DiagramToolbar({
    required this.searchController,
    required this.onSearch,
    required this.onFit,
    required this.onFullscreen,
  });

  final TextEditingController searchController;
  final VoidCallback onSearch;
  final VoidCallback onFit;
  final VoidCallback onFullscreen;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 0, 8, 4),
    child: Row(
      children: [
        Expanded(
          child: TextField(
            controller: searchController,
            textInputAction: TextInputAction.search,
            onSubmitted: (_) => onSearch(),
            decoration: const InputDecoration(
              hintText: 'Buscar clase',
              prefixIcon: Icon(Icons.search),
              contentPadding: EdgeInsets.symmetric(vertical: 8),
            ),
          ),
        ),
        IconButton(
          tooltip: 'Enfocar clase',
          onPressed: onSearch,
          icon: const Icon(Icons.my_location_outlined),
        ),
        IconButton(
          tooltip: 'Centrar diagrama',
          onPressed: onFit,
          icon: const Icon(Icons.fit_screen),
        ),
        IconButton(
          tooltip: 'Pantalla completa',
          onPressed: onFullscreen,
          icon: const Icon(Icons.fullscreen),
        ),
      ],
    ),
  );
}

class _InfoBanner extends StatelessWidget {
  const _InfoBanner({required this.message, required this.icon, this.onRetry});

  final String message;
  final IconData icon;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 3),
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
    decoration: BoxDecoration(
      color: const Color(0xFFFFF7ED),
      borderRadius: BorderRadius.circular(10),
    ),
    child: Row(
      children: [
        Icon(icon, size: 18, color: const Color(0xFFB45309)),
        const SizedBox(width: 8),
        Expanded(child: Text(message, style: const TextStyle(fontSize: 12))),
        if (onRetry != null)
          IconButton(
            tooltip: 'Reintentar',
            visualDensity: VisualDensity.compact,
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
          ),
      ],
    ),
  );
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({
    required this.icon,
    required this.title,
    required this.message,
    this.onRetry,
  });

  final IconData icon;
  final String title;
  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 48, color: Theme.of(context).colorScheme.primary),
          const SizedBox(height: 14),
          Text(
            title,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 6),
          Text(message, textAlign: TextAlign.center),
          if (onRetry != null) ...[
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar'),
            ),
          ],
        ],
      ),
    ),
  );
}

class _ClassDetailsSheet extends StatelessWidget {
  const _ClassDetailsSheet({
    required this.observation,
    required this.modelClass,
  });

  final DiagramObservation observation;
  final DiagramClass modelClass;

  @override
  Widget build(BuildContext context) {
    final related = <String>[];
    final visibleRelationshipIds = observation.edges
        .map((edge) => edge.umlElementId)
        .toSet();
    for (final association in observation.associations) {
      if (visibleRelationshipIds.contains(association.id) &&
          association.ends.any((end) => end.classifierId == modelClass.id)) {
        final otherEnds = association.ends.where(
          (end) => end.classifierId != modelClass.id,
        );
        for (final end in otherEnds) {
          related.add(
            'Asociación con ${observation.classFor(end.classifierId)?.name ?? 'elemento UML'}'
            '${end.roleName == null ? '' : ' (${end.roleName})'}'
            ' · ${end.lowerBound}..${end.upperBound ?? '*'}',
          );
        }
      }
    }
    for (final generalization in observation.generalizations) {
      if (!visibleRelationshipIds.contains(generalization.id)) continue;
      if (generalization.specificClassifierId == modelClass.id) {
        related.add(
          'Hereda de ${observation.classFor(generalization.generalClassifierId)?.name ?? 'elemento UML'}',
        );
      } else if (generalization.generalClassifierId == modelClass.id) {
        related.add(
          'Es clase base de ${observation.classFor(generalization.specificClassifierId)?.name ?? 'elemento UML'}',
        );
      }
    }
    for (final realization in observation.realizations) {
      if (!visibleRelationshipIds.contains(realization.id)) continue;
      if (realization.clientClassifierId == modelClass.id) {
        related.add(
          'Implementa ${observation.classFor(realization.supplierClassifierId)?.name ?? 'interfaz'}',
        );
      } else if (realization.supplierClassifierId == modelClass.id) {
        related.add(
          'Es implementada por ${observation.classFor(realization.clientClassifierId)?.name ?? 'clase'}',
        );
      }
    }

    return SafeArea(
      child: DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.72,
        minChildSize: 0.4,
        maxChildSize: 0.94,
        builder: (context, scrollController) => ListView(
          controller: scrollController,
          padding: const EdgeInsets.fromLTRB(20, 4, 20, 28),
          children: [
            Text(
              modelClass.name,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            Text(
              '${modelClass.kind == 'INTERFACE' ? 'Interfaz' : 'Clase'} · '
              '${modelClass.visibility.toLowerCase()}'
              '${modelClass.isAbstract ? ' · abstracta' : ''}',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 18),
            _sectionTitle(context, 'Atributos'),
            if (modelClass.properties.isEmpty)
              const Text('Sin atributos')
            else
              for (final property in modelClass.properties)
                ListTile(
                  dense: true,
                  leading: const Icon(Icons.data_object, size: 18),
                  title: Text(
                    '${umlVisibilitySymbol(property.visibility)}${property.name}',
                  ),
                  subtitle: Text(
                    '${property.type} · ${property.lowerBound}..${property.upperBound ?? '*'}'
                    '${property.isReadOnly ? ' · solo lectura' : ''}'
                    '${property.isStatic ? ' · estático' : ''}',
                  ),
                ),
            const Divider(),
            _sectionTitle(context, 'Operaciones'),
            if (modelClass.operations.isEmpty)
              const Text('Sin operaciones')
            else
              for (final operation in modelClass.operations)
                ListTile(
                  dense: true,
                  leading: const Icon(Icons.functions, size: 18),
                  title: Text(
                    '${umlVisibilitySymbol(operation.visibility)}${operation.name}',
                  ),
                  subtitle: Text(_formatOperationDetails(operation)),
                ),
            const Divider(),
            _sectionTitle(context, 'Relaciones'),
            if (related.isEmpty)
              const Text('Sin relaciones visibles en este diagrama.')
            else
              for (final item in related)
                ListTile(
                  dense: true,
                  leading: const Icon(Icons.hub_outlined, size: 18),
                  title: Text(item),
                ),
          ],
        ),
      ),
    );
  }

  Widget _sectionTitle(BuildContext context, String title) => Padding(
    padding: const EdgeInsets.only(bottom: 6),
    child: Text(title, style: Theme.of(context).textTheme.titleMedium),
  );
}

class _AiConversationSheet extends StatelessWidget {
  const _AiConversationSheet({required this.controller});

  final DiagramViewerController controller;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: controller,
    builder: (context, _) => SafeArea(
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.75,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 10),
              child: Row(
                children: [
                  const Icon(Icons.auto_awesome_outlined),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'Chat IA · solo lectura',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                  IconButton(
                    tooltip: 'Actualizar conversación',
                    onPressed: controller.refreshAiConversation,
                    icon: const Icon(Icons.refresh),
                  ),
                ],
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: controller.isLoadingAi
                  ? const Center(child: CircularProgressIndicator())
                  : controller.aiErrorMessage != null
                  ? Center(
                      child: Padding(
                        padding: const EdgeInsets.all(20),
                        child: Text(
                          controller.aiErrorMessage!,
                          textAlign: TextAlign.center,
                        ),
                      ),
                    )
                  : controller.aiSession == null
                  ? const _EmptyState(
                      icon: Icons.chat_bubble_outline,
                      title: 'No hay una sesión de chat activa',
                      message: 'Cuando exista una conversación del proyecto, aparecerá aquí.',
                    )
                  : controller.aiMessages.isEmpty
                  ? const _EmptyState(
                      icon: Icons.forum_outlined,
                      title: 'Conversación vacía',
                      message: 'Todavía no hay mensajes en esta sesión.',
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.all(16),
                      itemCount: controller.aiMessages.length,
                      itemBuilder: (context, index) {
                        final message = controller.aiMessages[index];
                        final isAssistant = message.role == 'ASSISTANT';
                        return Align(
                          alignment: isAssistant
                              ? Alignment.centerLeft
                              : Alignment.centerRight,
                          child: Container(
                            constraints: const BoxConstraints(maxWidth: 340),
                            margin: const EdgeInsets.only(bottom: 10),
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: isAssistant
                                  ? const Color(0xFFF0F2FB)
                                  : const Color(0xFFEDEBFF),
                              borderRadius: BorderRadius.circular(14),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  message.authorName ??
                                      (isAssistant
                                          ? 'Asistente IA'
                                          : 'Participante'),
                                  style: const TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                                const SizedBox(height: 4),
                                SelectableText(message.content),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
            ),
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 8, 16, 14),
              child: Text(
                'En el móvil puedes consultar el chat, pero no enviar mensajes.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 12, color: Colors.black54),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

String _formatOperationDetails(UmlOperation operation) {
  final parameters = operation.parameters
      .where((item) => item.direction != 'RETURN')
      .map((item) => '${item.name}: ${item.type}')
      .join(', ');
  final returnType = operation.parameters
      .where((item) => item.direction == 'RETURN')
      .firstOrNull
      ?.type;
  return '$parameters${returnType == null ? '' : ' → $returnType'}';
}
