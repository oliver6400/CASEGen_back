import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart';

import '../../features/diagram_viewer/models/diagram_observation.dart';
import '../../models/assistant_message.dart';
import '../../models/pending_action.dart';
import '../../models/project_summary.dart';
import '../../models/uml_model_summary.dart';

class LocalDatabase {
  LocalDatabase._();

  static final LocalDatabase instance = LocalDatabase._();
  Database? _database;

  // In-memory fallback for Web platform where native sqflite is unavailable
  final List<ProjectSummary> _webProjects = [];
  final Map<String, List<UmlModelSummary>> _webModels = {};
  final Map<String, List<Map<String, String>>> _webDiagrams = {};
  final Map<String, DiagramObservation> _webObservations = {};
  final List<AssistantMessage> _webMessages = [];
  final List<PendingAction> _webActions = [];

  Future<void> initialize() async {
    if (kIsWeb) return;
    if (_database != null) return;
    final databasePath = path.join(
      await getDatabasesPath(),
      'casegen_mobile.db',
    );
    _database = await openDatabase(
      databasePath,
      version: 2,
      onCreate: (database, _) async {
        await _createTables(database);
      },
      onUpgrade: (database, oldVersion, newVersion) async {
        if (oldVersion < 2) {
          await _createCacheTables(database);
        }
      },
    );
  }

  Future<void> _createTables(Database database) async {
    await database.execute('''
      CREATE TABLE IF NOT EXISTS assistant_messages (
        id TEXT PRIMARY KEY,
        role TEXT NOT NULL,
        text TEXT NOT NULL,
        created_at TEXT NOT NULL,
        status TEXT NOT NULL,
        is_offline INTEGER NOT NULL
      )
    ''');
    await database.execute('''
      CREATE TABLE IF NOT EXISTS pending_actions (
        id TEXT PRIMARY KEY,
        project_id TEXT,
        model_id TEXT,
        intent_type TEXT NOT NULL,
        payload_json TEXT NOT NULL,
        created_at TEXT NOT NULL,
        status TEXT NOT NULL
      )
    ''');
    await _createCacheTables(database);
  }

  Future<void> _createCacheTables(Database database) async {
    await database.execute('''
      CREATE TABLE IF NOT EXISTS cached_projects (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        description TEXT,
        visibility TEXT NOT NULL
      )
    ''');
    await database.execute('''
      CREATE TABLE IF NOT EXISTS cached_models (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL,
        name TEXT NOT NULL,
        revision INTEGER NOT NULL,
        status TEXT NOT NULL
      )
    ''');
    await database.execute('''
      CREATE TABLE IF NOT EXISTS cached_diagrams (
        id TEXT PRIMARY KEY,
        model_id TEXT NOT NULL,
        name TEXT NOT NULL
      )
    ''');
    await database.execute('''
      CREATE TABLE IF NOT EXISTS cached_observations (
        diagram_id TEXT PRIMARY KEY,
        model_id TEXT NOT NULL,
        observation_json TEXT NOT NULL,
        cached_at TEXT NOT NULL
      )
    ''');
  }

  // --- Offline Cache: Proyectos ---
  Future<void> saveProjects(List<ProjectSummary> projects) async {
    if (kIsWeb) {
      _webProjects.clear();
      _webProjects.addAll(projects);
      return;
    }
    await initialize();
    final batch = _database!.batch();
    for (final p in projects) {
      batch.insert(
        'cached_projects',
        {
          'id': p.id,
          'name': p.name,
          'description': p.description,
          'visibility': p.visibility,
        },
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
    await batch.commit(noResult: true);
  }

  Future<List<ProjectSummary>> getCachedProjects() async {
    if (kIsWeb) {
      return List.unmodifiable(_webProjects);
    }
    await initialize();
    final rows = await _database!.query('cached_projects', orderBy: 'name ASC');
    return rows.map((r) => ProjectSummary.fromJson(r)).toList();
  }

  // --- Offline Cache: Modelos UML ---
  Future<void> saveModels(String projectId, List<UmlModelSummary> models) async {
    if (kIsWeb) {
      _webModels[projectId] = List.from(models);
      return;
    }
    await initialize();
    final batch = _database!.batch();
    for (final m in models) {
      batch.insert(
        'cached_models',
        {
          'id': m.id,
          'project_id': m.projectId,
          'name': m.name,
          'revision': m.revision,
          'status': m.status,
        },
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
    await batch.commit(noResult: true);
  }

  Future<List<UmlModelSummary>> getCachedModels(String projectId) async {
    if (kIsWeb) {
      return List.unmodifiable(_webModels[projectId] ?? const []);
    }
    await initialize();
    final rows = await _database!.query(
      'cached_models',
      where: 'project_id = ?',
      whereArgs: [projectId],
      orderBy: 'name ASC',
    );
    return rows
        .map(
          (r) => UmlModelSummary(
            id: r['id'] as String,
            projectId: r['project_id'] as String,
            name: r['name'] as String,
            revision: r['revision'] as int? ?? 0,
            status: r['status'] as String? ?? 'EDITABLE',
          ),
        )
        .toList();
  }

  // --- Offline Cache: Diagramas ---
  Future<void> saveDiagrams(
    String modelId,
    List<Map<String, String>> diagrams,
  ) async {
    if (kIsWeb) {
      _webDiagrams[modelId] = List.from(diagrams);
      return;
    }
    await initialize();
    final batch = _database!.batch();
    for (final d in diagrams) {
      batch.insert(
        'cached_diagrams',
        {
          'id': d['id']!,
          'model_id': modelId,
          'name': d['name'] ?? 'Diagrama',
        },
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
    await batch.commit(noResult: true);
  }

  Future<List<Map<String, String>>> getCachedDiagrams(String modelId) async {
    if (kIsWeb) {
      return List.unmodifiable(_webDiagrams[modelId] ?? const []);
    }
    await initialize();
    final rows = await _database!.query(
      'cached_diagrams',
      where: 'model_id = ?',
      whereArgs: [modelId],
      orderBy: 'name ASC',
    );
    return rows
        .map(
          (r) => {
            'id': r['id'] as String,
            'name': r['name'] as String? ?? 'Diagrama',
          },
        )
        .toList();
  }

  // --- Offline Cache: Diagram Observation ---
  Future<void> saveObservation(DiagramObservation observation) async {
    if (kIsWeb) {
      _webObservations[observation.id] = observation;
      return;
    }
    await initialize();
    await _database!.insert(
      'cached_observations',
      {
        'diagram_id': observation.id,
        'model_id': observation.modelId,
        'observation_json': jsonEncode(observation.toJson()),
        'cached_at': DateTime.now().toIso8601String(),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<DiagramObservation?> getCachedObservation(String diagramId) async {
    if (kIsWeb) {
      return _webObservations[diagramId];
    }
    await initialize();
    final rows = await _database!.query(
      'cached_observations',
      where: 'diagram_id = ?',
      whereArgs: [diagramId],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    final jsonStr = rows.first['observation_json'] as String;
    final map = jsonDecode(jsonStr) as Map<String, dynamic>;
    return DiagramObservation.fromJson(map);
  }

  // --- Assistant / Pending legacy ---
  Future<void> saveMessage(AssistantMessage message) async {
    if (kIsWeb) {
      _webMessages.add(message);
      return;
    }
    await initialize();
    await _database!.insert(
      'assistant_messages',
      message.toDatabaseMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<AssistantMessage>> readMessages() async {
    if (kIsWeb) {
      return List.unmodifiable(_webMessages);
    }
    await initialize();
    final rows = await _database!.query(
      'assistant_messages',
      orderBy: 'created_at ASC',
    );
    return rows.map(AssistantMessage.fromDatabaseMap).toList();
  }

  Future<void> savePendingAction(PendingAction action) async {
    if (kIsWeb) {
      _webActions.add(action);
      return;
    }
    await initialize();
    await _database!.insert('pending_actions', {
      'id': action.id,
      'project_id': action.projectId,
      'model_id': action.modelId,
      'intent_type': action.intentType,
      'payload_json': jsonEncode(action.payload),
      'created_at': action.createdAt.toIso8601String(),
      'status': action.status,
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }
}
