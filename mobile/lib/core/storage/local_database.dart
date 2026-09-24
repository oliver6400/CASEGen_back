import 'dart:convert';

import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart';

import '../../models/assistant_message.dart';
import '../../models/pending_action.dart';

class LocalDatabase {
  LocalDatabase._();

  static final LocalDatabase instance = LocalDatabase._();
  Database? _database;

  Future<void> initialize() async {
    if (_database != null) return;
    final databasePath = path.join(
      await getDatabasesPath(),
      'casegen_mobile.db',
    );
    _database = await openDatabase(
      databasePath,
      version: 1,
      onCreate: (database, _) async {
        await database.execute('''
          CREATE TABLE assistant_messages (
            id TEXT PRIMARY KEY,
            role TEXT NOT NULL,
            text TEXT NOT NULL,
            created_at TEXT NOT NULL,
            status TEXT NOT NULL,
            is_offline INTEGER NOT NULL
          )
        ''');
        await database.execute('''
          CREATE TABLE pending_actions (
            id TEXT PRIMARY KEY,
            project_id TEXT,
            model_id TEXT,
            intent_type TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            created_at TEXT NOT NULL,
            status TEXT NOT NULL
          )
        ''');
      },
    );
  }

  Future<void> saveMessage(AssistantMessage message) async {
    await initialize();
    await _database!.insert(
      'assistant_messages',
      message.toDatabaseMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<AssistantMessage>> readMessages() async {
    await initialize();
    final rows = await _database!.query(
      'assistant_messages',
      orderBy: 'created_at ASC',
    );
    return rows.map(AssistantMessage.fromDatabaseMap).toList();
  }

  Future<void> savePendingAction(PendingAction action) async {
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
