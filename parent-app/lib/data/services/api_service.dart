import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/models.dart';
import '../../core/config/api_config.dart';
import 'logger_service.dart';

class ApiService {
  static final ApiService _instance = ApiService._internal();
  factory ApiService() => _instance;

  late Dio _dio;
  String? _token;

  ApiService._internal() {
    _dio = Dio(BaseOptions(
      baseUrl: ApiConfig.baseUrl,
      connectTimeout: const Duration(milliseconds: ApiConfig.connectTimeout),
      receiveTimeout: const Duration(milliseconds: ApiConfig.receiveTimeout),
      headers: {'Content-Type': 'application/json'},
    ));
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) {
        if (_token != null) options.headers['Authorization'] = 'Bearer $_token';
        return handler.next(options);
      },
      onError: (error, handler) {
        if (error.response?.statusCode == 401) _clearToken();
        return handler.next(error);
      },
    ));
  }

  Future<void> init() async {
    await ApiConfig.load();
    _dio.options.baseUrl = ApiConfig.baseUrl;
    final prefs = await SharedPreferences.getInstance();
    _token = prefs.getString(ApiConfig.tokenKey);
  }

  void updateBaseUrl(String url) {
    _dio.options.baseUrl = url;
  }

  Future<void> _saveToken(String t) async {
    _token = t;
    (await SharedPreferences.getInstance()).setString(ApiConfig.tokenKey, t);
  }
  Future<void> _clearToken() async {
    _token = null;
    (await SharedPreferences.getInstance()).remove(ApiConfig.tokenKey);
  }
  bool get isLoggedIn => _token != null;

  // Auth
  Future<Map<String, dynamic>> login(String email, String password) async {
    final r = await _dio.post('/login', data: {'email': email, 'password': password});
    await _saveToken(r.data['data']['token']);
    Logger.i('Auth', '登录成功', {'email': email});
    return r.data['data'];
  }

  Future<Map<String, dynamic>> register(String email, String password, String nickname) async {
    final r = await _dio.post('/register', data: {'email': email, 'password': password, 'nickname': nickname});
    await _saveToken(r.data['data']['token']);
    return r.data['data'];
  }

  Future<void> logout() async => _clearToken();

  // Devices
  Future<List<Device>> getDevices() async {
    final r = await _dio.get('/devices');
    return (r.data['data'] as List).map((d) => Device.fromJson(d)).toList();
  }

  Future<Device> bindDevice(String deviceId, String pairingCode, {String? name}) async {
    final r = await _dio.post('/devices/bind', data: {'device_id': deviceId, 'pairing_code': pairingCode, 'device_name': name});
    return Device.fromJson(r.data['data']);
  }

  Future<void> unbindDevice(int id) async => _dio.delete('/devices/$id');

  // Child Apps
  Future<List<ChildApp>> getDeviceApps(int deviceId, {String search = ''}) async {
    final r = await _dio.get('/devices/$deviceId/apps', queryParameters: {'search': search, 'page_size': 100});
    return ((r.data['data'] as Map)['data'] as List?)?.map((a) => ChildApp.fromJson(a)).toList() ?? [];
  }

  // Rules
  Future<List<Rule>> getRules() async {
    final r = await _dio.get('/rules');
    return (r.data['data'] as List).map((r) => Rule.fromJson(r)).toList();
  }

  Future<void> createRule(String name, List<String> appIds, List<Map<String,dynamic>> schedules, List<int> deviceIds) async {
    await _dio.post('/rules', data: {'name': name, 'app_ids': appIds, 'schedules': schedules, 'device_ids': deviceIds});
  }

  Future<void> updateRule(int id, {String? name, bool? isActive, List<String>? appIds, List<Map<String,dynamic>>? schedules, List<int>? deviceIds}) async {
    await _dio.put('/rules/$id', data: {
      if (name != null) 'name': name, if (isActive != null) 'is_active': isActive,
      if (appIds != null) 'app_ids': appIds, if (schedules != null) 'schedules': schedules,
      if (deviceIds != null) 'device_ids': deviceIds,
    });
  }

  Future<void> deleteRule(int id) async => _dio.delete('/rules/$id');
  Future<void> toggleRule(int id, bool active) async => _dio.post('/rules/$id/toggle', data: {'is_active': active});

  // Events
  Future<Map<String, dynamic>> getEvents({int? deviceId, int page = 1, int pageSize = 20}) async {
    final r = await _dio.get('/events', queryParameters: {
      'page': page, 'page_size': pageSize, if (deviceId != null) 'device_id': deviceId,
    });
    return r.data['data'];
  }

  Future<Stats> getStats() async {
    final r = await _dio.get('/events/stats');
    return Stats.fromJson(r.data['data']);
  }

  // Logs
  Future<void> uploadLogs(List<Map<String, dynamic>> logs) async {
    try {
      await _dio.post('/logs', data: {'source': 'parent-app', 'logs': logs});
    } catch (_) {}
  }
}
