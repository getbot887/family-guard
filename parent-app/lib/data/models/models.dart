class User {
  final int id; final String email; final String nickname; final DateTime createdAt;
  User({required this.id, required this.email, required this.nickname, required this.createdAt});
  factory User.fromJson(Map<String, dynamic> j) => User(id: j['id'], email: j['email'], nickname: j['nickname']??'', createdAt: DateTime.parse(j['created_at']));
}

class Device {
  final int id; final String deviceId; final String deviceName; final String model;
  final bool isOnline; final DateTime? lastSeenAt; final DateTime createdAt;
  Device({required this.id, required this.deviceId, required this.deviceName, required this.model, required this.isOnline, this.lastSeenAt, required this.createdAt});
  factory Device.fromJson(Map<String, dynamic> j) => Device(
    id: j['id'], deviceId: j['device_id'], deviceName: j['device_name']??'未知设备', model: j['model']??'',
    isOnline: j['is_online']??false,
    lastSeenAt: j['last_seen_at'] != null ? DateTime.parse(j['last_seen_at']) : null,
    createdAt: DateTime.parse(j['created_at']));
}

class Rule {
  final int id; final String name; final bool isActive; final List<RuleApp> apps;
  final List<RuleSchedule> schedules; final List<int> deviceIds; final DateTime createdAt;
  Rule({required this.id, required this.name, required this.isActive, required this.apps, required this.schedules, required this.deviceIds, required this.createdAt});
  factory Rule.fromJson(Map<String, dynamic> j) => Rule(
    id: j['id'], name: j['name'], isActive: j['is_active']??true,
    apps: (j['apps'] as List?)?.map((a)=>RuleApp.fromJson(a)).toList()??[],
    schedules: (j['schedules'] as List?)?.map((s)=>RuleSchedule.fromJson(s)).toList()??[],
    deviceIds: (j['device_ids'] as List?)?.map((d)=>d as int).toList()??[],
    createdAt: DateTime.parse(j['created_at']));
}

class RuleApp { final int id; final String packageName; final String appName;
  RuleApp({required this.id, required this.packageName, required this.appName});
  factory RuleApp.fromJson(Map<String, dynamic> j) => RuleApp(id: j['id'], packageName: j['package_name'], appName: j['app_name']??''); }

class RuleSchedule { final int id; final List<int> daysOfWeek; final String startTime; final String endTime;
  RuleSchedule({required this.id, required this.daysOfWeek, required this.startTime, required this.endTime});
  factory RuleSchedule.fromJson(Map<String, dynamic> j) => RuleSchedule(
    id: j['id'], daysOfWeek: List<int>.from(j['days_of_week']),
    startTime: j['start_time'], endTime: j['end_time']); }

class BlockEvent { final int id; final int deviceId; final String packageName; final String appName; final DateTime blockedAt;
  BlockEvent({required this.id, required this.deviceId, required this.packageName, required this.appName, required this.blockedAt});
  factory BlockEvent.fromJson(Map<String, dynamic> j) => BlockEvent(
    id: j['id'], deviceId: j['device_id'], packageName: j['package_name'], appName: j['app_name']??'',
    blockedAt: DateTime.parse(j['blocked_at'])); }

class ChildApp { final int id; final String packageName; final String appName;
  ChildApp({required this.id, required this.packageName, required this.appName});
  factory ChildApp.fromJson(Map<String, dynamic> j) => ChildApp(id: j['id'], packageName: j['package_name'], appName: j['app_name']??''); }

class Stats { final int totalBlocked; final int todayBlocked; final List<AppStat> topApps; final List<DayStat> dailyStats;
  Stats({required this.totalBlocked, required this.todayBlocked, required this.topApps, required this.dailyStats});
  factory Stats.fromJson(Map<String, dynamic> j) => Stats(
    totalBlocked: j['total_blocked']??0, todayBlocked: j['today_blocked']??0,
    topApps: (j['top_apps'] as List?)?.map((a)=>AppStat.fromJson(a)).toList()??[],
    dailyStats: (j['daily_stats'] as List?)?.map((d)=>DayStat.fromJson(d)).toList()??[]); }

class AppStat { final String packageName; final String appName; final int count;
  AppStat({required this.packageName, required this.appName, required this.count});
  factory AppStat.fromJson(Map<String, dynamic> j) => AppStat(packageName: j['package_name'], appName: j['app_name']??'', count: j['count']??0); }

class DayStat { final String date; final int count;
  DayStat({required this.date, required this.count});
  factory DayStat.fromJson(Map<String, dynamic> j) => DayStat(date: j['date'], count: j['count']??0); }
