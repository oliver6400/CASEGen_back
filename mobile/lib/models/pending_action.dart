class PendingAction {
  const PendingAction({
    required this.id,
    required this.intentType,
    required this.payload,
    required this.createdAt,
    required this.status,
    this.projectId,
    this.modelId,
  });

  final String id;
  final String? projectId;
  final String? modelId;
  final String intentType;
  final Map<String, Object?> payload;
  final DateTime createdAt;
  final String status;
}
