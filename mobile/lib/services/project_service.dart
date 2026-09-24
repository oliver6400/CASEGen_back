import 'package:dio/dio.dart';

import '../core/network/api_client.dart';
import '../core/network/api_exception.dart';
import '../models/project_summary.dart';
import '../models/uml_model_summary.dart';

class ProjectService {
  const ProjectService(this._apiClient);

  final ApiClient _apiClient;

  Future<List<ProjectSummary>> listProjects() async {
    try {
      final response = await _apiClient.dio.get<List<dynamic>>(
        '/api/v1/projects',
      );
      return (response.data ?? const [])
          .map((item) => ProjectSummary.fromJson(item as Map<String, dynamic>))
          .toList();
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  Future<List<UmlModelSummary>> listModels(String projectId) async {
    try {
      final response = await _apiClient.dio.get<List<dynamic>>(
        '/api/projects/$projectId/uml-models',
      );
      return (response.data ?? const [])
          .map((item) => UmlModelSummary.fromJson(item as Map<String, dynamic>))
          .toList();
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }
}
