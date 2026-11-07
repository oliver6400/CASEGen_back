import 'package:flutter_test/flutter_test.dart';

import 'package:mobile/features/diagram_viewer/models/diagram_observation.dart';
import 'package:mobile/features/diagram_viewer/widgets/diagram_canvas.dart';

void main() {
  group('read-only UML diagram models', () {
    test('parses node geometry and edge routing from the backend contract', () {
      final node = DiagramNode.fromJson({
        'id': 'node-1',
        'umlElementId': 'class-1',
        'x': 24,
        'y': 40,
        'width': 310,
        'height': 180,
        'zIndex': 2,
      });
      final edge = DiagramEdge.fromJson({
        'id': 'edge-1',
        'umlElementId': 'association-1',
        'sourceNodeId': 'node-1',
        'targetNodeId': 'node-2',
        'sourceSide': 'RIGHT',
        'targetSide': 'LEFT',
        'waypoints': [
          {'x': 400, 'y': 100},
        ],
      });

      expect(node.x, 24);
      expect(node.width, 310);
      expect(edge.sourceSide, 'RIGHT');
      expect(edge.waypoints.single.x, 400);
    });

    test('resolves classifier types and keeps return parameters distinct', () {
      final property = UmlProperty.fromJson(
        {
          'name': 'customer',
          'visibility': 'PRIVATE',
          'typeClassifierId': 'customer-class',
          'lowerBound': 1,
          'upperBound': 1,
        },
        classifierNames: const {'customer-class': 'Customer'},
      );
      final returnParameter = UmlParameter.fromJson(
        {
          'name': null,
          'direction': 'RETURN',
          'typeClassifierId': 'customer-class',
        },
        classifierNames: const {'customer-class': 'Customer'},
      );

      expect(property.type, 'Customer');
      expect(property.lowerBound, 1);
      expect(returnParameter.type, 'Customer');
      expect(returnParameter.direction, 'RETURN');
    });
  });

  test('fits negative node coordinates into a padded canvas', () {
    const observation = DiagramObservation(
      id: 'diagram-1',
      modelId: 'model-1',
      name: 'Main',
      revision: 3,
      nodes: [
        DiagramNode(
          id: 'node-1',
          umlElementId: 'class-1',
          x: -20,
          y: 40,
          width: 310,
          height: 180,
          zIndex: 0,
        ),
      ],
      edges: [],
      classes: [],
      associations: [],
      generalizations: [],
      realizations: [],
    );

    final geometry = DiagramCanvasGeometry.from(observation);

    expect(geometry.originX, 68);
    expect(geometry.originY, 8);
    expect(geometry.width, 406);
    expect(geometry.height, 276);
  });
}
