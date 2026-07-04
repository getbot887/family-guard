import 'package:flutter/foundation.dart';
import '../data/models/models.dart';
import '../data/services/api_service.dart';

class AuthProvider extends ChangeNotifier {
  final ApiService _api = ApiService();
  User? _user; bool _loading = false; String? _error;
  User? get user => _user; bool get isLoading => _loading; bool get isLoggedIn => _api.isLoggedIn;

  Future<void> init() async {
    await _api.init();
    if (_api.isLoggedIn) { /* 可在此拉取用户信息 */ }
    notifyListeners();
  }

  Future<bool> login(String email, String password) async {
    _loading = true; _error = null; notifyListeners();
    try {
      final data = await _api.login(email, password);
      _user = User.fromJson(data['user']);
      _loading = false; notifyListeners(); return true;
    } catch (e) { _error = e.toString(); _loading = false; notifyListeners(); return false; }
  }

  Future<bool> register(String email, String password, String nickname) async {
    _loading = true; _error = null; notifyListeners();
    try {
      final data = await _api.register(email, password, nickname);
      _user = User.fromJson(data['user']);
      _loading = false; notifyListeners(); return true;
    } catch (e) { _error = e.toString(); _loading = false; notifyListeners(); return false; }
  }

  Future<void> logout() async { await _api.logout(); _user = null; notifyListeners(); }
  String? get error => _error;
}

class DeviceProvider extends ChangeNotifier {
  final ApiService _api = ApiService();
  List<Device> _devices = []; bool _loading = false; String? _error;
  List<Device> get devices => _devices; bool get isLoading => _loading; String? get error => _error;

  Future<void> fetch() async {
    _loading = true; _error = null; notifyListeners();
    try { _devices = await _api.getDevices(); }
    catch (e) { _error = e.toString(); }
    _loading = false; notifyListeners();
  }

  Future<bool> bind(String deviceId, String code, {String? name}) async {
    try { await _api.bindDevice(deviceId, code, name: name); await fetch(); return true; }
    catch (e) { _error = e.toString(); notifyListeners(); return false; }
  }

  Future<bool> unbind(int id) async {
    try { await _api.unbindDevice(id); await fetch(); return true; }
    catch (e) { _error = e.toString(); notifyListeners(); return false; }
  }
}

class RuleProvider extends ChangeNotifier {
  final ApiService _api = ApiService();
  List<Rule> _rules = []; bool _loading = false; String? _error;
  List<Rule> get rules => _rules; bool get isLoading => _loading; String? get error => _error;

  Future<void> fetch() async {
    _loading = true; _error = null; notifyListeners();
    try { _rules = await _api.getRules(); }
    catch (e) { _error = e.toString(); }
    _loading = false; notifyListeners();
  }

  Future<bool> create(String name, List<String> appIds, List<Map<String,dynamic>> schedules, List<int> deviceIds) async {
    try { await _api.createRule(name, appIds, schedules, deviceIds); await fetch(); return true; }
    catch (e) { _error = e.toString(); notifyListeners(); return false; }
  }

  Future<bool> update(int id, {String? name, bool? isActive}) async {
    try { await _api.updateRule(id, name: name, isActive: isActive); await fetch(); return true; }
    catch (e) { _error = e.toString(); notifyListeners(); return false; }
  }

  Future<bool> delete(int id) async {
    try { await _api.deleteRule(id); await fetch(); return true; }
    catch (e) { _error = e.toString(); notifyListeners(); return false; }
  }

  Future<bool> toggle(int id, bool active) async {
    try { await _api.toggleRule(id, active); await fetch(); return true; }
    catch (e) { _error = e.toString(); notifyListeners(); return false; }
  }
}

class EventProvider extends ChangeNotifier {
  final ApiService _api = ApiService();
  List<BlockEvent> _events = []; Stats? _stats; bool _loading = false; String? _error;
  List<BlockEvent> get events => _events; Stats? get stats => _stats; bool get isLoading => _loading; String? get error => _error;

  Future<void> fetchEvents({int? deviceId, bool refresh = false}) async {
    _loading = true; _error = null; notifyListeners();
    try {
      final data = await _api.getEvents(deviceId: deviceId, page: 1);
      _events = (data['data'] as List).map((e) => BlockEvent.fromJson(e)).toList();
    } catch (e) { _error = e.toString(); }
    _loading = false; notifyListeners();
  }

  Future<void> fetchStats() async {
    try { _stats = await _api.getStats(); notifyListeners(); }
    catch (e) { _error = e.toString(); notifyListeners(); }
  }
}
