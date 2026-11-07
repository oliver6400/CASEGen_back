import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../models/diagram_observation.dart';

class DiagramCanvasGeometry {
  const DiagramCanvasGeometry({
    required this.originX,
    required this.originY,
    required this.width,
    required this.height,
  });

  final double originX;
  final double originY;
  final double width;
  final double height;

  factory DiagramCanvasGeometry.from(DiagramObservation observation) {
    if (observation.nodes.isEmpty) {
      return const DiagramCanvasGeometry(
        originX: 0,
        originY: 0,
        width: 720,
        height: 480,
      );
    }
    final left = observation.nodes
        .map((node) => node.x)
        .reduce((a, b) => a < b ? a : b);
    final top = observation.nodes
        .map((node) => node.y)
        .reduce((a, b) => a < b ? a : b);
    final right = observation.nodes
        .map((node) => node.x + math.max(220, node.width))
        .reduce((a, b) => a > b ? a : b);
    final bottom = observation.nodes
        .map((node) => node.y + math.max(128, node.height))
        .reduce((a, b) => a > b ? a : b);
    const margin = 48.0;
    return DiagramCanvasGeometry(
      originX: margin - left,
      originY: margin - top,
      width: right - left + margin * 2,
      height: bottom - top + margin * 2,
    );
  }
}

class DiagramCanvas extends StatelessWidget {
  const DiagramCanvas({
    required this.observation,
    required this.geometry,
    required this.participants,
    required this.onClassTap,
    super.key,
  });

  final DiagramObservation observation;
  final DiagramCanvasGeometry geometry;
  final List<PresenceParticipant> participants;
  final ValueChanged<DiagramClass> onClassTap;

  @override
  Widget build(BuildContext context) {
    final nodesById = {for (final node in observation.nodes) node.id: node};
    final presenceByClass = <String, List<PresenceParticipant>>{};
    for (final participant in participants) {
      final selectedId = participant.selectedElementId;
      if (participant.activity == 'EDITING' && selectedId != null) {
        presenceByClass.putIfAbsent(selectedId, () => []).add(participant);
      }
    }
    final sortedNodes = [...observation.nodes]
      ..sort((left, right) => left.zIndex.compareTo(right.zIndex));

    return SizedBox(
      width: geometry.width,
      height: geometry.height,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Positioned.fill(
            child: CustomPaint(
              painter: _RelationshipPainter(
                observation: observation,
                geometry: geometry,
                nodesById: nodesById,
              ),
            ),
          ),
          for (final node in sortedNodes)
            if (observation.classFor(node.umlElementId) case final modelClass?)
              Positioned(
                left: node.x + geometry.originX,
                top: node.y + geometry.originY,
                width: math.max(220, node.width),
                height: math.max(128, node.height),
                child: _ClassNode(
                  modelClass: modelClass,
                  editors: presenceByClass[modelClass.id] ?? const [],
                  onTap: () => onClassTap(modelClass),
                ),
              ),
        ],
      ),
    );
  }
}

class _ClassNode extends StatelessWidget {
  const _ClassNode({
    required this.modelClass,
    required this.editors,
    required this.onTap,
  });

  final DiagramClass modelClass;
  final List<PresenceParticipant> editors;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final isHighlighted = editors.isNotEmpty;
    final visibility = umlVisibilitySymbol(modelClass.visibility);
    final editorColor = editors.isEmpty
        ? null
        : _presenceColor(editors.first.userId);
    return Material(
      color: Colors.white,
      elevation: 3,
      shadowColor: colorScheme.shadow.withValues(alpha: 0.15),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(10),
        side: BorderSide(
          color: editorColor ?? const Color(0xFFCDD4E2),
          width: isHighlighted ? 3 : 1,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              color: const Color(0xFFF0F2FB),
              padding: const EdgeInsets.fromLTRB(10, 7, 10, 7),
              child: Column(
                children: [
                  Text(
                    modelClass.kind == 'INTERFACE' ? '«interface»' : '«class»',
                    style: const TextStyle(fontSize: 10, color: Colors.black54),
                  ),
                  Text(
                    '$visibility${modelClass.name}${modelClass.isAbstract ? ' (abstract)' : ''}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 13,
                    ),
                  ),
                  if (editors.isNotEmpty)
                    Text(
                      '${editors.first.displayName} editando',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: editorColor,
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                ],
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: _memberPreview(
                modelClass.properties.take(5).map(_formatProperty).toList(),
                modelClass.operations.take(4).map(_formatOperation).toList(),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _memberPreview(List<String> properties, List<String> operations) {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final property in properties)
            Text(
              property,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 10),
            ),
          if (properties.isNotEmpty && operations.isNotEmpty)
            const Divider(height: 7),
          for (final operation in operations)
            Text(
              operation,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 10),
            ),
          if (properties.isEmpty && operations.isEmpty)
            const Text(
              'Sin atributos ni operaciones',
              style: TextStyle(fontSize: 10, color: Colors.black54),
            ),
        ],
      ),
    );
  }
}

class _RelationshipPainter extends CustomPainter {
  _RelationshipPainter({
    required this.observation,
    required this.geometry,
    required this.nodesById,
  });

  final DiagramObservation observation;
  final DiagramCanvasGeometry geometry;
  final Map<String, DiagramNode> nodesById;

  @override
  void paint(Canvas canvas, Size size) {
    for (final edge in observation.edges) {
      final sourceNode = nodesById[edge.sourceNodeId];
      final targetNode = nodesById[edge.targetNodeId];
      if (sourceNode == null || targetNode == null) continue;

      final points = <Offset>[
        _sidePoint(sourceNode, edge.sourceSide),
        ...edge.waypoints.map(
          (point) =>
              Offset(point.x + geometry.originX, point.y + geometry.originY),
        ),
        _sidePoint(targetNode, edge.targetSide),
      ];
      if (points.length < 2) continue;

      final association = _associationFor(edge.umlElementId);
      final isGeneralization = observation.generalizations.any(
        (item) => item.id == edge.umlElementId,
      );
      final isRealization = observation.realizations.any(
        (item) => item.id == edge.umlElementId,
      );
      final color = const Color(0xFF586174);
      final paint = Paint()
        ..color = color
        ..strokeWidth = 1.7
        ..style = PaintingStyle.stroke;
      _drawLine(canvas, points, paint, dashed: isRealization);

      if (isGeneralization || isRealization) {
        _drawTriangle(canvas, points[points.length - 2], points.last, paint);
      }

      if (association != null) {
        _drawAssociationEnd(
          canvas,
          points,
          association,
          sourceNode,
          targetNode,
          paint,
        );
        _drawMultiplicity(
          canvas,
          points.first,
          association.ends.firstWhere(
            (end) => end.classifierId == sourceNode.umlElementId,
            orElse: () => association.ends.first,
          ),
        );
        _drawMultiplicity(
          canvas,
          points.last,
          association.ends.firstWhere(
            (end) => end.classifierId == targetNode.umlElementId,
            orElse: () => association.ends.last,
          ),
        );
      }
    }
  }

  UmlAssociation? _associationFor(String id) {
    for (final association in observation.associations) {
      if (association.id == id) return association;
    }
    return null;
  }

  Offset _sidePoint(DiagramNode node, String side) {
    final left = node.x + geometry.originX;
    final top = node.y + geometry.originY;
    final width = math.max(220, node.width);
    final height = math.max(128, node.height);
    return switch (side) {
      'TOP' => Offset(left + width / 2, top),
      'BOTTOM' => Offset(left + width / 2, top + height),
      'LEFT' => Offset(left, top + height / 2),
      _ => Offset(left + width, top + height / 2),
    };
  }

  void _drawLine(
    Canvas canvas,
    List<Offset> points,
    Paint paint, {
    required bool dashed,
  }) {
    final path = Path()..moveTo(points.first.dx, points.first.dy);
    for (final point in points.skip(1)) {
      path.lineTo(point.dx, point.dy);
    }
    if (!dashed) {
      canvas.drawPath(path, paint);
      return;
    }
    for (final metric in path.computeMetrics()) {
      var distance = 0.0;
      while (distance < metric.length) {
        final end = math.min(distance + 8, metric.length);
        canvas.drawPath(metric.extractPath(distance, end), paint);
        distance = end + 5;
      }
    }
  }

  void _drawTriangle(Canvas canvas, Offset beforeEnd, Offset end, Paint paint) {
    final direction = (end - beforeEnd);
    final length = direction.distance;
    if (length == 0) return;
    final unit = direction / length;
    final base = end - unit * 13;
    final normal = Offset(-unit.dy, unit.dx) * 7;
    final triangle = Path()
      ..moveTo(end.dx, end.dy)
      ..lineTo(base.dx + normal.dx, base.dy + normal.dy)
      ..lineTo(base.dx - normal.dx, base.dy - normal.dy)
      ..close();
    canvas.drawPath(triangle, paint);
  }

  void _drawAssociationEnd(
    Canvas canvas,
    List<Offset> points,
    UmlAssociation association,
    DiagramNode source,
    DiagramNode target,
    Paint paint,
  ) {
    for (final end in association.ends) {
      if (end.aggregation == 'NONE') continue;
      final isSource = end.classifierId == source.umlElementId;
      final point = isSource ? points.first : points.last;
      final neighbor = isSource ? points[1] : points[points.length - 2];
      final direction = neighbor - point;
      final length = direction.distance;
      if (length == 0) continue;
      final unit = direction / length;
      final normal = Offset(-unit.dy, unit.dx) * 6;
      final center = point + unit * 12;
      final far = center + unit * 10;
      final diamond = Path()
        ..moveTo(point.dx, point.dy)
        ..lineTo(center.dx + normal.dx, center.dy + normal.dy)
        ..lineTo(far.dx, far.dy)
        ..lineTo(center.dx - normal.dx, center.dy - normal.dy)
        ..close();
      final markerPaint = Paint()
        ..color = end.aggregation == 'COMPOSITE'
            ? const Color(0xFF586174)
            : Colors.white
        ..style = PaintingStyle.fill;
      canvas.drawPath(diamond, markerPaint);
      canvas.drawPath(diamond, paint);
    }
  }

  void _drawMultiplicity(Canvas canvas, Offset point, UmlAssociationEnd end) {
    final text = '${end.lowerBound}..${end.upperBound ?? '*'}';
    _drawLabel(canvas, Offset(point.dx + 6, point.dy + 4), text);
  }

  void _drawLabel(Canvas canvas, Offset point, String text) {
    final textPainter = TextPainter(
      text: TextSpan(
        text: text,
        style: const TextStyle(
          fontSize: 9,
          color: Color(0xFF475569),
          backgroundColor: Colors.white,
        ),
      ),
      textDirection: TextDirection.ltr,
      maxLines: 1,
    )..layout(maxWidth: 120);
    textPainter.paint(canvas, point);
  }

  @override
  bool shouldRepaint(covariant _RelationshipPainter oldDelegate) =>
      oldDelegate.observation != observation ||
      oldDelegate.geometry != geometry ||
      oldDelegate.observation.revision != observation.revision ||
      oldDelegate.observation.nodes != observation.nodes ||
      oldDelegate.observation.edges != observation.edges;
}

String _formatProperty(UmlProperty property) {
  final modifiers = [
    if (property.isStatic) 'static',
    if (property.isReadOnly) 'readOnly',
    if (property.isDerived) '/',
  ];
  return '${umlVisibilitySymbol(property.visibility)}${property.name}: ${property.type}'
      ' [${property.lowerBound}..${property.upperBound ?? '*'}]'
      '${modifiers.isEmpty ? '' : ' (${modifiers.join(', ')})'}';
}

String _formatOperation(UmlOperation operation) {
  final parameters = operation.parameters
      .where((parameter) => parameter.direction != 'RETURN')
      .map((parameter) => '${parameter.name}: ${parameter.type}')
      .join(', ');
  final returnType = operation.parameters
      .where((parameter) => parameter.direction == 'RETURN')
      .firstOrNull
      ?.type;
  final modifiers = [
    if (operation.isStatic) 'static',
    if (operation.isAbstract) 'abstract',
  ];
  return '${umlVisibilitySymbol(operation.visibility)}${operation.name}($parameters)'
      '${returnType == null ? '' : ': $returnType'}'
      '${modifiers.isEmpty ? '' : ' (${modifiers.join(', ')})'}';
}

Color _presenceColor(String userId) {
  const colors = [
    Color(0xFF15803D),
    Color(0xFF2563EB),
    Color(0xFF9333EA),
    Color(0xFFEA580C),
    Color(0xFFDB2777),
    Color(0xFF0891B2),
  ];
  final hash = userId.codeUnits.fold<int>(0, (value, unit) => value + unit);
  return colors[hash % colors.length];
}
