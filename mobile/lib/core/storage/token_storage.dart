import 'package:flutter_secure_storage/flutter_secure_storage.dart';

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
    await Future.wait([
      _storage.write(key: _accessTokenKey, value: tokens.accessToken),
      _storage.write(key: _refreshTokenKey, value: tokens.refreshToken),
      _storage.write(key: _tokenTypeKey, value: tokens.tokenType),
    ]);
  }

  Future<String?> readAccessToken() => _storage.read(key: _accessTokenKey);

  Future<String?> readRefreshToken() => _storage.read(key: _refreshTokenKey);

  Future<String> readTokenType() async =>
      await _storage.read(key: _tokenTypeKey) ?? 'Bearer';

  Future<bool> hasSession() async {
    final accessToken = await readAccessToken();
    final refreshToken = await readRefreshToken();
    return accessToken?.isNotEmpty == true || refreshToken?.isNotEmpty == true;
  }

  Future<void> clear() => _storage.deleteAll();
}
