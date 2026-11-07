import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AuthTokens {
  const AuthTokens({
    required this.accessToken,
    required this.refreshToken,
    this.tokenType = 'Bearer',
    this.expiresIn = 0,
  });

  factory AuthTokens.fromJson(Map<String, dynamic> json) => AuthTokens(
    accessToken: json['accessToken'] as String,
    refreshToken: json['refreshToken'] as String,
    tokenType: (json['tokenType'] as String?) ?? 'Bearer',
    expiresIn: (json['expiresIn'] as num?)?.toInt() ?? 0,
  );

  final String accessToken;
  final String refreshToken;
  final String tokenType;
  final int expiresIn;
}

class TokenStorage {
  const TokenStorage({FlutterSecureStorage? storage})
    : _storage = storage ?? const FlutterSecureStorage();

  static const _accessTokenKey = 'casegen_access_token';
  static const _refreshTokenKey = 'casegen_refresh_token';
  static const _tokenTypeKey = 'casegen_token_type';

  final FlutterSecureStorage _storage;

  Future<void> save(AuthTokens tokens) async {
    if (kIsWeb) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_accessTokenKey, tokens.accessToken);
      await prefs.setString(_refreshTokenKey, tokens.refreshToken);
      await prefs.setString(_tokenTypeKey, tokens.tokenType);
      return;
    }
    await Future.wait([
      _storage.write(key: _accessTokenKey, value: tokens.accessToken),
      _storage.write(key: _refreshTokenKey, value: tokens.refreshToken),
      _storage.write(key: _tokenTypeKey, value: tokens.tokenType),
    ]);
  }

  Future<String?> readAccessToken() async {
    if (kIsWeb) {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(_accessTokenKey);
    }
    return _storage.read(key: _accessTokenKey);
  }

  Future<String?> readRefreshToken() async {
    if (kIsWeb) {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(_refreshTokenKey);
    }
    return _storage.read(key: _refreshTokenKey);
  }

  Future<String> readTokenType() async {
    if (kIsWeb) {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(_tokenTypeKey) ?? 'Bearer';
    }
    return await _storage.read(key: _tokenTypeKey) ?? 'Bearer';
  }

  Future<bool> hasSession() async {
    try {
      final accessToken = await readAccessToken();
      final refreshToken = await readRefreshToken();
      return accessToken?.isNotEmpty == true || refreshToken?.isNotEmpty == true;
    } catch (_) {
      return false;
    }
  }

  Future<void> clear() async {
    if (kIsWeb) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_accessTokenKey);
      await prefs.remove(_refreshTokenKey);
      await prefs.remove(_tokenTypeKey);
      return;
    }
    await _storage.deleteAll();
  }
}
