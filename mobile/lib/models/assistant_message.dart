enum AssistantRole { user, assistant, system }

enum AssistantMessageStatus { sent, recognized, notUnderstood, error }

class AssistantMessage {
  const AssistantMessage({
    required this.id,
    required this.role,
    required this.text,
    required this.createdAt,
    required this.status,
    required this.isOffline,
  });

  factory AssistantMessage.fromDatabaseMap(Map<String, Object?> map) =>
      AssistantMessage(
        id: map['id']! as String,
        role: AssistantRole.values.byName(map['role']! as String),
        text: map['text']! as String,
        createdAt: DateTime.parse(map['created_at']! as String),
        status: AssistantMessageStatus.values.byName(map['status']! as String),
        isOffline: (map['is_offline']! as int) == 1,
      );

  final String id;
  final AssistantRole role;
  final String text;
  final DateTime createdAt;
  final AssistantMessageStatus status;
  final bool isOffline;

  Map<String, Object?> toDatabaseMap() => {
    'id': id,
    'role': role.name,
    'text': text,
    'created_at': createdAt.toIso8601String(),
    'status': status.name,
    'is_offline': isOffline ? 1 : 0,
  };
}
