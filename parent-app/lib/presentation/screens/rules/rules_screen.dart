import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/providers.dart';
import '../../../core/theme/app_theme.dart';

class RulesScreen extends StatelessWidget {
  const RulesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('规则管理'), actions: [
        IconButton(icon: const Icon(Icons.add), onPressed: () => _create(context)),
      ]),
      body: Consumer<RuleProvider>(builder: (_, rp, __) {
        if (rp.isLoading) return const Center(child: CircularProgressIndicator());
        if (rp.rules.isEmpty) return Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          Icon(Icons.rule_outlined, size: 80, color: AppTheme.textSecondary.withOpacity(0.5)),
          const SizedBox(height: 16), Text('暂无规则', style: TextStyle(fontSize: 18, color: AppTheme.textSecondary)),
          const SizedBox(height: 24),
          ElevatedButton.icon(onPressed: () => _create(context), icon: const Icon(Icons.add), label: const Text('创建规则')),
        ]));
        return RefreshIndicator(onRefresh: () => rp.fetch(), child: ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: rp.rules.length,
          itemBuilder: (_, i) {
            final r = rp.rules[i];
            return Card(margin: const EdgeInsets.only(bottom: 12), child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Container(width: 48, height: 48,
                    decoration: BoxDecoration(color: (r.isActive ? AppTheme.primary : AppTheme.textSecondary).withOpacity(0.2), borderRadius: BorderRadius.circular(12)),
                    child: Icon(Icons.rule, color: r.isActive ? AppTheme.primary : AppTheme.textSecondary)),
                  const SizedBox(width: 12),
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(r.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.textPrimary)),
                    Text('${r.apps.length} 个应用, ${r.schedules.length} 个时段', style: TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
                  ])),
                  Switch(value: r.isActive, onChanged: (v) => rp.toggle(r.id, v), activeColor: AppTheme.primary),
                ]),
                if (r.apps.isNotEmpty) ...[
                  const SizedBox(height: 8), const Divider(), const SizedBox(height: 8),
                  Wrap(spacing: 8, children: r.apps.map((a) => Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(color: AppTheme.error.withOpacity(0.2), borderRadius: BorderRadius.circular(8)),
                    child: Text(a.appName.isNotEmpty ? a.appName : a.packageName, style: TextStyle(fontSize: 12, color: AppTheme.error)),
                  )).toList()),
                ],
                if (r.schedules.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  ...r.schedules.map((s) => Text('${_days(s.daysOfWeek)} ${s.startTime}-${s.endTime}', style: TextStyle(fontSize: 12, color: AppTheme.textSecondary))),
                ],
                const SizedBox(height: 8),
                Row(mainAxisAlignment: MainAxisAlignment.end, children: [
                  TextButton(onPressed: () {}, child: const Text('编辑')),
                  TextButton(onPressed: () async {
                    if (await rp.delete(r.id) && context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('规则已删除'), backgroundColor: AppTheme.success));
                    }
                  }, child: Text('删除', style: TextStyle(color: AppTheme.error))),
                ]),
              ]),
            ));
          },
        ));
      }),
    );
  }

  void _create(BuildContext context) {
    // 简化创建弹窗，完整版应有选择App和时间段的功能
    final nameCtrl = TextEditingController();
    showDialog(context: context, builder: (ctx) => AlertDialog(
      title: const Text('创建规则'), content: Column(mainAxisSize: MainAxisSize.min, children: [
        TextField(controller: nameCtrl, decoration: const InputDecoration(labelText: '规则名称', hintText: '例如：学习时间')),
        const SizedBox(height: 16),
        Text('完整创建功能需加载设备App列表\n请在后续版本中实现', style: TextStyle(color: AppTheme.textSecondary)),
      ]), actions: [
        TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
        ElevatedButton(onPressed: () async {
          if (nameCtrl.text.isNotEmpty) {
            final ok = await context.read<RuleProvider>().create(nameCtrl.text, [], [], []);
            if (ok && ctx.mounted) { Navigator.pop(ctx); ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('规则已创建'), backgroundColor: AppTheme.success)); }
          }
        }, child: const Text('创建')),
      ],
    ));
  }

  String _days(List<int> d) {
    const names = ['周一','周二','周三','周四','周五','周六','周日'];
    if (d.length == 7) return '每天';
    if (d.length == 5 && !d.contains(6) && !d.contains(7)) return '工作日';
    return d.map((n) => names[n-1]).join(',');
  }
}
