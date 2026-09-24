import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/storage/token_storage.dart';

class AuthController extends ChangeNotifier {
  AuthController(this._apiClient, this._tokenStorage);

  final ApiClient _apiClient;
  final TokenStorage _tokenStorage;

  bool _isAuthenticated = false;
  bool _isRestoring = true;
  bool _isSubmitting = false;
  String? _errorMessage;

  bool get isAuthenticated => _isAuthenticated;
  bool get isRestoring => _isRestoring;
  bool get isSubmitting => _isSubmitting;
  String? get errorMessage => _errorMessage;

  Future<void> restoreSession() async {
    _isAuthenticated = await _tokenStorage.hasSession();
    _isRestoring = false;
    notifyListeners();
  }

  Future<bool> login({required String email, required String password}) async {
    _isSubmitting = true;
    _errorMessage = null;
    notifyListeners();
    try {
      final response = await _apiClient.dio.post<Map<String, dynamic>>(
        '/api/auth/login',
        data: {'email': email.trim(), 'password': password},
      );
      final data = response.data;
      if (data == null) throw const ApiException('Respuesta de login vacía.');
      await _tokenStorage.save(AuthTokens.fromJson(data));
      _isAuthenticated = true;
      return true;
    } on DioException catch (error) {
      _errorMessage = ApiException.fromDio(error).message;
      return false;
    } on ApiException catch (error) {
      _errorMessage = error.message;
      return false;
    } finally {
      _isSubmitting = false;
      notifyListeners();
    }
  }

  Future<void> logout() async {
    final refreshToken = await _tokenStorage.readRefreshToken();
    try {
      if (refreshToken?.isNotEmpty == true) {
        await _apiClient.dio.post<void>(
          '/api/auth/logout',
          data: {'refreshToken': refreshToken},
        );
      }
    } on DioException {
      // Local logout must still succeed if the server is unavailable.
    } finally {
      await _tokenStorage.clear();
      _isAuthenticated = false;
      notifyListeners();
    }
  }

  Future<void> handleUnauthorized() async {
    _isAuthenticated = false;
    _errorMessage = 'Tu sesión expiró. Inicia sesión nuevamente.';
    notifyListeners();
  }
}
