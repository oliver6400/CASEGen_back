import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/api_exception.dart';
import '../models/diagram_observation.dart';

class ObservationService {
  const ObservationService(this._apiClient);

  final ApiClient _apiClient;

  Future<List<Map<String, dynamic>>> listDiagrams(String modelId) async {
    try {
      final response = await _apiClient.dio.get<List<dynamic>>(
        '/api/uml-models/$modelId/diagrams',
      );
      return (response.data ?? const [])
          .map((item) => item as Map<String, dynamic>)
          .toList();
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  Future<DiagramObservation> loadDiagram(
    String modelId,
    String diagramId,
  ) async {
    try {
      final results = await Future.wait<dynamic>([
        _apiClient.dio.get<Map<String, dynamic>>('/api/diagrams/$diagramId'),
        _apiClient.dio.get<List<dynamic>>(
          '/api/uml-models/$modelId/classifiers',
        ),
        _apiClient.dio.get<List<dynamic>>(
          '/api/uml-models/$modelId/associations',
        ),
        _apiClient.dio.get<List<dynamic>>(
          '/api/uml-models/$modelId/generalizations',
        ),
        _apiClient.dio.get<List<dynamic>>(
          '/api/uml-models/$modelId/realizations',
        ),
      ]);
      final diagram = results[0].data as Map<String, dynamic>;
      final classifierJson = results[1].data as List<dynamic>;
      final classifierNames = <String, String>{
        for (final item in classifierJson)
          (item as Map<String, dynamic>)['id'] as String:
              item['name'] as String? ?? 'Clase',
      };
      final propertyResults = await Future.wait(
        classifierJson.map((item) {
          final classifier = item as Map<String, dynamic>;
          return _apiClient.dio.get<List<dynamic>>(
            '/api/uml-models/$modelId/classifiers/${classifier['id']}/properties',
          );
        }),
      );
      final operationResults = await Future.wait(
        classifierJson.map((item) {
          final classifier = item as Map<String, dynamic>;
          return _apiClient.dio.get<List<dynamic>>(
            '/api/uml-models/$modelId/classifiers/${classifier['id']}/operations',
          );
        }),
      );

      final classes = <DiagramClass>[];
      for (var index = 0; index < classifierJson.length; index++) {
        final classifier = classifierJson[index] as Map<String, dynamic>;
        final properties = (propertyResults[index].data ?? const [])
            .map(
              (item) => UmlProperty.fromJson(
                item as Map<String, dynamic>,
                classifierNames: classifierNames,
              ),
            )
            .toList();
        final operationJson = (operationResults[index].data ?? const [])
            .cast<Map<String, dynamic>>();
        final operations = await Future.wait(
          operationJson.map((operation) async {
            final parameterResponse = await _apiClient.dio.get<List<dynamic>>(
              '/api/uml-models/$modelId/operations/${operation['id']}/parameters',
            );
            final parameters = (parameterResponse.data ?? const [])
                .map(
                  (item) => UmlParameter.fromJson(
                    item as Map<String, dynamic>,
                    classifierNames: classifierNames,
                  ),
                )
                .toList();
            return UmlOperation.fromJson(operation, parameters: parameters);
          }),
        );
        classes.add(
          DiagramClass.fromJson(
            classifier,
            properties: properties,
            operations: operations,
          ),
        );
      }

      return DiagramObservation(
        id: diagram['id'] as String,
        modelId: diagram['modelId'] as String,
        name: diagram['name'] as String? ?? 'Diagrama',
        revision: (diagram['revision'] as num?)?.toInt() ?? 0,
        nodes: (diagram['nodes'] as List<dynamic>? ?? const [])
            .map((item) => DiagramNode.fromJson(item as Map<String, dynamic>))
            .toList(),
        edges: (diagram['edges'] as List<dynamic>? ?? const [])
            .map((item) => DiagramEdge.fromJson(item as Map<String, dynamic>))
            .toList(),
        classes: classes,
        associations: (results[2].data as List<dynamic>? ?? const [])
            .map(
              (item) => UmlAssociation.fromJson(item as Map<String, dynamic>),
            )
            .toList(),
        generalizations: (results[3].data as List<dynamic>? ?? const [])
            .map(
              (item) =>
                  UmlGeneralization.fromJson(item as Map<String, dynamic>),
            )
            .toList(),
        realizations: (results[4].data as List<dynamic>? ?? const [])
            .map(
              (item) => UmlRealization.fromJson(item as Map<String, dynamic>),
            )
            .toList(),
      );
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  Future<AiSessionSummary?> currentAiSession(String projectId) async {
    try {
      final response = await _apiClient.dio.get<dynamic>(
        '/api/projects/$projectId/ai/sessions/current',
      );
      final data = response.data;
      if (data == null) return null;
      final json = data as Map<String, dynamic>;
      return AiSessionSummary(
        id: json['id'] as String,
        status: json['status'] as String? ?? 'ACTIVE',
      );
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  Future<List<AiChatMessage>> loadAiMessages(
    String projectId,
    String sessionId,
  ) async {
    try {
      final response = await _apiClient.dio.get<Map<String, dynamic>>(
        '/api/projects/$projectId/ai/sessions/$sessionId/messages',
        queryParameters: const {'page': 0, 'size': 50},
      );
      return (response.data?['content'] as List<dynamic>? ?? const [])
          .map((item) => AiChatMessage.fromJson(item as Map<String, dynamic>))
          .toList();
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }
}

class AiSessionSummary {
  const AiSessionSummary({required this.id, required this.status});

  final String id;
  final String status;
}
