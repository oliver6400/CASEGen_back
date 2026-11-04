package com.casegen.generation.frontend.service;

import com.casegen.generation.logical.dto.LogicalModelDtos.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Deterministic React Native (Expo Go) client generated from an immutable logical model. */
@Component
public class ReactNativeFrontendRenderer {
    public record Traceability(String modelName, long modelVersionNumber) {}

    public void render(Path root, Response input, Traceability trace) throws IOException {
        JsonNode profile = input.profileSnapshot().path("profile");
        String appName = safeAppName(profile.path("artifactId").asString("generated-app"));
        List<Table> generatedTables = SpringTables.generated(input.tables());

        put(root, "package.json", packageJson(appName));
        put(root, "app.json", appJson(appName));
        put(root, "tsconfig.json", tsconfig());
        put(root, "babel.config.js", "module.exports = { presets: ['babel-preset-expo'] };\n");
        put(root, ".env.example", "EXPO_PUBLIC_API_BASE_URL=http://localhost:8080\n# Android emulator: http://10.0.2.2:8080\n# Dispositivo fisico con Expo Go: http://TU_IP_LAN:8080\n");
        put(root, "src/config.ts", configTs());
        put(root, "src/theme.ts", themeTs());
        put(root, "src/db/sqlite.ts", sqliteTs(generatedTables));
        put(root, "src/db/SyncQueueService.ts", syncQueueServiceTs());
        put(root, "src/api/client.ts", client());
        put(root, "src/auth/AuthContext.tsx", authContext());
        put(root, "src/components/VoiceAssistantModal.tsx", voiceAssistantModal(generatedTables));
        put(root, "src/screens/LoginScreen.tsx", loginScreen(appName, trace));
        put(root, "src/screens/HomeScreen.tsx", homeScreen(appName, trace, generatedTables));
        put(root, "App.tsx", app());
        put(root, "README.md", readme(appName, trace));

        for (Table table : generatedTables) {
            String resource = resourceName(table.name());
            String screen = screenName(table.name());
            put(root, "src/screens/" + screen + "Screen.tsx", crudScreen(table, resource, screen));
        }

        put(root, "src/navigation.tsx", navigation(generatedTables));
    }

    private String packageJson(String appName) {
        return "{\n" +
                "  \"name\": \"" + appName + "\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"main\": \"expo/AppEntry.js\",\n" +
                "  \"private\": true,\n" +
                "  \"scripts\": {\n" +
                "    \"start\": \"expo start\",\n" +
                "    \"android\": \"expo start --android\",\n" +
                "    \"web\": \"expo start --web\",\n" +
                "    \"typecheck\": \"tsc --noEmit\"\n" +
                "  },\n" +
                "  \"dependencies\": {\n" +
                "    \"@react-navigation/native\": \"^7.1.17\",\n" +
                "    \"@react-navigation/native-stack\": \"^7.3.26\",\n" +
                "    \"expo\": \"~54.0.0\",\n" +
                "    \"expo-speech\": \"~14.0.7\",\n" +
                "    \"expo-sqlite\": \"~15.0.0\",\n" +
                "    \"expo-status-bar\": \"~3.0.8\",\n" +
                "    \"react\": \"19.1.0\",\n" +
                "    \"react-dom\": \"^19.1.0\",\n" +
                "    \"react-native\": \"0.81.4\",\n" +
                "    \"react-native-safe-area-context\": \"^5.6.1\",\n" +
                "    \"react-native-screens\": \"~4.16.0\",\n" +
                "    \"react-native-web\": \"^0.21.4\"\n" +
                "  },\n" +
                "  \"devDependencies\": {\n" +
                "    \"@types/react\": \"~19.1.10\",\n" +
                "    \"babel-preset-expo\": \"^58.0.23\",\n" +
                "    \"typescript\": \"~5.9.2\"\n" +
                "  }\n" +
                "}\n";
    }

    private String appJson(String appName) {
        return "{\n" +
                "  \"expo\": {\n" +
                "    \"name\": \"" + appName + "\",\n" +
                "    \"slug\": \"" + appName + "\",\n" +
                "    \"scheme\": \"" + appName + "\",\n" +
                "    \"version\": \"1.0.0\",\n" +
                "    \"orientation\": \"portrait\",\n" +
                "    \"userInterfaceStyle\": \"light\",\n" +
                "    \"platforms\": [\"android\", \"ios\", \"web\"]\n" +
                "  }\n" +
                "}\n";
    }

    private String tsconfig() {
        return "{ \"extends\": \"expo/tsconfig.base\", \"compilerOptions\": { \"strict\": true, \"noEmit\": true }, \"include\": [\"**/*.ts\", \"**/*.tsx\"] }\n";
    }

    private String configTs() {
        return "import { NativeModules, Platform } from 'react-native';\n\n" +
                "function detectDefaultBaseUrl(): string {\n" +
                "  if (process.env.EXPO_PUBLIC_API_BASE_URL) {\n" +
                "    return process.env.EXPO_PUBLIC_API_BASE_URL.replace(/\\/+$/, '');\n" +
                "  }\n" +
                "  try {\n" +
                "    const scriptURL: string | undefined = NativeModules?.SourceCode?.scriptURL;\n" +
                "    if (scriptURL) {\n" +
                "      const match = scriptURL.match(/^https?:\\/\\/([^/:]+):\\d+/);\n" +
                "      if (match && match[1] && match[1] !== 'localhost' && match[1] !== '127.0.0.1') {\n" +
                "        return `http://${match[1]}:8080`;\n" +
                "      }\n" +
                "    }\n" +
                "  } catch {\n" +
                "    // ignore detection errors\n" +
                "  }\n" +
                "  const localhost = Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';\n" +
                "  return localhost;\n" +
                "}\n\n" +
                "export const API_BASE_URL = detectDefaultBaseUrl();\n";
    }

    private String themeTs() {
        return "export const theme = {\n" +
                "  colors: {\n" +
                "    primary: '#1E3A8A',\n" +
                "    primaryLight: '#2563EB',\n" +
                "    primarySoft: '#EFF6FF',\n" +
                "    secondary: '#0D9488',\n" +
                "    secondarySoft: '#F0FDFA',\n" +
                "    ai: '#7C3AED',\n" +
                "    aiSoft: '#F5F3FF',\n" +
                "    danger: '#DC2626',\n" +
                "    dangerSoft: '#FEF2F2',\n" +
                "    success: '#16A34A',\n" +
                "    successSoft: '#F0FDF4',\n" +
                "    background: '#F1F5F9',\n" +
                "    surface: '#FFFFFF',\n" +
                "    border: '#CBD5E1',\n" +
                "    text: '#0F172A',\n" +
                "    textMuted: '#475569',\n" +
                "  },\n" +
                "};\n";
    }

    private String client() {
        return "import { API_BASE_URL } from '../config';\n\n" +
                "let accessToken: string | null = null;\n" +
                "let runtimeBaseUrl: string = API_BASE_URL;\n\n" +
                "export function setAccessToken(token: string | null) { accessToken = token; }\n" +
                "export function getApiBaseUrl(): string { return runtimeBaseUrl; }\n" +
                "export function setApiBaseUrl(url: string) { runtimeBaseUrl = url.trim().replace(/\\/+$/, ''); }\n\n" +
                "export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {\n" +
                "  const response = await fetch(`${runtimeBaseUrl}${path}`, {\n" +
                "    ...init,\n" +
                "    headers: {\n" +
                "      'Content-Type': 'application/json',\n" +
                "      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),\n" +
                "      ...init.headers,\n" +
                "    },\n" +
                "  });\n" +
                "  if (!response.ok) {\n" +
                "    const raw = await response.text();\n" +
                "    try {\n" +
                "      const parsed = JSON.parse(raw) as { message?: string };\n" +
                "      if (parsed.message) throw new Error(parsed.message);\n" +
                "    } catch (e) {\n" +
                "      if (e instanceof Error && !raw.startsWith('{')) throw new Error(`${response.status} ${raw}`);\n" +
                "      if (e instanceof Error) throw e;\n" +
                "    }\n" +
                "    throw new Error(`${response.status} ${raw}`);\n" +
                "  }\n" +
                "  if (response.status === 204) return undefined as T;\n" +
                "  return response.json() as Promise<T>;\n" +
                "}\n";
    }

    private String authContext() {
        return "import React, { createContext, useContext, useState } from 'react';\n" +
                "import { api, setAccessToken } from '../api/client';\n\n" +
                "type AuthUser = { email: string; displayName: string };\n" +
                "type AuthContextValue = {\n" +
                "  authenticated: boolean;\n" +
                "  user: AuthUser | null;\n" +
                "  login: (email: string, password: string) => Promise<void>;\n" +
                "  register: (displayName: string, email: string, password: string) => Promise<void>;\n" +
                "  logout: () => void;\n" +
                "};\n" +
                "const AuthContext = createContext<AuthContextValue | null>(null);\n\n" +
                "export function AuthProvider({ children }: { children: React.ReactNode }) {\n" +
                "  const [authenticated, setAuthenticated] = useState(false);\n" +
                "  const [user, setUser] = useState<AuthUser | null>(null);\n" +
                "  const login = async (email: string, password: string) => {\n" +
                "    const result = await api<{ accessToken: string; email?: string; displayName?: string }>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });\n" +
                "    setAccessToken(result.accessToken);\n" +
                "    setUser({ email: result.email ?? email, displayName: result.displayName ?? email });\n" +
                "    setAuthenticated(true);\n" +
                "  };\n" +
                "  const register = async (displayName: string, email: string, password: string) => {\n" +
                "    const result = await api<{ accessToken: string; email?: string; displayName?: string }>('/api/auth/register', { method: 'POST', body: JSON.stringify({ displayName, email, password }) });\n" +
                "    setAccessToken(result.accessToken);\n" +
                "    setUser({ email: result.email ?? email, displayName: result.displayName ?? displayName });\n" +
                "    setAuthenticated(true);\n" +
                "  };\n" +
                "  const logout = () => {\n" +
                "    setAccessToken(null);\n" +
                "    setUser(null);\n" +
                "    setAuthenticated(false);\n" +
                "  };\n" +
                "  return <AuthContext.Provider value={{ authenticated, user, login, register, logout }}>{children}</AuthContext.Provider>;\n" +
                "}\n\n" +
                "export function useAuth() {\n" +
                "  const value = useContext(AuthContext);\n" +
                "  if (!value) throw new Error('AuthProvider required');\n" +
                "  return value;\n" +
                "}\n";
    }

    private String loginScreen(String appName, Traceability trace) {
        return "import React, { useState } from 'react';\n" +
                "import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';\n" +
                "import { useAuth } from '../auth/AuthContext';\n" +
                "import { getApiBaseUrl, setApiBaseUrl } from '../api/client';\n" +
                "import { theme } from '../theme';\n\n" +
                "export function LoginScreen() {\n" +
                "  const { login, register } = useAuth();\n" +
                "  const [mode, setMode] = useState<'login' | 'register'>('login');\n" +
                "  const [displayName, setDisplayName] = useState('');\n" +
                "  const [email, setEmail] = useState('admin@demo.com');\n" +
                "  const [password, setPassword] = useState('admin1234');\n" +
                "  const [serverUrl, setServerUrl] = useState(getApiBaseUrl());\n" +
                "  const [showServerConfig, setShowServerConfig] = useState(false);\n" +
                "  const [error, setError] = useState<string>();\n" +
                "  const [busy, setBusy] = useState(false);\n\n" +
                "  const submit = async () => {\n" +
                "    setBusy(true);\n" +
                "    setError(undefined);\n" +
                "    setApiBaseUrl(serverUrl);\n" +
                "    try {\n" +
                "      if (mode === 'register') {\n" +
                "        await register(displayName, email, password);\n" +
                "      } else {\n" +
                "        await login(email, password);\n" +
                "      }\n" +
                "    } catch (reason) {\n" +
                "      setError(reason instanceof Error ? reason.message : 'No se pudo conectar con el servidor');\n" +
                "    } finally {\n" +
                "      setBusy(false);\n" +
                "    }\n" +
                "  };\n\n" +
                "  const quickDemoLogin = async () => {\n" +
                "    setEmail('admin@demo.com');\n" +
                "    setPassword('admin1234');\n" +
                "    setBusy(true);\n" +
                "    setError(undefined);\n" +
                "    setApiBaseUrl(serverUrl);\n" +
                "    try {\n" +
                "      await login('admin@demo.com', 'admin1234');\n" +
                "    } catch {\n" +
                "      try {\n" +
                "        await register('Administrador Demo', 'admin@demo.com', 'admin1234');\n" +
                "      } catch (reason) {\n" +
                "        setError(reason instanceof Error ? reason.message : 'Verifique que el backend este corriendo en ' + serverUrl);\n" +
                "      }\n" +
                "    } finally {\n" +
                "      setBusy(false);\n" +
                "    }\n" +
                "  };\n\n" +
                "  return (\n" +
                "    <ScrollView contentContainerStyle={styles.scroll}>\n" +
                "      <View style={styles.hero}>\n" +
                "        <Text style={styles.badge}>SISTEMA EMPRESARIAL · v" + trace.modelVersionNumber() + "</Text>\n" +
                "        <Text style={styles.heroTitle}>" + trace.modelName() + "</Text>\n" +
                "        <Text style={styles.heroSub}>Aplicación Móvil + Agente IA por Voz (" + appName + ")</Text>\n" +
                "      </View>\n" +
                "      <View style={styles.card}>\n" +
                "        <Text style={styles.title}>{mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}</Text>\n" +
                "        {mode === 'register' && (\n" +
                "          <View style={styles.fieldGroup}>\n" +
                "            <Text style={styles.label}>Nombre completo</Text>\n" +
                "            <TextInput placeholder='Nombre' style={styles.input} value={displayName} onChangeText={setDisplayName} />\n" +
                "          </View>\n" +
                "        )}\n" +
                "        <View style={styles.fieldGroup}>\n" +
                "          <Text style={styles.label}>Correo electrónico</Text>\n" +
                "          <TextInput placeholder='Email' autoCapitalize='none' keyboardType='email-address' style={styles.input} value={email} onChangeText={setEmail} />\n" +
                "        </View>\n" +
                "        <View style={styles.fieldGroup}>\n" +
                "          <Text style={styles.label}>Contraseña</Text>\n" +
                "          <TextInput placeholder='Contraseña' secureTextEntry style={styles.input} value={password} onChangeText={setPassword} />\n" +
                "        </View>\n" +
                "        {error && <View style={styles.errorBox}><Text style={styles.error}>{error}</Text></View>}\n" +
                "        {busy ? (\n" +
                "          <ActivityIndicator size='large' color={theme.colors.primaryLight} style={{ marginVertical: 16 }} />\n" +
                "        ) : (\n" +
                "          <>\n" +
                "            <Pressable style={styles.primaryBtn} onPress={() => void submit()}>\n" +
                "              <Text style={styles.primaryBtnText}>{mode === 'login' ? 'Entrar al Sistema' : 'Crear cuenta'}</Text>\n" +
                "            </Pressable>\n" +
                "            <Pressable style={styles.demoBtn} onPress={() => void quickDemoLogin()}>\n" +
                "              <Text style={styles.demoBtnText}>Acceso Rápido Demo (admin@demo.com)</Text>\n" +
                "            </Pressable>\n" +
                "            <Pressable style={styles.switchBtn} onPress={() => setMode(mode === 'login' ? 'register' : 'login')}>\n" +
                "              <Text style={styles.switchText}>{mode === 'login' ? 'Registrarme' : 'Ya tengo cuenta'}</Text>\n" +
                "            </Pressable>\n" +
                "          </>\n" +
                "        )}\n" +
                "        <View style={styles.serverSection}>\n" +
                "          <Pressable onPress={() => setShowServerConfig(!showServerConfig)}>\n" +
                "            <Text style={styles.serverToggle}>Servidor Backend: {serverUrl} (Cambiar IP)</Text>\n" +
                "          </Pressable>\n" +
                "          {showServerConfig && (\n" +
                "            <View style={{ marginTop: 8 }}>\n" +
                "              <Text style={styles.serverHint}>En Expo Go con celular físico, usa la IP Wi-Fi de tu PC (ej. http://192.168.1.15:8080):</Text>\n" +
                "              <TextInput value={serverUrl} onChangeText={(v) => { setServerUrl(v); setApiBaseUrl(v); }} placeholder='http://192.168.1.X:8080' autoCapitalize='none' style={styles.input} />\n" +
                "            </View>\n" +
                "          )}\n" +
                "        </View>\n" +
                "      </View>\n" +
                "    </ScrollView>\n" +
                "  );\n" +
                "}\n\n" +
                "const styles = StyleSheet.create({\n" +
                "  scroll: { flexGrow: 1, backgroundColor: theme.colors.background, justifyContent: 'center', padding: 20 },\n" +
                "  hero: { backgroundColor: theme.colors.primary, borderRadius: 16, padding: 24, marginBottom: 16, shadowColor: '#000', shadowOpacity: 0.15, shadowRadius: 8, elevation: 4 },\n" +
                "  badge: { color: '#93C5FD', fontSize: 11, fontWeight: '700', letterSpacing: 1, marginBottom: 6 },\n" +
                "  heroTitle: { color: '#FFFFFF', fontSize: 26, fontWeight: '800' },\n" +
                "  heroSub: { color: '#E2E8F0', fontSize: 13, marginTop: 4 },\n" +
                "  card: { backgroundColor: theme.colors.surface, borderRadius: 16, padding: 24, borderWidth: 1, borderColor: theme.colors.border, shadowColor: '#000', shadowOpacity: 0.06, shadowRadius: 6, elevation: 2 },\n" +
                "  title: { fontSize: 20, fontWeight: '700', color: theme.colors.text, marginBottom: 16 },\n" +
                "  fieldGroup: { marginBottom: 12 },\n" +
                "  label: { fontSize: 13, fontWeight: '600', color: theme.colors.textMuted, marginBottom: 4 },\n" +
                "  input: { borderWidth: 1, borderColor: theme.colors.border, backgroundColor: '#F8FAFC', borderRadius: 10, paddingHorizontal: 14, paddingVertical: 11, fontSize: 15, color: theme.colors.text },\n" +
                "  errorBox: { backgroundColor: theme.colors.dangerSoft, borderRadius: 8, padding: 10, marginBottom: 12, borderWidth: 1, borderColor: '#FECACA' },\n" +
                "  error: { color: theme.colors.danger, fontSize: 13, fontWeight: '600' },\n" +
                "  primaryBtn: { backgroundColor: theme.colors.primaryLight, borderRadius: 10, paddingVertical: 13, alignItems: 'center', marginTop: 4 },\n" +
                "  primaryBtnText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },\n" +
                "  demoBtn: { backgroundColor: theme.colors.secondarySoft, borderWidth: 1, borderColor: theme.colors.secondary, borderRadius: 10, paddingVertical: 11, alignItems: 'center', marginTop: 10 },\n" +
                "  demoBtnText: { color: theme.colors.secondary, fontSize: 14, fontWeight: '700' },\n" +
                "  switchBtn: { paddingVertical: 12, alignItems: 'center', marginTop: 4 },\n" +
                "  switchText: { color: theme.colors.primaryLight, fontSize: 14, fontWeight: '600' },\n" +
                "  serverSection: { marginTop: 14, paddingTop: 12, borderTopWidth: 1, borderTopColor: '#E2E8F0' },\n" +
                "  serverToggle: { fontSize: 12, color: theme.colors.textMuted, textAlign: 'center', textDecorationLine: 'underline' },\n" +
                "  serverHint: { fontSize: 12, color: theme.colors.textMuted, marginBottom: 6 },\n" +
                "});\n";
    }

    private String homeScreen(String appName, Traceability trace, List<Table> tables) {
        StringBuilder cards = new StringBuilder();
        for (Table table : tables) {
            String screen = screenName(table.name());
            String resource = resourceName(table.name());
            int colCount = table.columns().size();
            cards.append("        {\n")
                    .append("          name: '").append(screen).append("',\n")
                    .append("          table: '").append(table.name()).append("',\n")
                    .append("          endpoint: '/api/").append(resource).append("',\n")
                    .append("          fields: ").append(colCount).append(",\n")
                    .append("        },\n");
        }
        return "import React, { useEffect, useState } from 'react';\n" +
                "import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';\n" +
                "import { useAuth } from '../auth/AuthContext';\n" +
                "import { getApiBaseUrl } from '../api/client';\n" +
                "import { VoiceAssistantModal } from '../components/VoiceAssistantModal';\n" +
                "import { SyncQueueService } from '../db/SyncQueueService';\n" +
                "import { theme } from '../theme';\n\n" +
                "const MODULES = [\n" + cards + "];\n\n" +
                "export function HomeScreen({ navigation }: { navigation: { navigate: (screen: string) => void } }) {\n" +
                "  const { user, logout } = useAuth();\n" +
                "  const [voiceOpen, setVoiceOpen] = useState(false);\n" +
                "  const [pendingCount, setPendingCount] = useState(0);\n" +
                "  const [syncing, setSyncing] = useState(false);\n\n" +
                "  useEffect(() => {\n" +
                "    return SyncQueueService.subscribe((count, isSync) => {\n" +
                "      setPendingCount(count);\n" +
                "      setSyncing(isSync);\n" +
                "    });\n" +
                "  }, []);\n\n" +
                "  const triggerSync = async () => {\n" +
                "    try {\n" +
                "      const res = await SyncQueueService.syncPendingQueue();\n" +
                "      Alert.alert('Sincronización Offline', `Operaciones: ${res.total}\\nSincronizadas: ${res.synced}\\nFallidas: ${res.failed}`);\n" +
                "    } catch (e) {\n" +
                "      Alert.alert('Error', String(e));\n" +
                "    }\n" +
                "  };\n\n" +
                "  return (\n" +
                "    <ScrollView style={styles.container} contentContainerStyle={styles.content}>\n" +
                "      <View style={styles.banner}>\n" +
                "        <View style={{ flex: 1 }}>\n" +
                "          <Text style={styles.bannerTag}>PANEL PRINCIPAL · v" + trace.modelVersionNumber() + "</Text>\n" +
                "          <Text style={styles.bannerTitle}>" + trace.modelName() + "</Text>\n" +
                "          <Text style={styles.bannerUser}>Usuario: {user?.displayName ?? 'Administrador'} ({user?.email ?? 'activo'})</Text>\n" +
                "          <Text style={styles.bannerServer}>API: {getApiBaseUrl()}</Text>\n" +
                "        </View>\n" +
                "        <Pressable style={styles.logoutBtn} onPress={logout}>\n" +
                "          <Text style={styles.logoutText}>Salir</Text>\n" +
                "        </Pressable>\n" +
                "      </View>\n\n" +
                "      <View style={pendingCount > 0 ? styles.syncCardWarning : styles.syncCardOnline}>\n" +
                "        <View style={{ flex: 1 }}>\n" +
                "          <Text style={pendingCount > 0 ? styles.syncTitleWarning : styles.syncTitleOnline}>\n" +
                "            {pendingCount > 0 ? `Modo Offline · ${pendingCount} operaciones en cola SQLite` : '🟢 En Línea · Todo sincronizado'}\n" +
                "          </Text>\n" +
                "          <Text style={styles.syncSub}>\n" +
                "            {pendingCount > 0 ? 'Los cambios se guardan localmente en SQLite y se sincronizarán a PostgreSQL.' : 'Base de datos PostgreSQL conectada y actualizada.'}\n" +
                "          </Text>\n" +
                "        </View>\n" +
                "        <Pressable style={styles.syncBtn} onPress={() => void triggerSync()} disabled={syncing}>\n" +
                "          <Text style={styles.syncBtnText}>{syncing ? 'Sincronizando...' : (pendingCount > 0 ? 'Sincronizar Ya' : 'Comprobar')}</Text>\n" +
                "        </Pressable>\n" +
                "      </View>\n\n" +
                "      <Pressable style={styles.aiCard} onPress={() => setVoiceOpen(true)}>\n" +
                "        <View style={styles.aiHeader}>\n" +
                "          <View style={styles.aiBadge}><Text style={styles.aiBadgeText}>IA LOCAL POR VOZ</Text></View>\n" +
                "          <Text style={styles.aiAction}>Abrir Micrófono →</Text>\n" +
                "        </View>\n" +
                "        <Text style={styles.aiTitle}>Insertar Datos con Comando de Voz</Text>\n" +
                "        <Text style={styles.aiDesc}>Dicta con tu voz el registro que deseas insertar en cualquier tabla de la base de datos PostgreSQL.</Text>\n" +
                "      </Pressable>\n\n" +
                "      <Text style={styles.sectionTitle}>Módulos de Gestión CRUD ({MODULES.length})</Text>\n" +
                "      <View style={styles.grid}>\n" +
                "        {MODULES.map((m) => (\n" +
                "          <Pressable key={m.name} style={styles.moduleCard} onPress={() => navigation.navigate(m.name)}>\n" +
                "            <View style={styles.moduleTop}>\n" +
                "              <Text style={styles.moduleName}>{m.name}</Text>\n" +
                "              <View style={styles.fieldPill}><Text style={styles.fieldPillText}>{m.fields} campos</Text></View>\n" +
                "            </View>\n" +
                "            <Text style={styles.moduleMeta}>Tabla SQL: {m.table}</Text>\n" +
                "            <Text style={styles.moduleEndpoint}>REST: {m.endpoint}</Text>\n" +
                "            <View style={styles.moduleFooter}>\n" +
                "              <Text style={styles.moduleLink}>Gestionar registros (Crear / Editar / Eliminar) →</Text>\n" +
                "            </View>\n" +
                "          </Pressable>\n" +
                "        ))}\n" +
                "      </View>\n\n" +
                "      <VoiceAssistantModal visible={voiceOpen} onClose={() => setVoiceOpen(false)} />\n" +
                "    </ScrollView>\n" +
                "  );\n" +
                "}\n\n" +
                "const styles = StyleSheet.create({\n" +
                "  container: { flex: 1, backgroundColor: theme.colors.background },\n" +
                "  content: { padding: 16, paddingBottom: 40 },\n" +
                "  banner: { backgroundColor: theme.colors.primary, borderRadius: 16, padding: 20, flexDirection: 'row', alignItems: 'center', marginBottom: 16 },\n" +
                "  bannerTag: { color: '#93C5FD', fontSize: 11, fontWeight: '700', letterSpacing: 0.8 },\n" +
                "  bannerTitle: { color: '#FFFFFF', fontSize: 22, fontWeight: '800', marginTop: 2 },\n" +
                "  bannerUser: { color: '#E2E8F0', fontSize: 13, marginTop: 4 },\n" +
                "  bannerServer: { color: '#CBD5E1', fontSize: 11, marginTop: 2 },\n" +
                "  logoutBtn: { backgroundColor: 'rgba(255,255,255,0.16)', paddingHorizontal: 14, paddingVertical: 9, borderRadius: 10 },\n" +
                "  logoutText: { color: '#FFFFFF', fontWeight: '700', fontSize: 13 },\n" +
                "  syncCardOnline: { backgroundColor: theme.colors.successSoft, borderWidth: 1, borderColor: '#86EFAC', borderRadius: 14, padding: 14, marginBottom: 16, flexDirection: 'row', alignItems: 'center' },\n" +
                "  syncCardWarning: { backgroundColor: '#FFFBEB', borderWidth: 1.5, borderColor: '#F59E0B', borderRadius: 14, padding: 14, marginBottom: 16, flexDirection: 'row', alignItems: 'center' },\n" +
                "  syncTitleOnline: { color: '#166534', fontWeight: '800', fontSize: 14 },\n" +
                "  syncTitleWarning: { color: '#B45309', fontWeight: '800', fontSize: 14 },\n" +
                "  syncSub: { color: theme.colors.textMuted, fontSize: 11, marginTop: 2 },\n" +
                "  syncBtn: { backgroundColor: theme.colors.primary, paddingHorizontal: 12, paddingVertical: 8, borderRadius: 8, marginLeft: 10 },\n" +
                "  syncBtnText: { color: '#FFFFFF', fontWeight: '700', fontSize: 12 },\n" +
                "  aiCard: { backgroundColor: theme.colors.aiSoft, borderWidth: 1.5, borderColor: theme.colors.ai, borderRadius: 16, padding: 18, marginBottom: 20 },\n" +
                "  aiHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },\n" +
                "  aiBadge: { backgroundColor: theme.colors.ai, paddingHorizontal: 10, paddingVertical: 4, borderRadius: 6 },\n" +
                "  aiBadgeText: { color: '#FFFFFF', fontSize: 11, fontWeight: '800' },\n" +
                "  aiAction: { color: theme.colors.ai, fontWeight: '700', fontSize: 13 },\n" +
                "  aiTitle: { color: '#2E1065', fontSize: 18, fontWeight: '800', marginBottom: 4 },\n" +
                "  aiDesc: { color: '#4C1D95', fontSize: 13, lineHeight: 18 },\n" +
                "  sectionTitle: { fontSize: 16, fontWeight: '800', color: theme.colors.text, marginBottom: 12 },\n" +
                "  grid: { gap: 12 },\n" +
                "  moduleCard: { backgroundColor: theme.colors.surface, borderRadius: 14, padding: 16, borderWidth: 1, borderColor: theme.colors.border, marginBottom: 12 },\n" +
                "  moduleTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },\n" +
                "  moduleName: { fontSize: 18, fontWeight: '800', color: theme.colors.primary },\n" +
                "  fieldPill: { backgroundColor: theme.colors.primarySoft, paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999 },\n" +
                "  fieldPillText: { color: theme.colors.primaryLight, fontSize: 12, fontWeight: '700' },\n" +
                "  moduleMeta: { fontSize: 13, color: theme.colors.textMuted },\n" +
                "  moduleEndpoint: { fontSize: 12, color: '#64748B', fontFamily: 'monospace', marginTop: 2 },\n" +
                "  moduleFooter: { marginTop: 12, paddingTop: 10, borderTopWidth: 1, borderTopColor: '#F1F5F9' },\n" +
                "  moduleLink: { color: theme.colors.primaryLight, fontSize: 13, fontWeight: '700' },\n" +
                "});\n";
    }

    private String voiceAssistantModal(List<Table> tables) {
        StringBuilder defaultExamples = new StringBuilder();
        for (Table t : tables) {
            String label = screenName(t.name());
            String sampleCols = t.columns().stream()
                    .filter(c -> !isPrimaryKey(t, c.name()))
                    .limit(2)
                    .map(c -> fieldName(c.name()) + " " + sampleValueForVoice(c))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("nombre Demo");
            defaultExamples.append("  { table: '").append(t.name()).append("', label: '").append(label)
                    .append("', phrase: 'Crear en ").append(label).append(": ").append(sampleCols).append("' },\n");
        }
        return "import React, { useEffect, useRef, useState } from 'react';\n" +
                "import { ActivityIndicator, Modal, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';\n" +
                "import * as Speech from 'expo-speech';\n" +
                "import { api } from '../api/client';\n" +
                "import { insertLocalItem } from '../db/sqlite';\n" +
                "import { SyncQueueService } from '../db/SyncQueueService';\n" +
                "import { theme } from '../theme';\n\n" +
                "type VoiceResult = {\n" +
                "  executed: boolean;\n" +
                "  action: string;\n" +
                "  table: string;\n" +
                "  entityLabel: string;\n" +
                "  extractedData: Record<string, unknown>;\n" +
                "  message: string;\n" +
                "  engine: string;\n" +
                "};\n\n" +
                "const EXAMPLES = [\n" + defaultExamples + "];\n\n" +
                "export function VoiceAssistantModal({\n" +
                "  visible,\n" +
                "  preferredTable,\n" +
                "  onClose,\n" +
                "  onInserted,\n" +
                "}: {\n" +
                "  visible: boolean;\n" +
                "  preferredTable?: string;\n" +
                "  onClose: () => void;\n" +
                "  onInserted?: () => void;\n" +
                "}) {\n" +
                "  const [transcript, setTranscript] = useState('');\n" +
                "  const [selectedTable, setSelectedTable] = useState<string>(preferredTable ?? '');\n" +
                "  const [listening, setListening] = useState(false);\n" +
                "  const [busy, setBusy] = useState(false);\n" +
                "  const [result, setResult] = useState<VoiceResult | null>(null);\n" +
                "  const [error, setError] = useState<string>();\n" +
                "  const inputRef = useRef<TextInput>(null);\n\n" +
                "  useEffect(() => {\n" +
                "    if (preferredTable) setSelectedTable(preferredTable);\n" +
                "  }, [preferredTable, visible]);\n\n" +
                "  const speakFeedback = (text: string) => {\n" +
                "    try {\n" +
                "      Speech.speak(text, { language: 'es-ES', rate: 1.0 });\n" +
                "    } catch {\n" +
                "      // ignore speech errors\n" +
                "    }\n" +
                "  };\n\n" +
                "  const startVoiceCapture = () => {\n" +
                "    setError(undefined);\n" +
                "    setResult(null);\n" +
                "    if (Platform.OS === 'web' && typeof window !== 'undefined') {\n" +
                "      const Win = window as unknown as Record<string, unknown>;\n" +
                "      const SR = (Win.SpeechRecognition || Win.webkitSpeechRecognition) as (new () => {\n" +
                "        lang: string;\n" +
                "        interimResults: boolean;\n" +
                "        onresult: (e: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void;\n" +
                "        onerror: () => void;\n" +
                "        onend: () => void;\n" +
                "        start: () => void;\n" +
                "      }) | undefined;\n" +
                "      if (SR) {\n" +
                "        const recognition = new SR();\n" +
                "        recognition.lang = 'es-ES';\n" +
                "        recognition.interimResults = false;\n" +
                "        setListening(true);\n" +
                "        recognition.onresult = (event) => {\n" +
                "          const spoken = event.results[0]?.[0]?.transcript ?? '';\n" +
                "          setTranscript(spoken);\n" +
                "          setListening(false);\n" +
                "        };\n" +
                "        recognition.onerror = () => setListening(false);\n" +
                "        recognition.onend = () => setListening(false);\n" +
                "        recognition.start();\n" +
                "        return;\n" +
                "      }\n" +
                "    }\n" +
                "    setListening(true);\n" +
                "    inputRef.current?.focus();\n" +
                "    speakFeedback('Micrófono listo. Dicta los datos del registro.');\n" +
                "  };\n\n" +
                "  const sendVoiceCommand = async (customPhrase?: string) => {\n" +
                "    const textToSend = (customPhrase ?? transcript).trim();\n" +
                "    if (!textToSend) {\n" +
                "      setError('Dicta o escribe una instrucción para insertar en la base de datos.');\n" +
                "      return;\n" +
                "    }\n" +
                "    setBusy(true);\n" +
                "    setError(undefined);\n" +
                "    setResult(null);\n" +
                "    try {\n" +
                "      const res = await api<VoiceResult>('/api/ai/voice-command', {\n" +
                "        method: 'POST',\n" +
                "        body: JSON.stringify({ transcript: textToSend, preferredTable: selectedTable || null }),\n" +
                "      });\n" +
                "      setResult(res);\n" +
                "      speakFeedback(res.message);\n" +
                "      if (onInserted) onInserted();\n" +
                "    } catch (reason) {\n" +
                "      try {\n" +
                "        const targetT = selectedTable || (EXAMPLES[0]?.table ?? 'default');\n" +
                "        const tempId = 'voice_' + Date.now();\n" +
                "        const extracted: Record<string, unknown> = { id: tempId };\n" +
                "        const words = textToSend.split(/\\s+/);\n" +
                "        extracted['name'] = words.slice(0, 3).join(' ');\n" +
                "        insertLocalItem(targetT, extracted);\n" +
                "        await SyncQueueService.enqueue(targetT, 'CREATE', null, extracted);\n" +
                "        const offlineRes: VoiceResult = {\n" +
                "          executed: true,\n" +
                "          action: 'INSERT_OFFLINE',\n" +
                "          table: targetT,\n" +
                "          entityLabel: targetT,\n" +
                "          extractedData: extracted,\n" +
                "          message: 'Guardado localmente en SQLite por voz (Modo Offline).',\n" +
                "          engine: 'IA Local SQLite Contingencia',\n" +
                "        };\n" +
                "        setResult(offlineRes);\n" +
                "        speakFeedback(offlineRes.message);\n" +
                "        if (onInserted) onInserted();\n" +
                "      } catch (localErr) {\n" +
                "        setError(reason instanceof Error ? reason.message : String(reason));\n" +
                "      }\n" +
                "    } finally {\n" +
                "      setBusy(false);\n" +
                "      setListening(false);\n" +
                "    }\n" +
                "  };\n\n" +
                "  return (\n" +
                "    <Modal visible={visible} animationType='slide' transparent onRequestClose={onClose}>\n" +
                "      <View style={styles.overlay}>\n" +
                "        <View style={styles.sheet}>\n" +
                "          <View style={styles.header}>\n" +
                "            <View>\n" +
                "              <Text style={styles.badge}>AGENTE DE IA LOCAL (OLLAMA / NLP)</Text>\n" +
                "              <Text style={styles.title}>Inserción por Voz en Base de Datos</Text>\n" +
                "            </View>\n" +
                "            <Pressable style={styles.closeBtn} onPress={onClose}>\n" +
                "              <Text style={styles.closeText}>Cerrar</Text>\n" +
                "            </Pressable>\n" +
                "          </View>\n\n" +
                "          <ScrollView style={{ maxHeight: 500 }}>\n" +
                "            <Text style={styles.label}>1. Tabla destino (o detección automática):</Text>\n" +
                "            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipsRow}>\n" +
                "              <Pressable style={[styles.chip, !selectedTable && styles.chipActive]} onPress={() => setSelectedTable('')}>\n" +
                "                <Text style={[styles.chipText, !selectedTable && styles.chipTextActive]}>Auto-detectar</Text>\n" +
                "              </Pressable>\n" +
                "              {EXAMPLES.map((ex) => (\n" +
                "                <Pressable key={ex.table} style={[styles.chip, selectedTable === ex.table && styles.chipActive]} onPress={() => setSelectedTable(ex.table)}>\n" +
                "                  <Text style={[styles.chipText, selectedTable === ex.table && styles.chipTextActive]}>{ex.label}</Text>\n" +
                "                </Pressable>\n" +
                "              ))}\n" +
                "            </ScrollView>\n\n" +
                "            <Pressable style={[styles.micBtn, listening && styles.micBtnActive]} onPress={startVoiceCapture}>\n" +
                "              <Text style={styles.micBtnTitle}>{listening ? 'Escuchando tu voz... (Dicta ahora)' : 'Pulsar para Dictar con Voz'}</Text>\n" +
                "              <Text style={styles.micBtnSub}>Usa el micrófono de tu teclado móvil o reconocimiento de voz</Text>\n" +
                "            </Pressable>\n\n" +
                "            <Text style={styles.label}>2. Comando capturado (editable):</Text>\n" +
                "            <TextInput\n" +
                "              ref={inputRef}\n" +
                "              value={transcript}\n" +
                "              onChangeText={setTranscript}\n" +
                "              placeholder='Ej: Crear registro con nombre Juan Perez...'\n" +
                "              multiline\n" +
                "              style={styles.textArea}\n" +
                "            />\n\n" +
                "            {busy ? (\n" +
                "              <ActivityIndicator size='large' color={theme.colors.ai} style={{ marginVertical: 14 }} />\n" +
                "            ) : (\n" +
                "              <Pressable style={styles.execBtn} onPress={() => void sendVoiceCommand()}>\n" +
                "                <Text style={styles.execBtnText}>Procesar con IA e Insertar en PostgreSQL</Text>\n" +
                "              </Pressable>\n" +
                "            )}\n\n" +
                "            {error && <View style={styles.errorBox}><Text style={styles.errorText}>{error}</Text></View>}\n\n" +
                "            {result && (\n" +
                "              <View style={styles.resultBox}>\n" +
                "                <Text style={styles.resultTitle}>{result.message}</Text>\n" +
                "                <Text style={styles.resultEngine}>Motor: {result.engine}</Text>\n" +
                "                <Text style={styles.resultJson}>{JSON.stringify(result.extractedData, null, 2)}</Text>\n" +
                "              </View>\n" +
                "            )}\n\n" +
                "            <Text style={[styles.label, { marginTop: 14 }]}>Comandos rápidos de ejemplo (1 toque):</Text>\n" +
                "            {EXAMPLES.map((ex) => (\n" +
                "              <Pressable\n" +
                "                key={ex.table}\n" +
                "                style={styles.exampleBtn}\n" +
                "                onPress={() => {\n" +
                "                  setSelectedTable(ex.table);\n" +
                "                  setTranscript(ex.phrase);\n" +
                "                  void sendVoiceCommand(ex.phrase);\n" +
                "                }}\n" +
                "              >\n" +
                "                <Text style={styles.exampleText}>\"{ex.phrase}\"</Text>\n" +
                "              </Pressable>\n" +
                "            ))}\n" +
                "          </ScrollView>\n" +
                "        </View>\n" +
                "      </View>\n" +
                "    </Modal>\n" +
                "  );\n" +
                "}\n\n" +
                "const styles = StyleSheet.create({\n" +
                "  overlay: { flex: 1, backgroundColor: 'rgba(15,23,42,0.65)', justifyContent: 'flex-end' },\n" +
                "  sheet: { backgroundColor: '#FFFFFF', borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 20, maxHeight: '92%' },\n" +
                "  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 },\n" +
                "  badge: { color: theme.colors.ai, fontSize: 11, fontWeight: '800' },\n" +
                "  title: { fontSize: 19, fontWeight: '800', color: theme.colors.text },\n" +
                "  closeBtn: { backgroundColor: '#F1F5F9', paddingHorizontal: 12, paddingVertical: 8, borderRadius: 8 },\n" +
                "  closeText: { color: theme.colors.text, fontWeight: '700', fontSize: 13 },\n" +
                "  label: { fontSize: 13, fontWeight: '700', color: theme.colors.textMuted, marginBottom: 6 },\n" +
                "  chipsRow: { flexDirection: 'row', marginBottom: 12 },\n" +
                "  chip: { borderWidth: 1, borderColor: theme.colors.border, borderRadius: 999, paddingHorizontal: 12, paddingVertical: 6, marginRight: 8, backgroundColor: '#F8FAFC' },\n" +
                "  chipActive: { backgroundColor: theme.colors.ai, borderColor: theme.colors.ai },\n" +
                "  chipText: { fontSize: 12, fontWeight: '700', color: theme.colors.textMuted },\n" +
                "  chipTextActive: { color: '#FFFFFF' },\n" +
                "  micBtn: { backgroundColor: theme.colors.aiSoft, borderWidth: 2, borderColor: theme.colors.ai, borderRadius: 14, padding: 16, alignItems: 'center', marginBottom: 12 },\n" +
                "  micBtnActive: { backgroundColor: '#EDE9FE', borderColor: '#5B21B6' },\n" +
                "  micBtnTitle: { color: theme.colors.ai, fontSize: 16, fontWeight: '800' },\n" +
                "  micBtnSub: { color: '#5B21B6', fontSize: 12, marginTop: 4, textAlign: 'center' },\n" +
                "  textArea: { borderWidth: 1, borderColor: theme.colors.border, borderRadius: 10, padding: 12, minHeight: 70, backgroundColor: '#F8FAFC', fontSize: 15, color: theme.colors.text, marginBottom: 10 },\n" +
                "  execBtn: { backgroundColor: theme.colors.ai, borderRadius: 12, paddingVertical: 14, alignItems: 'center' },\n" +
                "  execBtnText: { color: '#FFFFFF', fontSize: 15, fontWeight: '800' },\n" +
                "  errorBox: { backgroundColor: theme.colors.dangerSoft, padding: 10, borderRadius: 8, marginTop: 10 },\n" +
                "  errorText: { color: theme.colors.danger, fontSize: 13, fontWeight: '600' },\n" +
                "  resultBox: { backgroundColor: theme.colors.successSoft, borderWidth: 1, borderColor: '#86EFAC', borderRadius: 12, padding: 12, marginTop: 12 },\n" +
                "  resultTitle: { color: '#166534', fontWeight: '800', fontSize: 14 },\n" +
                "  resultEngine: { color: '#15803D', fontSize: 11, marginTop: 2 },\n" +
                "  resultJson: { fontFamily: 'monospace', fontSize: 12, color: '#14532D', marginTop: 6 },\n" +
                "  exampleBtn: { backgroundColor: '#F8FAFC', borderWidth: 1, borderColor: '#E2E8F0', borderRadius: 8, padding: 10, marginBottom: 6 },\n" +
                "  exampleText: { color: theme.colors.primaryLight, fontSize: 13, fontWeight: '600' },\n" +
                "});\n";
    }

    private String app() {
        return "import React from 'react';\n" +
                "import { NavigationContainer } from '@react-navigation/native';\n" +
                "import { createNativeStackNavigator } from '@react-navigation/native-stack';\n" +
                "import { AuthProvider, useAuth } from './src/auth/AuthContext';\n" +
                "import { LoginScreen } from './src/screens/LoginScreen';\n" +
                "import { AppNavigator } from './src/navigation';\n\n" +
                "const Stack = createNativeStackNavigator();\n\n" +
                "function Root() {\n" +
                "  const { authenticated } = useAuth();\n" +
                "  return (\n" +
                "    <NavigationContainer>\n" +
                "      {authenticated ? (\n" +
                "        <AppNavigator />\n" +
                "      ) : (\n" +
                "        <Stack.Navigator>\n" +
                "          <Stack.Screen name='Login' component={LoginScreen} options={{ headerShown: false }} />\n" +
                "        </Stack.Navigator>\n" +
                "      )}\n" +
                "    </NavigationContainer>\n" +
                "  );\n" +
                "}\n\n" +
                "export default function App() {\n" +
                "  return (\n" +
                "    <AuthProvider>\n" +
                "      <Root />\n" +
                "    </AuthProvider>\n" +
                "  );\n" +
                "}\n";
    }

    private String navigation(List<Table> tables) {
        StringBuilder screens = new StringBuilder();
        StringBuilder registrations = new StringBuilder();
        screens.append("import { HomeScreen } from './screens/HomeScreen';\n");
        registrations.append("      <Stack.Screen name='Inicio' component={HomeScreen} options={{ title: 'Panel Principal' }} />\n");
        for (Table table : tables) {
            String screen = screenName(table.name());
            screens.append("import { ").append(screen).append("Screen } from './screens/").append(screen).append("Screen';\n");
            registrations.append("      <Stack.Screen name='").append(screen).append("' component={").append(screen).append("Screen} options={{ title: 'Gestión de ").append(screen).append("' }} />\n");
        }
        return "import React from 'react';\n" +
                "import { createNativeStackNavigator } from '@react-navigation/native-stack';\n" +
                screens +
                "import { theme } from './theme';\n\n" +
                "const Stack = createNativeStackNavigator();\n\n" +
                "export function AppNavigator() {\n" +
                "  return (\n" +
                "    <Stack.Navigator\n" +
                "      screenOptions={{\n" +
                "        headerStyle: { backgroundColor: theme.colors.primary },\n" +
                "        headerTintColor: '#FFFFFF',\n" +
                "        headerTitleStyle: { fontWeight: '800' },\n" +
                "      }}\n" +
                "    >\n" +
                registrations +
                "    </Stack.Navigator>\n" +
                "  );\n" +
                "}\n";
    }

    private String crudScreen(Table table, String resource, String screen) {
        List<String> pkCols = table.primaryKey() == null ? List.of("id") : table.primaryKey().columns();
        String pkCol = pkCols.isEmpty() ? "id" : pkCols.get(0);
        boolean singlePk = pkCols.size() == 1;
        String pkExpr = pkCols.stream().map(c -> "String(item." + fieldName(c) + " ?? '')").reduce((a, b) -> a + " + ':' + " + b).orElse("String(item.id ?? '')");

        Map<String, String> fkRefTable = new LinkedHashMap<>();
        for (ForeignKey fk : table.foreignKeys()) {
            if (fk.columns().size() == 1) {
                fkRefTable.put(fk.columns().getFirst().column(), fk.referencedTable());
            }
        }

        StringBuilder stateDecls = new StringBuilder();
        StringBuilder resetStmts = new StringBuilder();
        StringBuilder fillEditStmts = new StringBuilder();
        StringBuilder payloadFields = new StringBuilder();
        StringBuilder formInputs = new StringBuilder();
        StringBuilder fkStateDecls = new StringBuilder();
        StringBuilder fkLoadStmts = new StringBuilder();
        StringBuilder cardRows = new StringBuilder();

        for (Column col : table.columns()) {
            String fName = fieldName(col.name());
            String capName = cap(fName);
            String sql = col.sqlType().toUpperCase(Locale.ROOT);
            boolean isPk = isPrimaryKey(table, col.name());

            cardRows.append("              <View style={styles.fieldRow}><Text style={styles.fieldKey}>")
                    .append(col.name()).append(":</Text><Text style={styles.fieldVal}>{String(item.")
                    .append(fName).append(" ?? '—')}</Text></View>\n");

            if (isPk && singlePk) {
                continue;
            }

            stateDecls.append("  const [").append(fName).append(", set").append(capName).append("] = useState('');\n");
            resetStmts.append("    set").append(capName).append("('');\n");
            fillEditStmts.append("    set").append(capName).append("(item.").append(fName).append(" === undefined || item.").append(fName).append(" === null ? '' : String(item.").append(fName).append("));\n");

            if (payloadFields.length() > 0) payloadFields.append(",\n        ");
            if (sql.equals("INTEGER") || sql.equals("BIGINT") || sql.startsWith("NUMERIC")) {
                payloadFields.append(fName).append(": ").append(fName).append(".trim() === '' ? ").append(col.nullable() ? "null" : "0").append(" : Number(").append(fName).append(")");
            } else if (sql.equals("BOOLEAN")) {
                payloadFields.append(fName).append(": ").append(fName).append(".toLowerCase() === 'true' || ").append(fName).append(" === '1' || ").append(fName).append(".toLowerCase() === 'si'");
            } else {
                payloadFields.append(fName).append(": ").append(fName).append(".trim() === '' ? ").append(col.nullable() ? "null" : "''").append(" : ").append(fName);
            }

            String refTable = fkRefTable.get(col.name());
            if (refTable != null) {
                String fkOptionsVar = fName + "Options";
                String refResource = resourceName(refTable);
                fkStateDecls.append("  const [").append(fkOptionsVar).append(", set").append(cap(fkOptionsVar)).append("] = useState<Item[]>([]);\n");
                fkLoadStmts.append("    api<Item[]>('/api/").append(refResource).append("').then(set").append(cap(fkOptionsVar)).append(").catch(() => {});\n");
            }

            formInputs.append("        <View style={styles.inputGroup}>\n")
                    .append("          <Text style={styles.label}>").append(col.name()).append(" (").append(sql).append(col.nullable() ? ", opcional" : " *").append(")</Text>\n");
            if (sql.equals("BOOLEAN")) {
                formInputs.append("          <View style={styles.boolRow}>\n")
                        .append("            <Pressable style={[styles.boolBtn, (").append(fName).append(" === 'true') && styles.boolBtnActive]} onPress={() => set").append(capName).append("('true')}><Text style={[styles.boolText, (").append(fName).append(" === 'true') && styles.boolTextActive]}>Sí / Activo (true)</Text></Pressable>\n")
                        .append("            <Pressable style={[styles.boolBtn, (").append(fName).append(" === 'false') && styles.boolBtnActive]} onPress={() => set").append(capName).append("('false')}><Text style={[styles.boolText, (").append(fName).append(" === 'false') && styles.boolTextActive]}>No / Inactivo (false)</Text></Pressable>\n")
                        .append("          </View>\n");
            } else {
                String kbType = (sql.equals("INTEGER") || sql.equals("BIGINT") || sql.startsWith("NUMERIC")) ? " keyboardType='numeric'" : "";
                formInputs.append("          <TextInput value={").append(fName).append("} onChangeText={set").append(capName).append("} placeholder='").append(col.name()).append("'").append(kbType).append(" style={styles.input} />\n");
            }
            if (refTable != null) {
                String fkOptionsVar = fName + "Options";
                formInputs.append("          {").append(fkOptionsVar).append(".length > 0 && (\n")
                        .append("            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.fkRow}>\n")
                        .append("              {").append(fkOptionsVar).append(".map((opt, idx) => {\n")
                        .append("                const optId = String(opt.id ?? Object.values(opt)[0] ?? idx);\n")
                        .append("                const optLabel = String(opt.name ?? opt.nombre ?? opt.email ?? opt.title ?? optId.slice(0, 8));\n")
                        .append("                return (\n")
                        .append("                  <Pressable key={optId} style={[styles.fkChip, ").append(fName).append(" === optId && styles.fkChipActive]} onPress={() => set").append(capName).append("(optId)}>\n")
                        .append("                    <Text style={[styles.fkChipText, ").append(fName).append(" === optId && styles.fkChipTextActive]}>{optLabel}</Text>\n")
                        .append("                  </Pressable>\n")
                        .append("                );\n")
                        .append("              })}\n")
                        .append("            </ScrollView>\n")
                        .append("          )}\n");
            }
            formInputs.append("        </View>\n");
        }

        return "import React, { useEffect, useState } from 'react';\n" +
                "import { Alert, FlatList, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';\n" +
                "import { api } from '../api/client';\n" +
                "import { deleteLocalItem, getLocalItems, insertLocalItem, saveLocalItems, updateLocalItem } from '../db/sqlite';\n" +
                "import { SyncQueueService } from '../db/SyncQueueService';\n" +
                "import { VoiceAssistantModal } from '../components/VoiceAssistantModal';\n" +
                "import { theme } from '../theme';\n\n" +
                "type Item = Record<string, unknown> & { id?: string };\n\n" +
                "export function " + screen + "Screen() {\n" +
                "  const [items, setItems] = useState<Item[]>([]);\n" +
                "  const [search, setSearch] = useState('');\n" +
                "  const [editingId, setEditingId] = useState<string | null>(null);\n" +
                "  const [voiceOpen, setVoiceOpen] = useState(false);\n" +
                "  const [statusMsg, setStatusMsg] = useState<string>();\n" +
                "  const [isOffline, setIsOffline] = useState(false);\n" +
                "  const [pendingCount, setPendingCount] = useState(0);\n" +
                stateDecls +
                fkStateDecls +
                "\n  const getPk = (item: Item): string => " + pkExpr + ";\n\n" +
                "  useEffect(() => {\n" +
                "    return SyncQueueService.subscribe((count) => setPendingCount(count));\n" +
                "  }, []);\n\n" +
                "  const triggerSync = async () => {\n" +
                "    try {\n" +
                "      const res = await SyncQueueService.syncPendingQueue();\n" +
                "      Alert.alert('Sincronización Offline', `Operaciones: ${res.total}\\nSincronizadas: ${res.synced}\\nFallidas: ${res.failed}`);\n" +
                "      await load();\n" +
                "    } catch (err) {\n" +
                "      Alert.alert('Error', String(err));\n" +
                "    }\n" +
                "  };\n\n" +
                "  const load = async () => {\n" +
                "    try {\n" +
                "      const data = await api<Item[]>('/api/" + resource + "');\n" +
                "      setItems(data);\n" +
                "      saveLocalItems('" + table.name() + "', data);\n" +
                "      setIsOffline(false);\n" +
                "    } catch (e) {\n" +
                "      const cached = getLocalItems('" + table.name() + "');\n" +
                "      setItems(cached);\n" +
                "      setIsOffline(true);\n" +
                "      setStatusMsg('Modo Offline: datos de SQLite local (' + cached.length + ' registros)');\n" +
                "    }\n" +
                fkLoadStmts +
                "  };\n\n" +
                "  useEffect(() => { void load().catch(() => {}); }, []);\n\n" +
                "  const resetForm = () => {\n" +
                "    setEditingId(null);\n" +
                resetStmts +
                "  };\n\n" +
                "  const startEdit = (item: Item) => {\n" +
                "    setEditingId(getPk(item));\n" +
                fillEditStmts +
                "    setStatusMsg('Editando registro seleccionado');\n" +
                "  };\n\n" +
                "  const buildPayload = () => ({\n" +
                "        " + payloadFields + "\n" +
                "  });\n\n" +
                "  const create = async () => {\n" +
                "    const payload = buildPayload();\n" +
                "    try {\n" +
                "      await api('/api/" + resource + "', { method: 'POST', body: JSON.stringify(payload) });\n" +
                "      resetForm();\n" +
                "      setStatusMsg('Registro creado y guardado en servidor');\n" +
                "      await load();\n" +
                "    } catch (e) {\n" +
                "      const tempId = 'local_' + Date.now();\n" +
                "      const localItem = { ...payload, " + pkCol + ": tempId };\n" +
                "      insertLocalItem('" + table.name() + "', localItem);\n" +
                "      await SyncQueueService.enqueue('" + table.name() + "', 'CREATE', null, payload);\n" +
                "      resetForm();\n" +
                "      const cached = getLocalItems('" + table.name() + "');\n" +
                "      setItems(cached);\n" +
                "      setIsOffline(true);\n" +
                "      setStatusMsg('Guardado localmente en SQLite (Pendiente de sincronizar)');\n" +
                "    }\n" +
                "  };\n\n" +
                "  const update = async (item?: Item) => {\n" +
                "    const targetId = item ? getPk(item) : editingId;\n" +
                "    if (!targetId) return;\n" +
                "    const payload = buildPayload();\n" +
                "    try {\n" +
                "      await api(`/api/" + resource + "/${encodeURIComponent(targetId)}`, { method: 'PUT', body: JSON.stringify(payload) });\n" +
                "      resetForm();\n" +
                "      setStatusMsg('Registro actualizado en servidor');\n" +
                "      await load();\n" +
                "    } catch (e) {\n" +
                "      updateLocalItem('" + table.name() + "', '" + pkCol + "', targetId, payload);\n" +
                "      await SyncQueueService.enqueue('" + table.name() + "', 'UPDATE', targetId, payload);\n" +
                "      resetForm();\n" +
                "      const cached = getLocalItems('" + table.name() + "');\n" +
                "      setItems(cached);\n" +
                "      setIsOffline(true);\n" +
                "      setStatusMsg('Actualizado localmente en SQLite (Pendiente de sincronizar)');\n" +
                "    }\n" +
                "  };\n\n" +
                "  const remove = async (item: Item) => {\n" +
                "    const targetId = getPk(item);\n" +
                "    try {\n" +
                "      await api(`/api/" + resource + "/${encodeURIComponent(targetId)}`, { method: 'DELETE' });\n" +
                "      if (editingId === targetId) resetForm();\n" +
                "      setStatusMsg('Registro eliminado del servidor');\n" +
                "      await load();\n" +
                "    } catch (e) {\n" +
                "      deleteLocalItem('" + table.name() + "', '" + pkCol + "', targetId);\n" +
                "      await SyncQueueService.enqueue('" + table.name() + "', 'DELETE', targetId, {});\n" +
                "      if (editingId === targetId) resetForm();\n" +
                "      const cached = getLocalItems('" + table.name() + "');\n" +
                "      setItems(cached);\n" +
                "      setIsOffline(true);\n" +
                "      setStatusMsg('Eliminado localmente en SQLite (Pendiente de sincronizar)');\n" +
                "    }\n" +
                "  };\n\n" +
                "  const filtered = items.filter((item) =>\n" +
                "    !search.trim() || JSON.stringify(item).toLowerCase().includes(search.trim().toLowerCase())\n" +
                "  );\n\n" +
                "  return (\n" +
                "    <View style={styles.container}>\n" +
                "      <FlatList\n" +
                "        data={filtered}\n" +
                "        keyExtractor={(item, index) => getPk(item) || String(index)}\n" +
                "        ListHeaderComponent={\n" +
                "          <View>\n" +
                "            <View style={styles.topBar}>\n" +
                "              <View>\n" +
                "                <Text style={styles.title}>" + screen + "</Text>\n" +
                "                <Text style={styles.subtitle}>Endpoint: /api/" + resource + " · {items.length} registros</Text>\n" +
                "              </View>\n" +
                "              <Pressable style={styles.voiceBtn} onPress={() => setVoiceOpen(true)}>\n" +
                "                <Text style={styles.voiceBtnText}>Insertar por Voz IA</Text>\n" +
                "              </Pressable>\n" +
                "            </View>\n\n" +
                "            {pendingCount > 0 && (\n" +
                "              <Pressable style={styles.syncAlert} onPress={() => void triggerSync()}>\n" +
                "                <Text style={styles.syncAlertText}>⚡ Cola Offline: {pendingCount} operaciones en SQLite · Toque para Sincronizar</Text>\n" +
                "              </Pressable>\n" +
                "            )}\n\n" +
                "            <View style={styles.formCard}>\n" +
                "              <View style={styles.formHeader}>\n" +
                "                <Text style={styles.formTitle}>{editingId ? `Editar " + screen + " (${editingId.slice(0, 8)})` : 'Nuevo Registro de " + screen + "'}</Text>\n" +
                "                {editingId && (\n" +
                "                  <Pressable onPress={resetForm}>\n" +
                "                    <Text style={styles.cancelLink}>Cancelar edición</Text>\n" +
                "                  </Pressable>\n" +
                "                )}\n" +
                "              </View>\n" +
                formInputs +
                "              {statusMsg && <Text style={styles.statusMsg}>{statusMsg}</Text>}\n" +
                "              <View style={styles.formActions}>\n" +
                "                {editingId ? (\n" +
                "                  <Pressable style={styles.updateMainBtn} onPress={() => void update().catch(reason => Alert.alert('Error', String(reason)))}>\n" +
                "                    <Text style={styles.primaryBtnText}>Guardar Cambios (Actualizar)</Text>\n" +
                "                  </Pressable>\n" +
                "                ) : (\n" +
                "                  <Pressable style={styles.createBtn} onPress={() => void create().catch(reason => Alert.alert('Error', String(reason)))}>\n" +
                "                    <Text style={styles.primaryBtnText}>Crear Registro</Text>\n" +
                "                  </Pressable>\n" +
                "                )}\n" +
                "              </View>\n" +
                "            </View>\n\n" +
                "            <View style={styles.searchRow}>\n" +
                "              <TextInput value={search} onChangeText={setSearch} placeholder='Buscar en " + screen + "...' style={styles.searchInput} />\n" +
                "              <Pressable style={styles.reloadBtn} onPress={() => void load().catch(reason => Alert.alert('Error', String(reason)))}>\n" +
                "                <Text style={styles.reloadText}>Recargar</Text>\n" +
                "              </Pressable>\n" +
                "            </View>\n" +
                "          </View>\n" +
                "        }\n" +
                "        ListEmptyComponent={<View style={styles.emptyBox}><Text style={styles.emptyText}>No hay registros todavía. Crea uno arriba o usa el botón Insertar por Voz IA.</Text></View>}\n" +
                "        renderItem={({ item }) => (\n" +
                "          <View style={styles.row}>\n" +
                cardRows +
                "            <View style={styles.rowActions}>\n" +
                "              <Pressable style={styles.editBtn} onPress={() => startEdit(item)}>\n" +
                "                <Text style={styles.editBtnText}>Editar</Text>\n" +
                "              </Pressable>\n" +
                "              <Pressable style={styles.deleteBtn} onPress={() => void remove(item).catch(reason => Alert.alert('Error', String(reason)))}>\n" +
                "                <Text style={styles.deleteBtnText}>Eliminar</Text>\n" +
                "              </Pressable>\n" +
                "            </View>\n" +
                "          </View>\n" +
                "        )}\n" +
                "      />\n" +
                "      <VoiceAssistantModal\n" +
                "        visible={voiceOpen}\n" +
                "        preferredTable='" + table.name() + "'\n" +
                "        onClose={() => setVoiceOpen(false)}\n" +
                "        onInserted={() => void load()}\n" +
                "      />\n" +
                "    </View>\n" +
                "  );\n" +
                "}\n\n" +
                "const styles = StyleSheet.create({\n" +
                "  container: { flex: 1, backgroundColor: theme.colors.background, paddingHorizontal: 14 },\n" +
                "  topBar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 14 },\n" +
                "  title: { fontSize: 22, fontWeight: '800', color: theme.colors.primary },\n" +
                "  subtitle: { fontSize: 12, color: theme.colors.textMuted, marginTop: 2 },\n" +
                "  voiceBtn: { backgroundColor: theme.colors.ai, paddingHorizontal: 14, paddingVertical: 10, borderRadius: 10 },\n" +
                "  voiceBtnText: { color: '#FFFFFF', fontWeight: '800', fontSize: 12 },\n" +
                "  syncAlert: { backgroundColor: '#FEF3C7', borderWidth: 1, borderColor: '#F59E0B', borderRadius: 10, padding: 10, marginBottom: 12, alignItems: 'center' },\n" +
                "  syncAlertText: { color: '#B45309', fontWeight: '800', fontSize: 12 },\n" +
                "  formCard: { backgroundColor: theme.colors.surface, borderRadius: 14, padding: 16, borderWidth: 1, borderColor: theme.colors.border, marginBottom: 14 },\n" +
                "  formHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },\n" +
                "  formTitle: { fontSize: 16, fontWeight: '800', color: theme.colors.text },\n" +
                "  cancelLink: { color: theme.colors.danger, fontSize: 13, fontWeight: '700' },\n" +
                "  inputGroup: { marginBottom: 10 },\n" +
                "  label: { fontSize: 12, fontWeight: '700', color: theme.colors.textMuted, marginBottom: 4 },\n" +
                "  input: { borderWidth: 1, borderColor: theme.colors.border, backgroundColor: '#F8FAFC', borderRadius: 8, paddingHorizontal: 12, paddingVertical: 10, fontSize: 14, color: theme.colors.text },\n" +
                "  boolRow: { flexDirection: 'row', gap: 8 },\n" +
                "  boolBtn: { flex: 1, borderWidth: 1, borderColor: theme.colors.border, borderRadius: 8, paddingVertical: 9, alignItems: 'center', backgroundColor: '#F8FAFC' },\n" +
                "  boolBtnActive: { backgroundColor: theme.colors.primarySoft, borderColor: theme.colors.primaryLight },\n" +
                "  boolText: { fontSize: 13, fontWeight: '600', color: theme.colors.textMuted },\n" +
                "  boolTextActive: { color: theme.colors.primaryLight, fontWeight: '800' },\n" +
                "  fkRow: { flexDirection: 'row', marginTop: 6 },\n" +
                "  fkChip: { borderWidth: 1, borderColor: theme.colors.border, borderRadius: 999, paddingHorizontal: 10, paddingVertical: 5, marginRight: 6, backgroundColor: '#F1F5F9' },\n" +
                "  fkChipActive: { backgroundColor: theme.colors.primaryLight, borderColor: theme.colors.primaryLight },\n" +
                "  fkChipText: { fontSize: 12, color: theme.colors.textMuted, fontWeight: '600' },\n" +
                "  fkChipTextActive: { color: '#FFFFFF' },\n" +
                "  statusMsg: { color: theme.colors.success, fontSize: 12, fontWeight: '700', marginBottom: 8 },\n" +
                "  formActions: { marginTop: 4 },\n" +
                "  createBtn: { backgroundColor: theme.colors.primaryLight, borderRadius: 10, paddingVertical: 12, alignItems: 'center' },\n" +
                "  updateMainBtn: { backgroundColor: theme.colors.secondary, borderRadius: 10, paddingVertical: 12, alignItems: 'center' },\n" +
                "  primaryBtnText: { color: '#FFFFFF', fontWeight: '800', fontSize: 14 },\n" +
                "  searchRow: { flexDirection: 'row', gap: 8, marginBottom: 12 },\n" +
                "  searchInput: { flex: 1, backgroundColor: theme.colors.surface, borderWidth: 1, borderColor: theme.colors.border, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 9, fontSize: 14 },\n" +
                "  reloadBtn: { backgroundColor: theme.colors.surface, borderWidth: 1, borderColor: theme.colors.border, borderRadius: 10, paddingHorizontal: 14, justifyContent: 'center' },\n" +
                "  reloadText: { color: theme.colors.primary, fontWeight: '700', fontSize: 13 },\n" +
                "  emptyBox: { backgroundColor: theme.colors.surface, borderRadius: 12, padding: 24, alignItems: 'center', borderWidth: 1, borderColor: theme.colors.border },\n" +
                "  emptyText: { color: theme.colors.textMuted, textAlign: 'center', fontSize: 13 },\n" +
                "  row: { backgroundColor: theme.colors.surface, borderRadius: 12, padding: 14, marginBottom: 10, borderWidth: 1, borderColor: theme.colors.border },\n" +
                "  fieldRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 3, borderBottomWidth: 1, borderBottomColor: '#F8FAFC' },\n" +
                "  fieldKey: { fontSize: 13, fontWeight: '700', color: theme.colors.textMuted },\n" +
                "  fieldVal: { fontSize: 13, fontWeight: '600', color: theme.colors.text, maxWidth: '65%', textAlign: 'right' },\n" +
                "  rowActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 8, marginTop: 10, paddingTop: 8, borderTopWidth: 1, borderTopColor: '#F1F5F9' },\n" +
                "  editBtn: { backgroundColor: theme.colors.secondarySoft, borderWidth: 1, borderColor: theme.colors.secondary, paddingHorizontal: 14, paddingVertical: 7, borderRadius: 8 },\n" +
                "  editBtnText: { color: theme.colors.secondary, fontWeight: '700', fontSize: 13 },\n" +
                "  deleteBtn: { backgroundColor: theme.colors.dangerSoft, borderWidth: 1, borderColor: '#FCA5A5', paddingHorizontal: 14, paddingVertical: 7, borderRadius: 8 },\n" +
                "  deleteBtnText: { color: theme.colors.danger, fontWeight: '700', fontSize: 13 },\n" +
                "});\n";
    }

    private String readme(String appName, Traceability trace) {
        return "# " + appName + " (Expo Go / React Native)\n\n" +
                "Frontend móvil corporativo generado por CASEGen. Consume el backend Spring Boot y el Agente de IA Local por Voz generados desde el mismo LogicalModel.\n\n" +
                "## Ejecución rápida con Expo Go\n\n" +
                "```powershell\n" +
                "npm install\n" +
                "npx expo start\n" +
                "```\n\n" +
                "- **Login Demo**: `admin@demo.com` / `admin1234` (o pulsa **Acceso Rápido Demo**).\n" +
                "- **Conexión con el Backend**: La app detecta automáticamente la IP LAN de tu computadora al usar Expo Go. También puedes cambiar la URL del servidor en vivo desde la pantalla de Login.\n" +
                "- **Agente IA por Voz**: Pulsa **Insertar por Voz IA** para dictar registros con tu voz e insertarlos directamente en PostgreSQL.\n\n" +
                "Origen: modelo `" + trace.modelName() + "`, versión v" + trace.modelVersionNumber() + ".\n";
    }

    private static String sampleValueForVoice(Column col) {
        String low = col.name().toLowerCase(Locale.ROOT);
        String sql = col.sqlType().toUpperCase(Locale.ROOT);
        if (low.contains("email") || low.contains("correo")) return "carlos@demo.com";
        if (low.contains("name") || low.contains("nombre")) return "Carlos Rojas";
        if (sql.equals("BOOLEAN")) return "verdadero";
        if (sql.equals("INTEGER") || sql.equals("BIGINT") || sql.startsWith("NUMERIC")) return "50";
        return "Demo";
    }

    private static boolean isPrimaryKey(Table table, String columnName) {
        return table.primaryKey() != null && table.primaryKey().columns().contains(columnName);
    }

    private static String safeAppName(String value) {
        String result = value.replaceAll("[^A-Za-z0-9-_]", "-").toLowerCase(Locale.ROOT);
        return result.isBlank() ? "generated-mobile-app" : result;
    }

    private static String resourceName(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String screenName(String value) {
        StringBuilder result = new StringBuilder();
        for (String part : value.split("[-_]")) result.append(cap(part));
        return result.toString();
    }

    private static String fieldName(String value) {
        String name = screenName(value);
        return name.isEmpty() ? "value" : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static String cap(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static void put(Path root, String relative, String content) throws IOException {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root.normalize())) throw new IOException("Unsafe generated path");
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private String sqliteTs(List<Table> tables) {
        StringBuilder tableCreations = new StringBuilder();
        for (Table t : tables) {
            StringBuilder cols = new StringBuilder();
            for (Column c : t.columns()) {
                if (!cols.isEmpty()) cols.append(", ");
                cols.append(c.name()).append(" TEXT");
            }
            tableCreations.append("    db.execSync(`CREATE TABLE IF NOT EXISTS ").append(t.name()).append(" (").append(cols).append(");`);\n");
        }

        return "import { Platform } from 'react-native';\n\n" +
                "let SQLite: any = null;\n" +
                "if (Platform.OS !== 'web') {\n" +
                "  try {\n" +
                "    SQLite = require('expo-sqlite');\n" +
                "  } catch (e) {\n" +
                "    console.warn('expo-sqlite no disponible en este entorno', e);\n" +
                "  }\n" +
                "}\n\n" +
                "let dbInstance: any = null;\n\n" +
                "export function getDatabase(): any {\n" +
                "  if (Platform.OS === 'web' || !SQLite) return null;\n" +
                "  if (!dbInstance) {\n" +
                "    try {\n" +
                "      dbInstance = SQLite.openDatabaseSync('casegen_local.db');\n" +
                "      initDatabase(dbInstance);\n" +
                "    } catch (e) {\n" +
                "      console.warn('Error inicializando SQLite local:', e);\n" +
                "    }\n" +
                "  }\n" +
                "  return dbInstance;\n" +
                "}\n\n" +
                "function initDatabase(db: any) {\n" +
                "  try {\n" +
                "    db.execSync(`\n" +
                "      CREATE TABLE IF NOT EXISTS offline_sync_queue (\n" +
                "        id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                "        client_id TEXT NOT NULL UNIQUE,\n" +
                "        entity_name TEXT NOT NULL,\n" +
                "        operation TEXT NOT NULL,\n" +
                "        target_id TEXT,\n" +
                "        payload TEXT NOT NULL,\n" +
                "        created_at TEXT NOT NULL,\n" +
                "        retry_count INTEGER DEFAULT 0,\n" +
                "        status TEXT DEFAULT 'PENDING'\n" +
                "      );\n" +
                "    `);\n" +
                tableCreations +
                "  } catch (e) {\n" +
                "    console.warn('Error inicializando tablas SQLite:', e);\n" +
                "  }\n" +
                "}\n\n" +
                "export function saveLocalItems(table: string, items: any[]) {\n" +
                "  try {\n" +
                "    const db = getDatabase();\n" +
                "    if (!db) return;\n" +
                "    db.execSync(`DELETE FROM ${table};`);\n" +
                "    for (const item of items) {\n" +
                "      insertLocalItem(table, item);\n" +
                "    }\n" +
                "  } catch (e) {\n" +
                "    console.warn('Error guardando cache local en ' + table, e);\n" +
                "  }\n" +
                "}\n\n" +
                "export function getLocalItems(table: string): any[] {\n" +
                "  try {\n" +
                "    const db = getDatabase();\n" +
                "    if (!db) return [];\n" +
                "    return db.getAllSync(`SELECT * FROM ${table};`);\n" +
                "  } catch (e) {\n" +
                "    console.warn('Error consultando SQLite para ' + table, e);\n" +
                "    return [];\n" +
                "  }\n" +
                "}\n\n" +
                "export function insertLocalItem(table: string, item: any) {\n" +
                "  try {\n" +
                "    const db = getDatabase();\n" +
                "    if (!db) return;\n" +
                "    const keys = Object.keys(item).filter(k => item[k] !== undefined);\n" +
                "    if (keys.length === 0) return;\n" +
                "    const placeholders = keys.map(() => '?').join(', ');\n" +
                "    const values = keys.map(k => typeof item[k] === 'object' && item[k] !== null ? JSON.stringify(item[k]) : String(item[k] ?? ''));\n" +
                "    db.runSync(`INSERT OR REPLACE INTO ${table} (${keys.join(', ')}) VALUES (${placeholders});`, values);\n" +
                "  } catch (e) {\n" +
                "    console.warn('Error insertando en SQLite ' + table, e);\n" +
                "  }\n" +
                "}\n\n" +
                "export function updateLocalItem(table: string, pkCol: string, targetId: string, item: any) {\n" +
                "  try {\n" +
                "    const db = getDatabase();\n" +
                "    if (!db) return;\n" +
                "    const keys = Object.keys(item).filter(k => k !== pkCol && item[k] !== undefined);\n" +
                "    if (keys.length === 0) return;\n" +
                "    const setClauses = keys.map(k => `${k} = ?`).join(', ');\n" +
                "    const values = keys.map(k => typeof item[k] === 'object' && item[k] !== null ? JSON.stringify(item[k]) : String(item[k] ?? ''));\n" +
                "    values.push(targetId);\n" +
                "    db.runSync(`UPDATE ${table} SET ${setClauses} WHERE ${pkCol} = ?;`, values);\n" +
                "  } catch (e) {\n" +
                "    console.warn('Error actualizando en SQLite ' + table, e);\n" +
                "  }\n" +
                "}\n\n" +
                "export function deleteLocalItem(table: string, pkCol: string, targetId: string) {\n" +
                "  try {\n" +
                "    const db = getDatabase();\n" +
                "    if (!db) return;\n" +
                "    db.runSync(`DELETE FROM ${table} WHERE ${pkCol} = ?;`, [targetId]);\n" +
                "  } catch (e) {\n" +
                "    console.warn('Error eliminando en SQLite ' + table, e);\n" +
                "  }\n" +
                "}\n";
    }

    private String syncQueueServiceTs() {
        return "import { getDatabase } from './sqlite';\n" +
                "import { api } from '../api/client';\n\n" +
                "export type SyncStatusListener = (pendingCount: number, syncing: boolean) => void;\n\n" +
                "let listeners: SyncStatusListener[] = [];\n" +
                "let isSyncing = false;\n\n" +
                "export const SyncQueueService = {\n" +
                "  subscribe(fn: SyncStatusListener) {\n" +
                "    listeners.push(fn);\n" +
                "    fn(this.getPendingCount(), isSyncing);\n" +
                "    return () => {\n" +
                "      listeners = listeners.filter((l) => l !== fn);\n" +
                "    };\n" +
                "  },\n\n" +
                "  notify() {\n" +
                "    const count = this.getPendingCount();\n" +
                "    listeners.forEach((l) => l(count, isSyncing));\n" +
                "  },\n\n" +
                "  getPendingCount(): number {\n" +
                "    try {\n" +
                "      const db = getDatabase();\n" +
                "      const rows = db.getAllSync<{ count: number }>(\"SELECT COUNT(*) as count FROM offline_sync_queue WHERE status = 'PENDING'\");\n" +
                "      return rows[0]?.count ?? 0;\n" +
                "    } catch (e) {\n" +
                "      return 0;\n" +
                "    }\n" +
                "  },\n\n" +
                "  async enqueue(entityName: string, operation: 'CREATE' | 'UPDATE' | 'DELETE', targetId: string | null, payload: Record<string, unknown>): Promise<string> {\n" +
                "    const db = getDatabase();\n" +
                "    const clientId = 'mut_' + Date.now() + '_' + Math.random().toString(36).slice(2, 7);\n" +
                "    if (!db) return clientId;\n" +
                "    db.runSync(\n" +
                "      'INSERT INTO offline_sync_queue (client_id, entity_name, operation, target_id, payload, created_at, retry_count, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',\n" +
                "      [clientId, entityName, operation, targetId, JSON.stringify(payload), new Date().toISOString(), 0, 'PENDING']\n" +
                "    );\n" +
                "    this.notify();\n" +
                "    return clientId;\n" +
                "  },\n\n" +
                "  async syncPendingQueue(): Promise<{ total: number; synced: number; failed: number }> {\n" +
                "    if (isSyncing) return { total: 0, synced: 0, failed: 0 };\n" +
                "    isSyncing = true;\n" +
                "    this.notify();\n\n" +
                "    try {\n" +
                "      const db = getDatabase();\n" +
                "      if (!db) {\n" +
                "        isSyncing = false;\n" +
                "        this.notify();\n" +
                "        return { total: 0, synced: 0, failed: 0 };\n" +
                "      }\n" +
                "      const pending = db.getAllSync<{\n" +
                "        id: number;\n" +
                "        client_id: string;\n" +
                "        entity_name: string;\n" +
                "        operation: string;\n" +
                "        target_id: string | null;\n" +
                "        payload: string;\n" +
                "      }>(\"SELECT * FROM offline_sync_queue WHERE status = 'PENDING' ORDER BY id ASC\");\n\n" +
                "      if (pending.length === 0) {\n" +
                "        isSyncing = false;\n" +
                "        this.notify();\n" +
                "        return { total: 0, synced: 0, failed: 0 };\n" +
                "      }\n\n" +
                "      const mutations = pending.map((p) => ({\n" +
                "        clientId: p.client_id,\n" +
                "        entityName: p.entity_name,\n" +
                "        operation: p.operation,\n" +
                "        targetId: p.target_id,\n" +
                "        payload: JSON.parse(p.payload),\n" +
                "      }));\n\n" +
                "      try {\n" +
                "        const res = await api<{ results: { clientId: string; success: boolean; message?: string }[] }>('/api/sync/batch', {\n" +
                "          method: 'POST',\n" +
                "          body: JSON.stringify({ mutations }),\n" +
                "        });\n\n" +
                "        let synced = 0;\n" +
                "        let failed = 0;\n" +
                "        for (const r of res.results) {\n" +
                "          if (r.success) {\n" +
                "            db.runSync(\"UPDATE offline_sync_queue SET status = 'SYNCED' WHERE client_id = ?\", [r.clientId]);\n" +
                "            synced++;\n" +
                "          } else {\n" +
                "            db.runSync(\"UPDATE offline_sync_queue SET retry_count = retry_count + 1 WHERE client_id = ?\", [r.clientId]);\n" +
                "            failed++;\n" +
                "          }\n" +
                "        }\n" +
                "        return { total: pending.length, synced, failed };\n" +
                "      } catch (batchErr) {\n" +
                "        let synced = 0;\n" +
                "        let failed = 0;\n" +
                "        for (const item of mutations) {\n" +
                "          try {\n" +
                "            const resource = item.entityName.toLowerCase().replace(/_/g, '-');\n" +
                "            if (item.operation === 'CREATE') {\n" +
                "              await api(`/api/${resource}`, { method: 'POST', body: JSON.stringify(item.payload) });\n" +
                "            } else if (item.operation === 'UPDATE' && item.targetId) {\n" +
                "              await api(`/api/${resource}/${encodeURIComponent(item.targetId)}`, { method: 'PUT', body: JSON.stringify(item.payload) });\n" +
                "            } else if (item.operation === 'DELETE' && item.targetId) {\n" +
                "              await api(`/api/${resource}/${encodeURIComponent(item.targetId)}`, { method: 'DELETE' });\n" +
                "            }\n" +
                "            db.runSync(\"UPDATE offline_sync_queue SET status = 'SYNCED' WHERE client_id = ?\", [item.clientId]);\n" +
                "            synced++;\n" +
                "          } catch (itemErr) {\n" +
                "            db.runSync(\"UPDATE offline_sync_queue SET retry_count = retry_count + 1 WHERE client_id = ?\", [item.clientId]);\n" +
                "            failed++;\n" +
                "          }\n" +
                "        }\n" +
                "        return { total: pending.length, synced, failed };\n" +
                "      }\n" +
                "    } finally {\n" +
                "      isSyncing = false;\n" +
                "      this.notify();\n" +
                "    }\n" +
                "  },\n" +
                "};\n";
    }

    private static final class SpringTables {
        private static List<Table> generated(List<Table> tables) {
            return tables.stream().filter(table -> !isPureJoinTable(table)).toList();
        }

        private static boolean isPureJoinTable(Table table) {
            if (table.primaryKey() == null || table.primaryKey().columns().size() != 2 || table.foreignKeys().size() != 2) return false;
            java.util.HashSet<String> keys = new java.util.HashSet<>(table.primaryKey().columns());
            java.util.HashSet<String> fkColumns = new java.util.HashSet<>();
            table.foreignKeys().forEach(foreignKey -> foreignKey.columns().forEach(column -> fkColumns.add(column.column())));
            return keys.equals(fkColumns) && table.columns().size() == 2;
        }
    }
}
