import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/providers.dart';
import '../../../core/theme/app_theme.dart';

class DevicesScreen extends StatelessWidget {
  const DevicesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('设备管理'), actions: [
        IconButton(icon: const Icon(Icons.qr_code_scanner), onPressed: () => _bind(context)),
      ]),
      body: Consumer<DeviceProvider>(builder: (_, dp, __) {
        if (dp.isLoading) return const Center(child: CircularProgressIndicator());
        if (dp.devices.isEmpty) return Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          Icon(Icons.phone_android_outlined, size: 80, color: AppTheme.textSecondary.withOpacity(0.5)),
          const SizedBox(height: 16), Text('暂无绑定设备', style: TextStyle(fontSize: 18, color: AppTheme.textSecondary)),
          const SizedBox(height: 24),
          ElevatedButton.icon(onPressed: () => _bind(context), icon: const Icon(Icons.add), label: const Text('绑定设备')),
        ]));
        return RefreshIndicator(onRefresh: () => dp.fetch(), child: ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: dp.devices.length,
          itemBuilder: (_, i) {
            final d = dp.devices[i];
            return Card(margin: const EdgeInsets.only(bottom: 12), child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Container(width: 48, height: 48,
                    decoration: BoxDecoration(color: (d.isOnline ? AppTheme.success : AppTheme.textSecondary).withOpacity(0.2), borderRadius: BorderRadius.circular(12)),
                    child: Icon(Icons.phone_android, color: d.isOnline ? AppTheme.success : AppTheme.textSecondary)),
                  const SizedBox(width: 12),
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(d.deviceName, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.textPrimary)),
                    Text(d.model, style: TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
                  ])),
                  Container(padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(color: (d.isOnline ? AppTheme.success : AppTheme.textSecondary).withOpacity(0.2), borderRadius: BorderRadius.circular(12)),
                    child: Text(d.isOnline ? '在线' : '离线', style: TextStyle(fontSize: 12, color: d.isOnline ? AppTheme.success : AppTheme.textSecondary))),
                ]),
                if (d.lastSeenAt != null) ...[
                  const SizedBox(height: 8),
                  Text('最后在线: ${_fmt(d.lastSeenAt!)}', style: TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
                ],
                Row(mainAxisAlignment: MainAxisAlignment.end, children: [
                  TextButton(onPressed: () {}, child: const Text('应用列表')),
                  TextButton(onPressed: () async {
                    if (await dp.unbind(d.id) && context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('设备已解绑'), backgroundColor: AppTheme.success));
                    }
                  }, child: Text('解绑', style: TextStyle(color: AppTheme.error))),
                ]),
              ]),
            ));
          },
        ));
      }),
    );
  }

  void _bind(BuildContext context) {
    final idCtrl = TextEditingController(); final codeCtrl = TextEditingController();
    showDialog(context: context, builder: (ctx) => AlertDialog(
      title: const Text('绑定设备'), content: Column(mainAxisSize: MainAxisSize.min, children: [
        TextField(controller: codeCtrl, decoration: const InputDecoration(labelText: '配对码', hintText: '6位数字'), maxLength: 6),
        TextField(controller: idCtrl, decoration: const InputDecoration(labelText: '设备ID')),
      ]), actions: [
        TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
        ElevatedButton(onPressed: () async {
          if (codeCtrl.text.length == 6) {
            final ok = await context.read<DeviceProvider>().bind(idCtrl.text, codeCtrl.text);
            if (ok && ctx.mounted) { Navigator.pop(ctx); ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('绑定成功'), backgroundColor: AppTheme.success)); }
          }
        }, child: const Text('绑定')),
      ],
    ));
  }

  String _fmt(DateTime dt) => '${dt.year}-${dt.month.toString().padLeft(2,'0')}-${dt.day.toString().padLeft(2,'0')} ${dt.hour.toString().padLeft(2,'0')}:${dt.minute.toString().padLeft(2,'0')}';
}
