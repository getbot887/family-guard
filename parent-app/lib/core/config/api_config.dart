import 'package:shared_preferences/shared_preferences.dart';

class ApiConfig {
  static const String _apiPath = '/api/v1';
  static const String _prodPlaceholder = 'https://YOUR_DOMAIN';
  static const String _keyBaseUrl = 'server_base_url';

  static String _cachedBaseUrl = _prodPlaceholder;

  static String get baseUrl => _cachedBaseUrl + _apiPath;

  static const int connectTimeout = 10000;
  static const int receiveTimeout = 10000;
  static const String tokenKey = 'auth_token';

  static Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_keyBaseUrl);
    _cachedBaseUrl = saved ?? _prodPlaceholder;
  }

  static Future<void> setBaseUrl(String domain) async {
    // 去除末尾斜杠
    _cachedBaseUrl = domain.endsWith('/') ? domain.substring(0, domain.length - 1) : domain;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyBaseUrl, _cachedBaseUrl);
  }

  static String get displayUrl => _cachedBaseUrl;
  static bool get isDefaultUrl => _cachedBaseUrl == _prodPlaceholder;
}
