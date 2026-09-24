class UmlModelSummary {
  const UmlModelSummary({
    required this.id,
    required this.projectId,
    required this.name,
    required this.revision,
    required this.status,
  });

  factory UmlModelSummary.fromJson(Map<String, dynamic> json) =>
      UmlModelSummary(
        id: json['id'] as String,
        projectId: json['projectId'] as String,
        name: json['name'] as String,
        revision: (json['revision'] as num?)?.toInt() ?? 0,
        status: (json['status'] as String?) ?? 'EDITABLE',
      );

  final String id;
  final String projectId;
  final String name;
  final int revision;
  final String status;
}
