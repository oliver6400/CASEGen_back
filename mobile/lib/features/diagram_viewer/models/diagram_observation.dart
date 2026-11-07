class DiagramObservation {
  const DiagramObservation({
    required this.id,
    required this.modelId,
    required this.name,
    required this.revision,
    required this.nodes,
    required this.edges,
    required this.classes,
    required this.associations,
    required this.generalizations,
    required this.realizations,
  });

  final String id;
  final String modelId;
  final String name;
  final int revision;
  final List<DiagramNode> nodes;
  final List<DiagramEdge> edges;
  final List<DiagramClass> classes;
  final List<UmlAssociation> associations;
  final List<UmlGeneralization> generalizations;
  final List<UmlRealization> realizations;

  DiagramClass? classFor(String id) {
    for (final item in classes) {
      if (item.id == id) return item;
    }
    return null;
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'modelId': modelId,
    'name': name,
    'revision': revision,
    'nodes': nodes.map((n) => n.toJson()).toList(),
    'edges': edges.map((e) => e.toJson()).toList(),
    'classes': classes.map((c) => c.toJson()).toList(),
    'associations': associations.map((a) => a.toJson()).toList(),
    'generalizations': generalizations.map((g) => g.toJson()).toList(),
    'realizations': realizations.map((r) => r.toJson()).toList(),
  };

  factory DiagramObservation.fromJson(Map<String, dynamic> json) =>
      DiagramObservation(
        id: json['id'] as String,
        modelId: json['modelId'] as String,
        name: json['name'] as String? ?? 'Diagrama',
        revision: (json['revision'] as num?)?.toInt() ?? 0,
        nodes: (json['nodes'] as List<dynamic>? ?? const [])
            .map((item) => DiagramNode.fromJson(item as Map<String, dynamic>))
            .toList(),
        edges: (json['edges'] as List<dynamic>? ?? const [])
            .map((item) => DiagramEdge.fromJson(item as Map<String, dynamic>))
            .toList(),
        classes: (json['classes'] as List<dynamic>? ?? const [])
            .map(
              (item) => DiagramClass.fromJson(
                item as Map<String, dynamic>,
                properties: (item['properties'] as List<dynamic>? ?? const [])
                    .map(
                      (p) =>
                          UmlProperty.fromStoredJson(p as Map<String, dynamic>),
                    )
                    .toList(),
                operations: (item['operations'] as List<dynamic>? ?? const [])
                    .map(
                      (o) => UmlOperation.fromStoredJson(
                        o as Map<String, dynamic>,
                      ),
                    )
                    .toList(),
              ),
            )
            .toList(),
        associations: (json['associations'] as List<dynamic>? ?? const [])
            .map(
              (item) => UmlAssociation.fromJson(item as Map<String, dynamic>),
            )
            .toList(),
        generalizations: (json['generalizations'] as List<dynamic>? ?? const [])
            .map(
              (item) =>
                  UmlGeneralization.fromJson(item as Map<String, dynamic>),
            )
            .toList(),
        realizations: (json['realizations'] as List<dynamic>? ?? const [])
            .map(
              (item) => UmlRealization.fromJson(item as Map<String, dynamic>),
            )
            .toList(),
      );
}

class DiagramNode {
  const DiagramNode({
    required this.id,
    required this.umlElementId,
    required this.x,
    required this.y,
    required this.width,
    required this.height,
    required this.zIndex,
  });

  final String id;
  final String umlElementId;
  final double x;
  final double y;
  final double width;
  final double height;
  final int zIndex;

  Map<String, dynamic> toJson() => {
    'id': id,
    'umlElementId': umlElementId,
    'x': x,
    'y': y,
    'width': width,
    'height': height,
    'zIndex': zIndex,
  };

  factory DiagramNode.fromJson(Map<String, dynamic> json) => DiagramNode(
    id: json['id'] as String,
    umlElementId: json['umlElementId'] as String,
    x: (json['x'] as num).toDouble(),
    y: (json['y'] as num).toDouble(),
    width: (json['width'] as num).toDouble(),
    height: (json['height'] as num).toDouble(),
    zIndex: (json['zIndex'] as num?)?.toInt() ?? 0,
  );
}

class DiagramEdge {
  const DiagramEdge({
    required this.id,
    required this.umlElementId,
    required this.sourceNodeId,
    required this.targetNodeId,
    required this.sourceSide,
    required this.targetSide,
    required this.waypoints,
  });

  final String id;
  final String umlElementId;
  final String sourceNodeId;
  final String targetNodeId;
  final String sourceSide;
  final String targetSide;
  final List<DiagramWaypoint> waypoints;

  Map<String, dynamic> toJson() => {
    'id': id,
    'umlElementId': umlElementId,
    'sourceNodeId': sourceNodeId,
    'targetNodeId': targetNodeId,
    'sourceSide': sourceSide,
    'targetSide': targetSide,
    'waypoints': waypoints.map((w) => w.toJson()).toList(),
  };

  factory DiagramEdge.fromJson(Map<String, dynamic> json) => DiagramEdge(
    id: json['id'] as String,
    umlElementId: json['umlElementId'] as String,
    sourceNodeId: json['sourceNodeId'] as String,
    targetNodeId: json['targetNodeId'] as String,
    sourceSide: json['sourceSide'] as String? ?? 'RIGHT',
    targetSide: json['targetSide'] as String? ?? 'LEFT',
    waypoints: (json['waypoints'] as List<dynamic>? ?? const [])
        .map((item) => DiagramWaypoint.fromJson(item as Map<String, dynamic>))
        .toList(),
  );
}

class DiagramWaypoint {
  const DiagramWaypoint(this.x, this.y);

  final double x;
  final double y;

  Map<String, dynamic> toJson() => {'x': x, 'y': y};

  factory DiagramWaypoint.fromJson(Map<String, dynamic> json) =>
      DiagramWaypoint(
        (json['x'] as num).toDouble(),
        (json['y'] as num).toDouble(),
      );
}

class DiagramClass {
  const DiagramClass({
    required this.id,
    required this.name,
    required this.kind,
    required this.visibility,
    required this.isAbstract,
    required this.properties,
    required this.operations,
  });

  final String id;
  final String name;
  final String kind;
  final String visibility;
  final bool isAbstract;
  final List<UmlProperty> properties;
  final List<UmlOperation> operations;

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'kind': kind,
    'visibility': visibility,
    'isAbstract': isAbstract,
    'properties': properties.map((p) => p.toJson()).toList(),
    'operations': operations.map((o) => o.toJson()).toList(),
  };

  factory DiagramClass.fromJson(
    Map<String, dynamic> json, {
    required List<UmlProperty> properties,
    required List<UmlOperation> operations,
  }) => DiagramClass(
    id: json['id'] as String,
    name: json['name'] as String? ?? 'Sin nombre',
    kind: json['kind'] as String? ?? 'CLASS',
    visibility: json['visibility'] as String? ?? 'PACKAGE',
    isAbstract: json['isAbstract'] as bool? ?? false,
    properties: properties,
    operations: operations,
  );
}

class UmlProperty {
  const UmlProperty({
    required this.name,
    required this.visibility,
    required this.type,
    required this.lowerBound,
    required this.upperBound,
    required this.isStatic,
    required this.isReadOnly,
    required this.isDerived,
  });

  final String name;
  final String visibility;
  final String type;
  final int lowerBound;
  final int? upperBound;
  final bool isStatic;
  final bool isReadOnly;
  final bool isDerived;

  Map<String, dynamic> toJson() => {
    'name': name,
    'visibility': visibility,
    'type': type,
    'lowerBound': lowerBound,
    'upperBound': upperBound,
    'isStatic': isStatic,
    'isReadOnly': isReadOnly,
    'isDerived': isDerived,
  };

  factory UmlProperty.fromJson(
    Map<String, dynamic> json, {
    required Map<String, String> classifierNames,
  }) => UmlProperty(
    name: json['name'] as String? ?? 'atributo',
    visibility: json['visibility'] as String? ?? 'PACKAGE',
    type: _readType(json, classifierNames),
    lowerBound: (json['lowerBound'] as num?)?.toInt() ?? 0,
    upperBound: (json['upperBound'] as num?)?.toInt(),
    isStatic: json['isStatic'] as bool? ?? false,
    isReadOnly: json['isReadOnly'] as bool? ?? false,
    isDerived: json['isDerived'] as bool? ?? false,
  );

  factory UmlProperty.fromStoredJson(Map<String, dynamic> json) => UmlProperty(
    name: json['name'] as String? ?? 'atributo',
    visibility: json['visibility'] as String? ?? 'PACKAGE',
    type: json['type'] as String? ?? 'Any',
    lowerBound: (json['lowerBound'] as num?)?.toInt() ?? 0,
    upperBound: (json['upperBound'] as num?)?.toInt(),
    isStatic: json['isStatic'] as bool? ?? false,
    isReadOnly: json['isReadOnly'] as bool? ?? false,
    isDerived: json['isDerived'] as bool? ?? false,
  );
}

class UmlOperation {
  const UmlOperation({
    required this.name,
    required this.visibility,
    required this.isAbstract,
    required this.isStatic,
    required this.parameters,
  });

  final String name;
  final String visibility;
  final bool isAbstract;
  final bool isStatic;
  final List<UmlParameter> parameters;

  Map<String, dynamic> toJson() => {
    'name': name,
    'visibility': visibility,
    'isAbstract': isAbstract,
    'isStatic': isStatic,
    'parameters': parameters.map((p) => p.toJson()).toList(),
  };

  factory UmlOperation.fromJson(
    Map<String, dynamic> json, {
    required List<UmlParameter> parameters,
  }) => UmlOperation(
    name: json['name'] as String? ?? 'operacion',
    visibility: json['visibility'] as String? ?? 'PACKAGE',
    isAbstract: json['isAbstract'] as bool? ?? false,
    isStatic: json['isStatic'] as bool? ?? false,
    parameters: parameters,
  );

  factory UmlOperation.fromStoredJson(Map<String, dynamic> json) =>
      UmlOperation(
        name: json['name'] as String? ?? 'operacion',
        visibility: json['visibility'] as String? ?? 'PACKAGE',
        isAbstract: json['isAbstract'] as bool? ?? false,
        isStatic: json['isStatic'] as bool? ?? false,
        parameters: (json['parameters'] as List<dynamic>? ?? const [])
            .map(
              (p) => UmlParameter.fromStoredJson(p as Map<String, dynamic>),
            )
            .toList(),
      );
}

class UmlParameter {
  const UmlParameter({
    required this.name,
    required this.type,
    required this.direction,
  });

  final String name;
  final String type;
  final String direction;

  Map<String, dynamic> toJson() => {
    'name': name,
    'type': type,
    'direction': direction,
  };

  factory UmlParameter.fromJson(
    Map<String, dynamic> json, {
    required Map<String, String> classifierNames,
  }) => UmlParameter(
    name: json['name'] as String? ?? 'parametro',
    type: _readType(json, classifierNames),
    direction: json['direction'] as String? ?? 'IN',
  );

  factory UmlParameter.fromStoredJson(Map<String, dynamic> json) =>
      UmlParameter(
        name: json['name'] as String? ?? 'parametro',
        type: json['type'] as String? ?? 'Any',
        direction: json['direction'] as String? ?? 'IN',
      );
}

String _readType(
  Map<String, dynamic> json,
  Map<String, String> classifierNames,
) {
  final classifierId = json['typeClassifierId'] as String?;
  if (classifierId != null) return classifierNames[classifierId] ?? 'Clase';
  return json['primitiveType'] as String? ?? 'Any';
}

String umlVisibilitySymbol(String value) => switch (value) {
  'PUBLIC' => '+',
  'PRIVATE' => '-',
  'PROTECTED' => '#',
  _ => '~',
};

class UmlAssociation {
  const UmlAssociation({required this.id, required this.ends});

  final String id;
  final List<UmlAssociationEnd> ends;

  Map<String, dynamic> toJson() => {
    'id': id,
    'ends': ends.map((e) => e.toJson()).toList(),
  };

  factory UmlAssociation.fromJson(Map<String, dynamic> json) => UmlAssociation(
    id: json['id'] as String,
    ends: (json['ends'] as List<dynamic>? ?? const [])
        .map((item) => UmlAssociationEnd.fromJson(item as Map<String, dynamic>))
        .toList(),
  );
}

class UmlAssociationEnd {
  const UmlAssociationEnd({
    required this.classifierId,
    required this.roleName,
    required this.lowerBound,
    required this.upperBound,
    required this.aggregation,
  });

  final String classifierId;
  final String? roleName;
  final int lowerBound;
  final int? upperBound;
  final String aggregation;

  Map<String, dynamic> toJson() => {
    'classifierId': classifierId,
    'roleName': roleName,
    'lowerBound': lowerBound,
    'upperBound': upperBound,
    'aggregation': aggregation,
  };

  factory UmlAssociationEnd.fromJson(Map<String, dynamic> json) =>
      UmlAssociationEnd(
        classifierId: json['classifierId'] as String,
        roleName: json['roleName'] as String?,
        lowerBound: (json['lowerBound'] as num?)?.toInt() ?? 0,
        upperBound: (json['upperBound'] as num?)?.toInt(),
        aggregation: json['aggregation'] as String? ?? 'NONE',
      );
}

class UmlGeneralization {
  const UmlGeneralization({
    required this.id,
    required this.specificClassifierId,
    required this.generalClassifierId,
  });

  final String id;
  final String specificClassifierId;
  final String generalClassifierId;

  Map<String, dynamic> toJson() => {
    'id': id,
    'specificClassifierId': specificClassifierId,
    'generalClassifierId': generalClassifierId,
  };

  factory UmlGeneralization.fromJson(Map<String, dynamic> json) =>
      UmlGeneralization(
        id: json['id'] as String,
        specificClassifierId: json['specificClassifierId'] as String,
        generalClassifierId: json['generalClassifierId'] as String,
      );
}

class UmlRealization {
  const UmlRealization({
    required this.id,
    required this.clientClassifierId,
    required this.supplierClassifierId,
  });

  final String id;
  final String clientClassifierId;
  final String supplierClassifierId;

  Map<String, dynamic> toJson() => {
    'id': id,
    'clientClassifierId': clientClassifierId,
    'supplierClassifierId': supplierClassifierId,
  };

  factory UmlRealization.fromJson(Map<String, dynamic> json) => UmlRealization(
    id: json['id'] as String,
    clientClassifierId: json['clientClassifierId'] as String,
    supplierClassifierId: json['supplierClassifierId'] as String,
  );
}

class PresenceParticipant {
  const PresenceParticipant({
    required this.userId,
    required this.displayName,
    required this.selectedElementId,
    required this.activity,
  });

  final String userId;
  final String displayName;
  final String? selectedElementId;
  final String activity;

  factory PresenceParticipant.fromJson(Map<String, dynamic> json) =>
      PresenceParticipant(
        userId: json['userId'] as String,
        displayName: json['displayName'] as String? ?? 'Colaborador',
        selectedElementId: json['selectedElementId'] as String?,
        activity: json['activity'] as String? ?? 'VIEWING',
      );
}

class AiChatMessage {
  const AiChatMessage({
    required this.id,
    required this.role,
    required this.authorName,
    required this.content,
    required this.createdAt,
  });

  final String id;
  final String role;
  final String? authorName;
  final String content;
  final DateTime? createdAt;

  factory AiChatMessage.fromJson(Map<String, dynamic> json) => AiChatMessage(
    id: json['id'] as String,
    role: json['role'] as String? ?? 'ASSISTANT',
    authorName: json['authorName'] as String?,
    content: json['content'] as String? ?? '',
    createdAt: DateTime.tryParse(json['createdAt'] as String? ?? ''),
  );
}
