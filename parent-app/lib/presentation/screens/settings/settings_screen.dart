import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/providers.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/config/api_config.dart';
import '../../../data/services/api_service.dart';
import '../auth/login_screen.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Consumer<AuthProvider>(builder: (_, a, __) => Card(child: ListTile(
          leading: CircleAvatar(backgroundColor: AppTheme.primary.withOpacity(0.2), child: const Icon(Icons.person, color: AppTheme.primary)),
          title: Text(a.user?.nickname ?? '用户', style: const TextStyle(fontWeight: FontWeight.bold, color: AppTheme.textPrimary)),
          subtitle: Text(a.user?.email ?? '', style: TextStyle(color: AppTheme.textSecondary)),
        ))),
        const SizedBox(height: 16),
        const Text('通用', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppTheme.textSecondary)),
        const SizedBox(height: 8),
        Card(child: Column(children: [
          ListTile(leading: const Icon(Icons.dns_outlined), title: const Text('服务器地址'),
            subtitle: Text(ApiConfig.displayUrl, style: TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
            trailing: const Icon(Icons.chevron_right), onTap: () => _editServerUrl(context)),
          const Divider(height: 1),
          ListTile(leading: const Icon(Icons.notifications_outlined), title: const Text('通知设置'), trailing: const Icon(Icons.chevron_right), onTap: () {}),
          const Divider(height: 1),
          ListTile(leading: const Icon(Icons.dark_mode_outlined), title: const Text('深色模式'),
            trailing: Switch(value: true, onChanged: (v) {}, activeColor: AppTheme.primary)),
        ])),
        const SizedBox(height: 16),
        const Text('关于', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppTheme.textSecondary)),
        const SizedBox(height: 8),
        Card(child: Column(children: [
          ListTile(leading: const Icon(Icons.info_outline), title: const Text('关于 FamilyGuard'), trailing: const Icon(Icons.chevron_right), onTap: () => _about(context)),
          const Divider(height: 1),
          ListTile(leading: const Icon(Icons.description_outlined), title: const Text('隐私政策'), trailing: const Icon(Icons.chevron_right), onTap: () {}),
        ])),
        const SizedBox(height: 24),
        ElevatedButton(onPressed: () => _logout(context), style: ElevatedButton.styleFrom(backgroundColor: AppTheme.error), child: const Text('退出登录')),
        const SizedBox(height: 16),
        Center(child: Text('Version 1.0.0', style: TextStyle(color: AppTheme.textSecondary, fontSize: 12))),
      ]),
    );
  }

  void _editServerUrl(BuildContext context) {
    final controller = TextEditingController(text: ApiConfig.displayUrl);
    showDialog(context: context, builder: (ctx) => AlertDialog(
      title: const Text('服务器地址'),
      content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('填写主域名，/api/v1 会自动拼接', style: TextStyle(fontSize: 12)),
        const SizedBox(height: 12),
        TextField(controller: controller, decoration: const InputDecoration(
          hintText: 'https://your-domain.com',
          prefixIcon: Icon(Icons.link),
        )),
      ]),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
        ElevatedButton(onPressed: () async {
          final url = controller.text.trim();
          if (url.isEmpty || !url.startsWith('http')) {
            ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请输入有效的 URL（以 http/https 开头）')));
            return;
          }
          await ApiConfig.setBaseUrl(url);
          ApiService().updateBaseUrl(ApiConfig.baseUrl);
          if (ctx.mounted) Navigator.pop(ctx);
          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('服务器地址已更新'), backgroundColor: AppTheme.success));
            // 触发刷新以显示新地址
            (context as Element).markNeedsBuild();
          }
        }, child: const Text('保存')),
      ],
    ));
  }

  void _about(BuildContext context) => showDialog(context: context, builder: (ctx) => AlertDialog(
    title: const Text('关于 FamilyGuard'), content: const Text('家长控制应用，帮助家长管理孩子的设备使用。', style: TextStyle(color: AppTheme.textSecondary)),
    actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('关闭'))],
  ));

  void _logout(BuildContext context) => showDialog(context: context, builder: (ctx) => AlertDialog(
    title: const Text('退出登录'), content: const Text('确定要退出吗？'), actions: [
      TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
      ElevatedButton(onPressed: () async {
        await context.read<AuthProvider>().logout();
        if (context.mounted) Navigator.pushAndRemoveUntil(context, MaterialPageRoute(builder: (_) => const LoginScreen()), (route) => false);
      }, style: ElevatedButton.styleFrom(backgroundColor: AppTheme.error), child: const Text('退出')),
    ],
  ));
}
