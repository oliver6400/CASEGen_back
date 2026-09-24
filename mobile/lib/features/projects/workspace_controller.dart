import 'package:flutter/foundation.dart';

import '../../core/network/api_exception.dart';
import '../../core/storage/selection_storage.dart';
import '../../models/project_summary.dart';
import '../../models/uml_model_summary.dart';
import '../../services/project_service.dart';

class WorkspaceController extends ChangeNotifier {
  WorkspaceController(this._projectService, this._selectionStorage);

  final ProjectService _projectService;
  final SelectionStorage _selectionStorage;

  List<ProjectSummary> projects = const [];
  List<UmlModelSummary> models = const [];
  ProjectSummary? selectedProject;
  UmlModelSummary? selectedModel;
  bool isLoading = false;
  String? errorMessage;
  bool _loaded = false;

  Future<void> load() async {
    if (_loaded || isLoading) return;
    isLoading = true;
    errorMessage = null;
    notifyListeners();
    try {
      final saved = await _selectionStorage.read();
      projects = await _projectService.listProjects();
      selectedProject = _findProject(saved.projectId) ?? projects.firstOrNull;
      if (selectedProject != null) {
        models = await _projectService.listModels(selectedProject!.id);
        selectedModel = _findModel(saved.modelId) ?? models.firstOrNull;
      }
      _loaded = true;
    } on ApiException catch (error) {
      errorMessage = error.message;
    } finally {
      isLoading = false;
      notifyListeners();
    }
  }

  Future<void> selectProject(ProjectSummary? project) async {
    selectedProject = project;
    selectedModel = null;
    models = const [];
    errorMessage = null;
    notifyListeners();
    await _selectionStorage.saveProject(project?.id);
    if (project == null) return;
    isLoading = true;
    notifyListeners();
    try {
      models = await _projectService.listModels(project.id);
      selectedModel = models.firstOrNull;
      await _selectionStorage.saveModel(selectedModel?.id);
    } on ApiException catch (error) {
      errorMessage = error.message;
    } finally {
      isLoading = false;
      notifyListeners();
    }
  }

  Future<void> selectModel(UmlModelSummary? model) async {
    selectedModel = model;
    notifyListeners();
    await _selectionStorage.saveModel(model?.id);
  }

  Future<void> reset() async {
    projects = const [];
    models = const [];
    selectedProject = null;
    selectedModel = null;
    errorMessage = null;
    isLoading = false;
    _loaded = false;
    await _selectionStorage.saveProject(null);
    notifyListeners();
  }

  ProjectSummary? _findProject(String? id) {
    if (id == null) return null;
    for (final project in projects) {
      if (project.id == id) return project;
    }
    return null;
  }

  UmlModelSummary? _findModel(String? id) {
    if (id == null) return null;
    for (final model in models) {
      if (model.id == id) return model;
    }
    return null;
  }
}
