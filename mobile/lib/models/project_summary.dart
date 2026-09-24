class ProjectSummary {
  const ProjectSummary({
    required this.id,
    required this.name,
    required this.visibility,
    this.description,
  });

  factory ProjectSummary.fromJson(Map<String, dynamic> json) => ProjectSummary(
    id: json['id'] as String,
    name: json['name'] as String,
    description: json['description'] as String?,
    visibility: (json['visibility'] as String?) ?? 'PRIVATE',
  );

  final String id;
  final String name;
  final String? description;
  final String visibility;
}
