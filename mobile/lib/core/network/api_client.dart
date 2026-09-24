import 'dart:async';

import 'package:dio/dio.dart';

import '../config/api_config.dart';
import '../storage/token_storage.dart';
import 'network_log_interceptor.dart';

class ApiClient {
  ApiClient(this._tokenStorage)
    : dio = Dio(
        BaseOptions(
          baseUrl: ApiConfig.baseUrl,
          connectTimeout: ApiConfig.connectTimeout,
          receiveTimeout: ApiConfig.receiveTimeout,
          headers: const {'Accept': 'application/json'},
        ),
      ) {
    dio.interceptors.add(
      InterceptorsWrapper(onRequest: _authorize, onError: _handleUnauthorized),
    );
    dio.interceptors.add(NetworkLogInterceptor());
  }

  final Dio dio;
  final TokenStorage _tokenStorage;
  Future<String?>? _refreshInProgress;
  FutureOr<void> Function()? onUnauthorized;

  static const _publicPaths = {'/api/auth/login', '/api/auth/refresh'};

  Future<void> _authorize(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    if (!_publicPaths.contains(options.path)) {
      final token = await _tokenStorage.readAccessToken();
      if (token?.isNotEmpty == true) {
        final type = await _tokenStorage.readTokenType();
        options.headers['Authorization'] = '$type $token';
      }
    }
    handler.next(options);
  }

  Future<void> _handleUnauthorized(
    DioException error,
    ErrorInterceptorHandler handler,
  ) async {
    final request = error.requestOptions;
    final isUnauthorized = error.response?.statusCode == 401;
    final alreadyRetried = request.extra['casegenRetried'] == true;
    final isAuthRequest = _publicPaths.contains(request.path);

    if (!isUnauthorized || alreadyRetried || isAuthRequest) {
      handler.next(error);
      return;
    }

    try {
      final accessToken = await _refreshAccessToken();
      if (accessToken == null) {
        await _expireSession();
        handler.next(error);
        return;
      }
      request.extra['casegenRetried'] = true;
      request.headers['Authorization'] = 'Bearer $accessToken';
      handler.resolve(await dio.fetch<dynamic>(request));
    } on Object {
      await _expireSession();
      handler.next(error);
    }
  }

  Future<String?> _refreshAccessToken() {
    final current = _refreshInProgress;
    if (current != null) return current;
    final operation = _performRefresh();
    _refreshInProgress = operation;
    return operation.whenComplete(() => _refreshInProgress = null);
  }

  Future<String?> _performRefresh() async {
    final refreshToken = await _tokenStorage.readRefreshToken();
    if (refreshToken == null || refreshToken.isEmpty) return null;
    final refreshClient = Dio(dio.options)
      ..interceptors.add(NetworkLogInterceptor());
    final response = await refreshClient.post<Map<String, dynamic>>(
      '/api/auth/refresh',
      data: {'refreshToken': refreshToken},
    );
    final data = response.data;
    if (data == null) return null;
    final tokens = AuthTokens.fromJson(data);
    await _tokenStorage.save(tokens);
    return tokens.accessToken;
  }

  Future<void> _expireSession() async {
    await _tokenStorage.clear();
    await onUnauthorized?.call();
  }
}
