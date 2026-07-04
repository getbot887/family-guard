import 'dart:async';
import 'dart:collection';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:dio/dio.dart';

class Logger {
  static final Queue<Map<String, dynamic>> _buffer = Queue();
  static Timer? _timer;
  static Dio? _dio;
  static String? _token;
  static const String _key = 'log_cache';

  static Future<void> init({required String baseUrl}) async {
    _dio = Dio(BaseOptions(
      baseUrl: baseUrl,
      connectTimeout: const Duration(seconds: 5),
      receiveTimeout: const Duration(seconds: 5),
    ));
    _timer = Timer.periodic(const Duration(seconds: 10), (_) => _flush());
    await _loadCached();
  }

  static Future<void> _loadCached() async {
    final prefs = await SharedPreferences.getInstance();
    final cached = prefs.getStringList(_key) ?? [];
    for (final json in cached) {
      try {
        _buffer.add(Map<String, dynamic>.from(
            Uri.dataFromString(json).queryParameters));
      } catch (_) {}
    }
    if (cached.isNotEmpty) await prefs.remove(_key);
  }

  static void setToken(String token) { _token = token; }

  static void d(String tag, String msg, [Map<String, dynamic>? extra]) =>
      _log('debug', tag, msg, extra);
  static void i(String tag, String msg, [Map<String, dynamic>? extra]) =>
      _log('info', tag, msg, extra);
  static void w(String tag, String msg, [Map<String, dynamic>? extra]) =>
      _log('warn', tag, msg, extra);
  static void e(String tag, String msg, [Object? error, StackTrace? stack]) {
    _log('error', tag, msg, {'error': error?.toString(), 'stack': stack?.toString()});
  }

  static void _log(String level, String tag, String msg, Map<String, dynamic>? extra) {
    final entry = {
      'level': level, 'tag': tag,
      'message': extra != null ? '$msg | $extra' : msg,
      'timestamp': DateTime.now().toUtc().toIso8601String(),
    };
    _buffer.add(entry);
    if (_buffer.length >= 200) _flush();
    if (level == 'error') debugPrint('[$tag] $msg');
  }

  static Future<void> _flush() async {
    if (_buffer.isEmpty || _dio == null) return;
    final batch = _buffer.take(200).toList();
    _buffer.clear();
    try {
      await _dio!.post('/logs', data: {
        'source': 'parent-app',
        'logs': batch,
      }, options: Options(
        headers: _token != null ? {'Authorization': 'Bearer $_token'} : null,
        extra: {'noLog': true},
      ));
    } catch (e) {
      // 上传失败时缓存到本地，下次再试
      _buffer.addAll(batch);
      if (_buffer.length > 500) {
        final toRemove = _buffer.length - 500;
        for (int i = 0; i < toRemove; i++) _buffer.removeFirst();
      }
    }
  }

  static void dispose() { _timer?.cancel(); _dio?.close(); }
}
