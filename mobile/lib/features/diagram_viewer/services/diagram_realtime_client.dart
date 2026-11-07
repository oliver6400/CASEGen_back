import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

import '../../../core/config/api_config.dart';
import '../models/diagram_observation.dart';

class DiagramRealtimeClient {
  StompClient? _client;
  Timer? _presenceHeartbeat;
  String? _aiTopicProjectId;
  String? _subscribedAiProjectId;
  VoidCallback? _onAiMessage;

  void connect({
    required String accessToken,
    required String modelId,
    required String diagramId,
    required ValueChanged<List<PresenceParticipant>> onPresence,
    required VoidCallback onDiagramChanged,
    required ValueChanged<bool> onConnectionChanged,
    required ValueChanged<String> onError,
  }) {
    disconnect();
    _aiTopicProjectId = null;
    _subscribedAiProjectId = null;
    _onAiMessage = null;
    late final StompClient client;
    client = StompClient(
      config: StompConfig(
        url: _webSocketUrl(),
        stompConnectHeaders: {'Authorization': 'Bearer $accessToken'},
        reconnectDelay: const Duration(seconds: 3),
        heartbeatIncoming: const Duration(seconds: 10),
        heartbeatOutgoing: const Duration(seconds: 10),
        onConnect: (_) {
          onConnectionChanged(true);
          client.subscribe(
            destination: '/topic/diagrams/$diagramId/presence',
            callback: (frame) {
              try {
                final body = jsonDecode(frame.body ?? '');
                final participants =
                    (body['participants'] as List<dynamic>? ?? const [])
                        .map(
                          (item) => PresenceParticipant.fromJson(
                            item as Map<String, dynamic>,
                          ),
                        )
                        .toList();
                onPresence(participants);
              } on FormatException catch (error) {
                onError('No se pudo leer la presencia: ${error.message}');
              } on TypeError catch (error) {
                onError('Formato de presencia inválido: $error');
              }
            },
          );
          client.subscribe(
            destination: '/topic/models/$modelId',
            callback: (_) => onDiagramChanged(),
          );
          client.subscribe(
            destination: '/topic/diagrams/$diagramId',
            callback: (_) => onDiagramChanged(),
          );
          _subscribedAiProjectId = null;
          _subscribeAiTopicIfReady();
          _publishViewing(diagramId);
          _presenceHeartbeat?.cancel();
          _presenceHeartbeat = Timer.periodic(
            const Duration(seconds: 15),
            (_) => _publishViewing(diagramId),
          );
        },
        onWebSocketError: (error) {
          onConnectionChanged(false);
          onError('Conexión en tiempo real no disponible: $error');
        },
        onStompError: (frame) {
          onConnectionChanged(false);
          onError(
            frame.body ?? 'El servidor rechazó la conexión en tiempo real.',
          );
        },
        onWebSocketDone: () {
          onConnectionChanged(false);
          _presenceHeartbeat?.cancel();
          _subscribedAiProjectId = null;
        },
      ),
    );
    _client = client;
    client.activate();
  }

  void disconnect() {
    _presenceHeartbeat?.cancel();
    _presenceHeartbeat = null;
    final client = _client;
    _client = null;
    _aiTopicProjectId = null;
    _subscribedAiProjectId = null;
    _onAiMessage = null;
    client?.deactivate();
  }

  void subscribeAiTopic(String projectId, VoidCallback onMessage) {
    _aiTopicProjectId = projectId;
    _onAiMessage = onMessage;
    _subscribeAiTopicIfReady();
  }

  void _subscribeAiTopicIfReady() {
    final client = _client;
    final projectId = _aiTopicProjectId;
    final onMessage = _onAiMessage;
    if (client == null ||
        !client.connected ||
        projectId == null ||
        onMessage == null ||
        _subscribedAiProjectId == projectId) {
      return;
    }
    client.subscribe(
      destination: '/topic/projects/$projectId/ai',
      callback: (_) => onMessage(),
    );
    _subscribedAiProjectId = projectId;
  }

  void _publishViewing(String diagramId) {
    final client = _client;
    if (client == null || !client.connected) return;
    client.send(
      destination: '/app/diagrams/$diagramId/presence',
      body: jsonEncode({'selectedElementId': null, 'activity': 'VIEWING'}),
    );
  }

  String _webSocketUrl() {
    final apiUri = Uri.parse(ApiConfig.baseUrl);
    final scheme = apiUri.scheme == 'https' ? 'wss' : 'ws';
    final basePath = apiUri.path.replaceFirst(RegExp(r'/+$'), '');
    return apiUri
        .replace(
          scheme: scheme,
          path: '$basePath/ws',
          query: null,
          fragment: null,
        )
        .toString();
  }
}
