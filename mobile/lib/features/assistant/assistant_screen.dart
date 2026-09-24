import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../models/project_summary.dart';
import '../../models/uml_model_summary.dart';
import '../../widgets/connectivity_badge.dart';
import '../../widgets/message_bubble.dart';
import '../auth/auth_controller.dart';
import '../offline/connectivity_controller.dart';
import '../projects/workspace_controller.dart';
import 'assistant_controller.dart';
import 'services/speech_input_buffer.dart';
import 'services/speech_service.dart';

class AssistantScreen extends StatefulWidget {
  const AssistantScreen({super.key});

  @override
  State<AssistantScreen> createState() => _AssistantScreenState();
}

class _AssistantScreenState extends State<AssistantScreen>
    with WidgetsBindingObserver {
  final _inputController = TextEditingController();
  final _scrollController = ScrollController();
  final _speechInputBuffer = SpeechInputBuffer();
  late final SpeechService _speechService;
  String _lastSpeechText = '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _speechService = SpeechService()..addListener(_onSpeechChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<WorkspaceController>().load();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached) {
      unawaited(_cancelSpeech());
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _speechService.removeListener(_onSpeechChanged);
    _speechService.dispose();
    _inputController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _toggleSpeech() async {
    FocusScope.of(context).unfocus();
    if (_speechService.isListening) {
      await _speechService.stopListening();
      return;
    }
    if (_speechService.status == SpeechServiceStatus.processing) return;

    _speechInputBuffer.begin(_inputController.text);
    _lastSpeechText = '';
    final started = await _speechService.startListening();
    if (!started && !_speechInputBuffer.receivedSpeech && mounted) {
      _setInputText(_speechInputBuffer.textBeforeDictation);
    }
  }

  Future<void> _cancelSpeech() async {
    if (!_speechService.isListening &&
        _speechService.status != SpeechServiceStatus.processing) {
      return;
    }
    await _speechService.cancelListening();
    if (!_speechInputBuffer.receivedSpeech && mounted) {
      _setInputText(_speechInputBuffer.textBeforeDictation);
    }
  }

  void _onSpeechChanged() {
    if (!mounted) return;
    final recognized = _speechService.recognizedText;
    if (recognized.isNotEmpty && recognized != _lastSpeechText) {
      _lastSpeechText = recognized;
      _setInputText(_speechInputBuffer.applyRecognition(recognized));
    }

    final message = _speechService.message;
    if (message != null) {
      if (!_speechInputBuffer.receivedSpeech) {
        _setInputText(_speechInputBuffer.textBeforeDictation);
      }
      _speechService.clearMessage();
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(message)));
      });
    }
    setState(() {});
  }

  void _setInputText(String text) {
    _inputController.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
  }

  Future<void> _send() async {
    final text = _inputController.text;
    if (text.trim().isEmpty) return;
    _inputController.clear();
    final isOffline = !context.read<ConnectivityController>().isOnline;
    await context.read<AssistantController>().submit(
      text,
      isOffline: isOffline,
    );
    if (!mounted) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  String? get _speechStatusText => switch (_speechService.status) {
    SpeechServiceStatus.listening => 'Escuchando...',
    SpeechServiceStatus.processing => 'Procesando voz...',
    _ => null,
  };

  @override
  Widget build(BuildContext context) {
    final workspace = context.watch<WorkspaceController>();
    final connectivity = context.watch<ConnectivityController>();
    final assistant = context.watch<AssistantController>();
    return Scaffold(
      appBar: AppBar(
        title: const Text('CASEGen Mobile'),
        actions: [
          IconButton(
            tooltip: 'Cerrar sesión',
            onPressed: () async {
              await context.read<WorkspaceController>().reset();
              if (!context.mounted) return;
              await context.read<AuthController>().logout();
            },
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
              child: Row(
                children: [
                  ConnectivityBadge(isOnline: connectivity.isOnline),
                  const Spacer(),
                  if (workspace.isLoading)
                    const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    children: [
                      DropdownButtonFormField<ProjectSummary>(
                        key: ValueKey(workspace.selectedProject?.id),
                        initialValue: workspace.selectedProject,
                        decoration: const InputDecoration(
                          labelText: 'Proyecto',
                          prefixIcon: Icon(Icons.folder_outlined),
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
                        onChanged: workspace.isLoading
                            ? null
                            : workspace.selectProject,
                      ),
                      const SizedBox(height: 10),
                      DropdownButtonFormField<UmlModelSummary>(
                        key: ValueKey(
                          '${workspace.selectedProject?.id}-${workspace.selectedModel?.id}',
                        ),
                        initialValue: workspace.selectedModel,
                        decoration: const InputDecoration(
                          labelText: 'Modelo UML',
                          prefixIcon: Icon(Icons.account_tree_outlined),
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
                        onChanged: workspace.isLoading
                            ? null
                            : workspace.selectModel,
                      ),
                      if (workspace.errorMessage != null) ...[
                        const SizedBox(height: 10),
                        Row(
                          children: [
                            Icon(
                              Icons.error_outline,
                              color: Theme.of(context).colorScheme.error,
                            ),
                            const SizedBox(width: 8),
                            Expanded(child: Text(workspace.errorMessage!)),
                            IconButton(
                              tooltip: 'Reintentar',
                              onPressed: workspace.load,
                              icon: const Icon(Icons.refresh),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
            Expanded(
              child: ListView.builder(
                controller: _scrollController,
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 8,
                ),
                itemCount: assistant.messages.length,
                itemBuilder: (context, index) =>
                    MessageBubble(message: assistant.messages[index]),
              ),
            ),
            Container(
              color: Colors.white,
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  AnimatedSwitcher(
                    duration: const Duration(milliseconds: 180),
                    child: _speechStatusText == null
                        ? const SizedBox.shrink()
                        : Padding(
                            key: ValueKey(_speechStatusText),
                            padding: const EdgeInsets.only(bottom: 8, left: 4),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                if (_speechService.isListening)
                                  Container(
                                    width: 8,
                                    height: 8,
                                    margin: const EdgeInsets.only(right: 7),
                                    decoration: const BoxDecoration(
                                      color: Color(0xFFDC2626),
                                      shape: BoxShape.circle,
                                    ),
                                  )
                                else
                                  const Padding(
                                    padding: EdgeInsets.only(right: 7),
                                    child: SizedBox.square(
                                      dimension: 12,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                      ),
                                    ),
                                  ),
                                Text(
                                  _speechStatusText!,
                                  style: const TextStyle(
                                    fontSize: 12,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ],
                            ),
                          ),
                  ),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      IconButton.filledTonal(
                        tooltip: _speechService.isListening
                            ? 'Detener dictado'
                            : 'Dictar instrucción',
                        onPressed:
                            _speechService.status ==
                                SpeechServiceStatus.processing
                            ? null
                            : _toggleSpeech,
                        style: _speechService.isListening
                            ? IconButton.styleFrom(
                                backgroundColor: const Color(0xFFFEE2E2),
                                foregroundColor: const Color(0xFFB91C1C),
                              )
                            : null,
                        icon: Icon(
                          _speechService.isListening
                              ? Icons.mic
                              : Icons.mic_none,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: TextField(
                          controller: _inputController,
                          minLines: 1,
                          maxLines: 4,
                          textInputAction: TextInputAction.send,
                          onSubmitted: (_) => _send(),
                          decoration: const InputDecoration(
                            hintText: 'Escribe una instrucción...',
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      IconButton.filled(
                        tooltip: 'Enviar',
                        onPressed:
                            _speechService.status ==
                                    SpeechServiceStatus.listening ||
                                _speechService.status ==
                                    SpeechServiceStatus.processing
                            ? null
                            : _send,
                        icon: const Icon(Icons.send_rounded),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
