import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../providers/providers.dart';
import '../../../core/theme/app_theme.dart';
import '../home/home_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _isLogin = true, _obscure = true;
  String? _nickname;

  @override void dispose() { _email.dispose(); _password.dispose(); super.dispose(); }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    final auth = context.read<AuthProvider>();
    final ok = _isLogin ? await auth.login(_email.text, _password.text)
                        : await auth.register(_email.text, _password.text, _nickname ?? '');
    if (ok && mounted) {
      // Provider 自动切换页面，无需手动导航
    } else if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(auth.error ?? '操作失败'), backgroundColor: AppTheme.error));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(body: SafeArea(child: Center(child: SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        Container(width: 100, height: 100, decoration: const BoxDecoration(gradient: AppTheme.primaryGradient, shape: BoxShape.circle),
          child: const Icon(Icons.shield, size: 50, color: Colors.white)),
        const SizedBox(height: 24),
        const Text('FamilyGuard', style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold, color: AppTheme.textPrimary)),
        const SizedBox(height: 8),
        Text('家长守护，安心成长', style: TextStyle(fontSize: 16, color: AppTheme.textSecondary)),
        const SizedBox(height: 48),
        Card(child: Padding(padding: const EdgeInsets.all(24), child: Form(key: _form, child: Column(children: [
          Row(children: [
            Expanded(child: TextButton(onPressed: () => setState(() => _isLogin = true),
              child: Text('登录', style: TextStyle(color: _isLogin ? AppTheme.primary : AppTheme.textSecondary, fontWeight: _isLogin ? FontWeight.bold : FontWeight.normal)))),
            Expanded(child: TextButton(onPressed: () => setState(() => _isLogin = false),
              child: Text('注册', style: TextStyle(color: !_isLogin ? AppTheme.primary : AppTheme.textSecondary, fontWeight: !_isLogin ? FontWeight.bold : FontWeight.normal)))),
          ]),
          const SizedBox(height: 16),
          if (!_isLogin) ...[
            TextFormField(decoration: const InputDecoration(labelText: '昵称', prefixIcon: Icon(Icons.person_outline)),
              onSaved: (v) => _nickname = v),
            const SizedBox(height: 16),
          ],
          TextFormField(controller: _email, keyboardType: TextInputType.emailAddress,
            decoration: const InputDecoration(labelText: '邮箱', prefixIcon: Icon(Icons.email_outlined)),
            validator: (v) => v == null || v.isEmpty ? '请输入邮箱' : null),
          const SizedBox(height: 16),
          TextFormField(controller: _password, obscureText: _obscure,
            decoration: InputDecoration(labelText: '密码', prefixIcon: const Icon(Icons.lock_outlined),
              suffixIcon: IconButton(icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined), onPressed: () => setState(() => _obscure = !_obscure))),
            validator: (v) => v == null || v.length < 6 ? '密码至少6位' : null),
          const SizedBox(height: 24),
          Consumer<AuthProvider>(builder: (_, a, __) => ElevatedButton(
            onPressed: a.isLoading ? null : _submit,
            child: a.isLoading ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                                : Text(_isLogin ? '登录' : '注册'))),
        ]))))),
      ]),
    ))));
  }
}
