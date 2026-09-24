import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

class NetworkLogInterceptor extends Interceptor {
  static const _startedAtKey = 'casegenRequestStartedAt';

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    options.extra[_startedAtKey] = DateTime.now().microsecondsSinceEpoch;
    if (kDebugMode) {
      debugPrint('[CASEGen HTTP] --> ${options.method} ${options.uri}');
    }
    handler.next(options);
  }

  @override
  void onResponse(
    Response<dynamic> response,
    ResponseInterceptorHandler handler,
  ) {
    if (kDebugMode) {
      debugPrint(
        '[CASEGen HTTP] <-- ${response.statusCode} '
        '${response.requestOptions.method} ${response.requestOptions.uri} '
        '(${_elapsed(response.requestOptions)} ms)',
      );
    }
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (kDebugMode) {
      final status = err.response?.statusCode?.toString() ?? 'NETWORK';
      final serverCode = _safeServerCode(err.response?.data);
      debugPrint(
        '[CASEGen HTTP] xx $status ${err.requestOptions.method} '
        '${err.requestOptions.uri} (${_elapsed(err.requestOptions)} ms) '
        'type=${err.type.name}${serverCode == null ? '' : ' code=$serverCode'}',
      );
    }
    handler.next(err);
  }

  int _elapsed(RequestOptions options) {
    final startedAt = options.extra[_startedAtKey];
    if (startedAt is! int) return 0;
    return (DateTime.now().microsecondsSinceEpoch - startedAt) ~/ 1000;
  }

  String? _safeServerCode(Object? data) {
    if (data is! Map<String, dynamic>) return null;
    final code = data['code'];
    return code is String && code.isNotEmpty ? code : null;
  }
}
