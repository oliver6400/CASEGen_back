import 'package:shared_preferences/shared_preferences.dart';

class WorkspaceSelection {
  const WorkspaceSelection({this.projectId, this.modelId});

  final String? projectId;
  final String? modelId;
}

class SelectionStorage {
  static const _projectKey = 'selected_project_id';
  static const _modelKey = 'selected_model_id';

  Future<WorkspaceSelection> read() async {
    final preferences = await SharedPreferences.getInstance();
    return WorkspaceSelection(
      projectId: preferences.getString(_projectKey),
      modelId: preferences.getString(_modelKey),
    );
  }

  Future<void> saveProject(String? projectId) async {
    final preferences = await SharedPreferences.getInstance();
    if (projectId == null) {
      await preferences.remove(_projectKey);
      await preferences.remove(_modelKey);
      return;
    }
    await preferences.setString(_projectKey, projectId);
  }

  Future<void> saveModel(String? modelId) async {
    final preferences = await SharedPreferences.getInstance();
    if (modelId == null) {
      await preferences.remove(_modelKey);
      return;
    }
    await preferences.setString(_modelKey, modelId);
  }
}
