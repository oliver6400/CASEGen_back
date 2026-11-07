import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../core/network/api_exception.dart';
import '../../core/storage/local_database.dart';
import '../../core/storage/token_storage.dart';
import 'models/diagram_observation.dart';
import 'services/diagram_realtime_client.dart';
import 'services/observation_service.dart';

class DiagramSummary {
  const DiagramSummary({required this.id, required this.name});

  final String id;
  final String name;
}

class DiagramViewerController extends ChangeNotifier {
  DiagramViewerController(this._service, this._tokenStorage);

  final ObservationService _service;
  final TokenStorage _tokenStorage;
  final DiagramRealtimeClient _realtime = DiagramRealtimeClient();

  List<DiagramSummary> diagrams = const [];
  DiagramSummary? selectedDiagram;
  DiagramObservation? observation;
  List<PresenceParticipant> participants = const [];
  AiSessionSummary? aiSession;
  List<AiChatMessage> aiMessages = const [];
  bool isLoadingDiagrams = false;
  bool isLoadingDiagram = false;
  bool isLoadingAi = false;
  bool canAccessAi = false;
  bool isRealtimeConnected = false;
  bool isOfflineObservation = false;
  String? errorMessage;
  String? aiErrorMessage;
  String? realtimeErrorMessage;
  String? _projectId;
  String? _modelId;
  int _diagramListGeneration = 0;
  int _diagramRequestGeneration = 0;
  int _aiLoadGeneration = 0;
  bool _disposed = false;

  Future<void> loadDiagrams({
    required String projectId,
    required String modelId,
  }) async {
    final generation = ++_diagramListGeneration;
    _projectId = projectId;
    _modelId = modelId;
    isLoadingDiagrams = true;
    errorMessage = null;
    aiErrorMessage = null;
    aiSession = null;
    aiMessages = const [];
    canAccessAi = false;
    isLoadingAi = false;
    observation = null;
    selectedDiagram = null;
    diagrams = const [];
    isLoadingDiagram = false;
    _stopRealtime();
    notifyListeners();
    try {
      List<DiagramSummary> loadedDiagrams;
      try {
        final response = await _service.listDiagrams(modelId);
        loadedDiagrams = response
            .map(
              (item) => DiagramSummary(
                id: item['id'] as String,
                name: item['name'] as String? ?? 'Diagrama',
              ),
            )
            .toList();
        await LocalDatabase.instance.saveDiagrams(
          modelId,
          loadedDiagrams.map((d) => {'id': d.id, 'name': d.name}).toList(),
        );
      } catch (_) {
        final cached = await LocalDatabase.instance.getCachedDiagrams(modelId);
        if (cached.isNotEmpty) {
          loadedDiagrams = cached
              .map((d) => DiagramSummary(id: d['id']!, name: d['name']!))
              .toList();
        } else {
          rethrow;
        }
      }
      if (_disposed || generation != _diagramListGeneration) return;
      diagrams = loadedDiagrams;
      if (diagrams.isNotEmpty) {
        await selectDiagram(diagrams.first);
      }
    } on ApiException catch (error) {
      if (_disposed || generation != _diagramListGeneration) return;
      if (diagrams.isEmpty) {
        errorMessage = error.message;
      }
    } finally {
      if (!_disposed && generation == _diagramListGeneration) {
        isLoadingDiagrams = false;
        notifyListeners();
      }
    }
  }

  void clearWorkspace() {
    ++_diagramListGeneration;
    ++_diagramRequestGeneration;
    ++_aiLoadGeneration;
    _projectId = null;
    _modelId = null;
    diagrams = const [];
    selectedDiagram = null;
    observation = null;
    participants = const [];
    aiSession = null;
    aiMessages = const [];
    isLoadingDiagrams = false;
    isLoadingDiagram = false;
    isLoadingAi = false;
    canAccessAi = false;
    isOfflineObservation = false;
    errorMessage = null;
    aiErrorMessage = null;
    _stopRealtime();
    notifyListeners();
  }

  Future<void> selectDiagram(DiagramSummary? diagram) async {
    ++_diagramRequestGeneration;
    ++_aiLoadGeneration;
    selectedDiagram = diagram;
    observation = null;
    isLoadingDiagram = false;
    participants = const [];
    _stopRealtime();
    aiSession = null;
    aiMessages = const [];
    aiErrorMessage = null;
    canAccessAi = false;
    isLoadingAi = false;
    notifyListeners();
    if (diagram == null || _modelId == null || _projectId == null) return;
    await reloadDiagram();
    await _connectRealtime(diagram);
    unawaited(loadAiConversation());
  }

  Future<void> refreshAiConversation() async {
    await loadAiConversation();
  }

  Future<void> reloadDiagram() async {
    final diagram = selectedDiagram;
    final modelId = _modelId;
    if (diagram == null || modelId == null) return;
    final selectedId = diagram.id;
    final generation = ++_diagramRequestGeneration;
    isLoadingDiagram = true;
    errorMessage = null;
    notifyListeners();
    try {
      DiagramObservation? result;
      try {
        result = await _service.loadDiagram(modelId, diagram.id);
        await LocalDatabase.instance.saveObservation(result);
        isOfflineObservation = false;
      } catch (_) {
        result = await LocalDatabase.instance.getCachedObservation(diagram.id);
        if (result != null) {
          isOfflineObservation = true;
        } else {
          rethrow;
        }
      }
      if (_disposed ||
          selectedDiagram?.id != selectedId ||
          generation != _diagramRequestGeneration) {
        return;
      }
      observation = result;
    } on ApiException catch (error) {
      if (_disposed ||
          selectedDiagram?.id != selectedId ||
          generation != _diagramRequestGeneration) {
        return;
      }
      if (observation == null) {
        errorMessage = error.message;
      }
    } finally {
      if (!_disposed &&
          selectedDiagram?.id == selectedId &&
          generation == _diagramRequestGeneration) {
        isLoadingDiagram = false;
        notifyListeners();
      }
    }
  }

  Future<void> loadAiConversation() async {
    final projectId = _projectId;
    if (projectId == null) return;
    final generation = ++_aiLoadGeneration;
    isLoadingAi = true;
    aiErrorMessage = null;
    notifyListeners();
    try {
      final session = await _service.currentAiSession(projectId);
      if (_disposed || generation != _aiLoadGeneration) return;
      canAccessAi = true;
      aiSession = session;
      aiMessages = session == null
          ? const []
          : await _service.loadAiMessages(projectId, session.id);
      if (_disposed || generation != _aiLoadGeneration) return;
      final diagram = selectedDiagram;
      if (diagram != null) {
        _realtime.subscribeAiTopic(projectId, () {
          if (_projectId == projectId && selectedDiagram?.id == diagram.id) {
            unawaited(loadAiConversation());
          }
        });
      }
    } on ApiException catch (error) {
      if (_disposed || generation != _aiLoadGeneration) return;
      canAccessAi = false;
      aiErrorMessage = error.message;
    } finally {
      if (!_disposed && generation == _aiLoadGeneration) {
        isLoadingAi = false;
        notifyListeners();
      }
    }
  }

  Future<void> _connectRealtime(DiagramSummary diagram) async {
    final token = await _tokenStorage.readAccessToken();
    final modelId = _modelId;
    if (_disposed || token == null || modelId == null || selectedDiagram?.id != diagram.id) {
      return;
    }
    realtimeErrorMessage = null;
    isRealtimeConnected = false;
    notifyListeners();
    _realtime.connect(
      accessToken: token,
      modelId: modelId,
      diagramId: diagram.id,
      onConnectionChanged: (connected) {
        if (_disposed || selectedDiagram?.id != diagram.id) return;
        isRealtimeConnected = connected;
        if (connected) realtimeErrorMessage = null;
        notifyListeners();
      },
      onError: (message) {
        if (_disposed || selectedDiagram?.id != diagram.id) return;
        isRealtimeConnected = false;
        realtimeErrorMessage = message;
        notifyListeners();
      },
      onDiagramChanged: () {
        if (_disposed || selectedDiagram?.id != diagram.id) return;
        unawaited(reloadDiagram());
      },
      onPresence: (updated) {
        if (_disposed || selectedDiagram?.id != diagram.id) return;
        participants = updated;
        notifyListeners();
      },
    );
  }

  void _stopRealtime() {
    isRealtimeConnected = false;
    realtimeErrorMessage = null;
    participants = const [];
    _realtime.disconnect();
  }

  @override
  void dispose() {
    _disposed = true;
    _stopRealtime();
    super.dispose();
  }
}
