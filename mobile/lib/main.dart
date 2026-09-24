import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:provider/provider.dart';

import 'core/config/api_config.dart';
import 'core/network/api_client.dart';
import 'core/storage/local_database.dart';
import 'core/storage/selection_storage.dart';
import 'core/storage/token_storage.dart';
import 'features/assistant/assistant_controller.dart';
import 'features/assistant/assistant_screen.dart';
import 'features/assistant/local_intent_parser.dart';
import 'features/auth/auth_controller.dart';
import 'features/auth/login_screen.dart';
import 'features/offline/connectivity_controller.dart';
import 'features/projects/workspace_controller.dart';
import 'services/project_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  if (kDebugMode) {
    debugPrint('[CASEGen Mobile] API base URL: ${ApiConfig.baseUrl}');
  }

  const tokenStorage = TokenStorage();
  final apiClient = ApiClient(tokenStorage);
  final authController = AuthController(apiClient, tokenStorage);
  apiClient.onUnauthorized = authController.handleUnauthorized;

  final connectivityController = ConnectivityController();
  final workspaceController = WorkspaceController(
    ProjectService(apiClient),
    SelectionStorage(),
  );
  final assistantController = AssistantController(
    const LocalIntentParser(),
    LocalDatabase.instance,
  );

  await authController.restoreSession();
  await connectivityController.initialize();
  await assistantController.initialize();

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: authController),
        ChangeNotifierProvider.value(value: connectivityController),
        ChangeNotifierProvider.value(value: workspaceController),
        ChangeNotifierProvider.value(value: assistantController),
      ],
      child: const CasegenMobileApp(),
    ),
  );
}

class CasegenMobileApp extends StatelessWidget {
  const CasegenMobileApp({super.key});

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xFF5546E8);
    return MaterialApp(
      title: 'CASEGen Mobile',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: seed),
        scaffoldBackgroundColor: const Color(0xFFF7F8FC),
        useMaterial3: true,
        inputDecorationTheme: const InputDecorationTheme(
          filled: true,
          fillColor: Colors.white,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.all(Radius.circular(14)),
            borderSide: BorderSide(color: Color(0xFFDDE1EE)),
          ),
        ),
        cardTheme: const CardThemeData(
          color: Colors.white,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.all(Radius.circular(16)),
            side: BorderSide(color: Color(0xFFE5E7F0)),
          ),
        ),
      ),
      home: Consumer<AuthController>(
        builder: (context, auth, _) {
          if (auth.isRestoring) {
            return const Scaffold(
              body: Center(child: CircularProgressIndicator()),
            );
          }
          return auth.isAuthenticated
              ? const AssistantScreen()
              : const LoginScreen();
        },
      ),
    );
  }
}
