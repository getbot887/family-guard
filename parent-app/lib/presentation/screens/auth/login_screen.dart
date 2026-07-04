import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/providers.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/config/api_config.dart';
import '../../../data/services/api_service.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _isLogin = true, _obscure = true;
  String? _nickname;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (ApiConfig.isDefaultUrl) _editServerUrl(context);
    });
  }

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    final auth = context.read<AuthProvider>();
    final ok = _isLogin
        ? await auth.login(_email.text, _password.text)
        : await auth.register(_email.text, _password.text, _nickname ?? '');
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(auth.error ?? '操作失败'),
        backgroundColor: AppTheme.error,
      ));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Container(
                  width: 100,
                  height: 100,
                  decoration: const BoxDecoration(
                    gradient: AppTheme.primaryGradient,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.shield, size: 50, color: Colors.white),
                ),
                const SizedBox(height: 24),
                const Text('FamilyGuard', style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold, color: AppTheme.textPrimary)),
                const SizedBox(height: 8),
                Text('家长守护，安心成长', style: TextStyle(fontSize: 16, color: AppTheme.textSecondary)),
                const SizedBox(height: 48),
                _buildFormCard(),
                const SizedBox(height: 16),
                TextButton.icon(
                  onPressed: () => _editServerUrl(context),
                  icon: const Icon(Icons.settings_outlined, size: 16),
                  label: Text('服务器: ${ApiConfig.displayUrl}',
                    style: TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildFormCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Form(
          key: _form,
          child: Column(
            children: [
              Row(children: [
                Expanded(
                  child: TextButton(
                    onPressed: () => setState(() => _isLogin = true),
                    child: Text('登录', style: TextStyle(
                      color: _isLogin ? AppTheme.primary : AppTheme.textSecondary,
                      fontWeight: _isLogin ? FontWeight.bold : FontWeight.normal,
                    )),
                  ),
                ),
                Expanded(
                  child: TextButton(
                    onPressed: () => setState(() => _isLogin = false),
                    child: Text('注册', style: TextStyle(
                      color: !_isLogin ? AppTheme.primary : AppTheme.textSecondary,
                      fontWeight: !_isLogin ? FontWeight.bold : FontWeight.normal,
                    )),
                  ),
                ),
              ]),
              const SizedBox(height: 16),
              if (!_isLogin)
                TextFormField(
                  decoration: const InputDecoration(labelText: '昵称', prefixIcon: Icon(Icons.person_outline)),
                  onSaved: (v) => _nickname = v,
                ),
              if (!_isLogin) const SizedBox(height: 16),
              TextFormField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                decoration: const InputDecoration(labelText: '邮箱', prefixIcon: Icon(Icons.email_outlined)),
                validator: (v) => v == null || v.isEmpty ? '请输入邮箱' : null,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _password,
                obscureText: _obscure,
                decoration: InputDecoration(
                  labelText: '密码',
                  prefixIcon: const Icon(Icons.lock_outlined),
                  suffixIcon: IconButton(
                    icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                    onPressed: () => setState(() => _obscure = !_obscure),
                  ),
                ),
                validator: (v) => v == null || v.length < 6 ? '密码至少6位' : null,
              ),
              const SizedBox(height: 24),
              Consumer<AuthProvider>(
                builder: (_, auth, __) {
                  final label = _isLogin ? '登录' : '注册';
                  return ElevatedButton(
                    onPressed: auth.isLoading ? null : _submit,
                    child: auth.isLoading
                        ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                        : Text(label),
                  );
                },
              ),
            ],
          ),
        ),
      ),
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
            ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请输入有效的 URL')));
            return;
          }
          await ApiConfig.setBaseUrl(url);
          ApiService().updateBaseUrl(ApiConfig.baseUrl);
          if (ctx.mounted) Navigator.pop(ctx);
          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('服务器地址已更新'), backgroundColor: AppTheme.success));
            setState(() {});
          }
        }, child: const Text('保存')),
      ],
    ));
  }
}
