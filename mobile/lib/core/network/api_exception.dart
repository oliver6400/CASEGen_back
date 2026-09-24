import 'package:dio/dio.dart';

class ApiException implements Exception {
  const ApiException(this.message, {this.statusCode});

  factory ApiException.fromDio(DioException error) {
    final data = error.response?.data;
    String? serverMessage;
    if (data is Map<String, dynamic>) {
      serverMessage = data['message'] as String? ?? data['detail'] as String?;
    }
    return ApiException(
      serverMessage ?? _fallback(error.response?.statusCode),
      statusCode: error.response?.statusCode,
    );
  }

  final String message;
  final int? statusCode;

  static String _fallback(int? statusCode) => switch (statusCode) {
    400 => 'La solicitud contiene datos inválidos.',
    401 => 'Correo o contraseña incorrectos.',
    403 => 'No tienes permiso para realizar esta acción.',
    _ => 'No fue posible conectar con CASEGen.',
  };

  @override
  String toString() => message;
}
