import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/providers.dart';
import '../../../core/theme/app_theme.dart';

class EventsScreen extends StatefulWidget {
  const EventsScreen({super.key});
  @override State<EventsScreen> createState() => _EventsScreenState();
}

class _EventsScreenState extends State<EventsScreen> {
  @override void initState() {
    super.initState();
    Future.microtask(() => context.read<EventProvider>().fetchEvents(refresh: true));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('拦截记录'), actions: [
        IconButton(icon: const Icon(Icons.filter_list), onPressed: () {}),
        IconButton(icon: const Icon(Icons.refresh), onPressed: () => context.read<EventProvider>().fetchEvents(refresh: true)),
      ]),
      body: Consumer<EventProvider>(builder: (_, ep, __) {
        if (ep.isLoading && ep.events.isEmpty) return const Center(child: CircularProgressIndicator());
        if (ep.events.isEmpty) return Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          Icon(Icons.history_outlined, size: 80, color: AppTheme.textSecondary.withOpacity(0.5)),
          const SizedBox(height: 16), Text('暂无拦截记录', style: TextStyle(fontSize: 18, color: AppTheme.textSecondary)),
        ]));
        return RefreshIndicator(onRefresh: () => ep.fetchEvents(refresh: true), child: ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: ep.events.length,
          itemBuilder: (_, i) {
            final e = ep.events[i];
            return Card(margin: const EdgeInsets.only(bottom: 8), child: ListTile(
              leading: Container(width: 40, height: 40,
                decoration: BoxDecoration(color: AppTheme.error.withOpacity(0.2), borderRadius: BorderRadius.circular(10)),
                child: const Icon(Icons.block, color: AppTheme.error, size: 20)),
              title: Text(e.appName.isNotEmpty ? e.appName : e.packageName, style: const TextStyle(fontWeight: FontWeight.bold, color: AppTheme.textPrimary)),
              subtitle: Text(e.packageName, style: TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
              trailing: Text(_fmt(e.blockedAt), style: TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
            ));
          },
        ));
      }),
    );
  }

  String _fmt(DateTime dt) {
    final diff = DateTime.now().difference(dt);
    if (diff.inMinutes < 1) return '刚刚';
    if (diff.inHours < 1) return '${diff.inMinutes}分钟前';
    if (diff.inDays < 1) return '${diff.inHours}小时前';
    return '${dt.month.toString().padLeft(2,'0')}-${dt.day.toString().padLeft(2,'0')} ${dt.hour.toString().padLeft(2,'0')}:${dt.minute.toString().padLeft(2,'0')}';
  }
}
