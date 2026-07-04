import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/providers.dart';
import '../../../core/theme/app_theme.dart';
import '../devices/devices_screen.dart';
import '../rules/rules_screen.dart';
import '../events/events_screen.dart';
import '../settings/settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});
  @override State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _tab = 0;

  final _pages = [const _Dashboard(), const DevicesScreen(), const RulesScreen(), const EventsScreen()];

  @override void initState() {
    super.initState();
    Future.microtask(() {
      context.read<DeviceProvider>().fetch();
      context.read<RuleProvider>().fetch();
      context.read<EventProvider>().fetchStats();
    });
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: _pages[_tab],
    bottomNavigationBar: BottomNavigationBar(
      currentIndex: _tab, onTap: (i) => setState(() => _tab = i),
      items: const [
        BottomNavigationBarItem(icon: Icon(Icons.dashboard_outlined), activeIcon: Icon(Icons.dashboard), label: '概览'),
        BottomNavigationBarItem(icon: Icon(Icons.phone_android_outlined), activeIcon: Icon(Icons.phone_android), label: '设备'),
        BottomNavigationBarItem(icon: Icon(Icons.rule_outlined), activeIcon: Icon(Icons.rule), label: '规则'),
        BottomNavigationBarItem(icon: Icon(Icons.history_outlined), activeIcon: Icon(Icons.history), label: '记录'),
      ],
    ),
  );
}

class _Dashboard extends StatelessWidget {
  const _Dashboard();
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('FamilyGuard'), actions: [
        IconButton(icon: const Icon(Icons.settings_outlined), onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SettingsScreen()))),
      ]),
      body: SingleChildScrollView(padding: const EdgeInsets.all(16), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('欢迎回来', style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: AppTheme.textPrimary)),
        const SizedBox(height: 24),
        Consumer<EventProvider>(builder: (_, ep, __) {
          final s = ep.stats;
          return Row(children: [
            _stat('今日拦截', '${s?.todayBlocked??0}', Icons.block, AppTheme.error),
            const SizedBox(width: 12),
            _stat('总拦截', '${s?.totalBlocked??0}', Icons.security, AppTheme.primary),
          ]);
        }),
        const SizedBox(height: 12),
        Consumer<DeviceProvider>(builder: (_, dp, __) {
          final online = dp.devices.where((d) => d.isOnline).length;
          return Row(children: [
            _stat('已绑定', '${dp.devices.length}', Icons.phone_android, AppTheme.info),
            const SizedBox(width: 12),
            _stat('在线', '$online', Icons.wifi, AppTheme.success),
          ]);
        }),
        const SizedBox(height: 24),
        const Text('快捷操作', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppTheme.textPrimary)),
        const SizedBox(height: 12),
        Row(children: [
          Expanded(child: _action(context, '绑定设备', Icons.add_circle_outline, () {})),
          const SizedBox(width: 12),
          Expanded(child: _action(context, '创建规则', Icons.rule_outlined, () {
            Navigator.push(context, MaterialPageRoute(builder: (_) => const RulesScreen()));
          })),
        ]),
        const SizedBox(height: 24),
        const Text('热门拦截应用', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppTheme.textPrimary)),
        const SizedBox(height: 12),
        Consumer<EventProvider>(builder: (_, ep, __) {
          final apps = ep.stats?.topApps ?? [];
          if (apps.isEmpty) return Card(child: Padding(padding: const EdgeInsets.all(24), child: Text('暂无数据', style: TextStyle(color: AppTheme.textSecondary))));
          return Card(child: Column(children: apps.take(5).map((a) => ListTile(
            leading: CircleAvatar(backgroundColor: AppTheme.primary.withOpacity(0.2), child: const Icon(Icons.apps, color: AppTheme.primary)),
            title: Text(a.appName.isNotEmpty ? a.appName : a.packageName),
            subtitle: Text(a.packageName, style: const TextStyle(fontSize: 12)),
            trailing: Text('${a.count}次', style: const TextStyle(color: AppTheme.error, fontWeight: FontWeight.bold)),
          )).toList()));
        }),
      ])),
    );
  }

  Widget _stat(String title, String value, IconData icon, Color color) => Expanded(child: Card(child: Padding(padding: const EdgeInsets.all(16), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Icon(icon, color: color, size: 24), const SizedBox(height: 12),
    Text(value, style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: color)),
    Text(title, style: TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
  ]))));

  Widget _action(BuildContext context, String title, IconData icon, VoidCallback onTap) => Card(child: InkWell(
    onTap: onTap, borderRadius: BorderRadius.circular(16),
    child: Padding(padding: const EdgeInsets.all(16), child: Column(children: [
      Icon(icon, size: 32, color: AppTheme.primary), const SizedBox(height: 8),
      Text(title, style: const TextStyle(fontWeight: FontWeight.bold, color: AppTheme.textPrimary)),
    ])),
  ));
}
