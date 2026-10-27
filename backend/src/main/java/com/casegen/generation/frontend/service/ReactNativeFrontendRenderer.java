package com.casegen.generation.frontend.service;

import com.casegen.generation.logical.dto.LogicalModelDtos.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Deterministic React Native client generated from an immutable logical model. */
@Component
public class ReactNativeFrontendRenderer {
    public record Traceability(String modelName, long modelVersionNumber) {}

    public void render(Path root, Response input, Traceability trace) throws IOException {
        JsonNode profile = input.profileSnapshot().path("profile");
        String appName = safeAppName(profile.path("artifactId").asString("generated-app"));
        put(root, "package.json", packageJson(appName));
        put(root, "app.json", appJson(appName));
        put(root, "tsconfig.json", tsconfig());
        put(root, "babel.config.js", "module.exports = { presets: ['babel-preset-expo'] };\n");
        put(root, ".env.example", "EXPO_PUBLIC_API_BASE_URL=http://localhost:8080\n# Android emulator: http://10.0.2.2:8080\n");
        put(root, "src/config.ts", "import { Platform } from 'react-native';\nconst localhost = Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';\nexport const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? localhost;\n");
        put(root, "src/api/client.ts", client());
        put(root, "src/auth/AuthContext.tsx", authContext());
        put(root, "src/screens/LoginScreen.tsx", loginScreen());
        put(root, "App.tsx", app());
        put(root, "README.md", readme(appName, trace));

        for (Table table : SpringTables.generated(input.tables())) {
            String resource = resourceName(table.name());
            String screen = screenName(table.name());
            put(root, "src/screens/" + screen + "Screen.tsx", crudScreen(table, resource, screen));
        }

        put(root, "src/navigation.ts", navigation(input.tables()));
    }

    private String packageJson(String appName) {
        return "{\n" +
                "  \"name\": \"" + appName + "\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"main\": \"expo/AppEntry.js\",\n" +
                "  \"private\": true,\n" +
                "  \"scripts\": { \"start\": \"expo start\", \"android\": \"expo start --android\", \"web\": \"expo start --web\", \"typecheck\": \"tsc --noEmit\" },\n" +
                "  \"dependencies\": { \"@react-navigation/native\": \"^7.1.17\", \"@react-navigation/native-stack\": \"^7.3.26\", \"expo\": \"~54.0.0\", \"expo-status-bar\": \"~3.0.8\", \"react\": \"19.1.0\", \"react-native\": \"0.81.4\", \"react-native-safe-area-context\": \"^5.6.1\", \"react-native-screens\": \"~4.16.0\" },\n" +
                "  \"devDependencies\": { \"@types/react\": \"~19.1.10\", \"typescript\": \"~5.9.2\" }\n" +
                "}\n";
    }

    private String appJson(String appName) {
        return "{\n  \"expo\": {\n    \"name\": \"" + appName + "\",\n    \"slug\": \"" + appName + "\",\n    \"scheme\": \"" + appName + "\",\n    \"version\": \"1.0.0\",\n    \"orientation\": \"portrait\",\n    \"platforms\": [\"android\",\"ios\",\"web\"]\n  }\n}\n";
    }

    private String tsconfig() {
        return "{ \"extends\": \"expo/tsconfig.base\", \"compilerOptions\": { \"strict\": true, \"noEmit\": true }, \"include\": [\"**/*.ts\", \"**/*.tsx\"] }\n";
    }

    private String client() {
        return "import { API_BASE_URL } from '../config';\n\nlet accessToken: string | null = null;\nexport function setAccessToken(token: string | null) { accessToken = token; }\nexport async function api<T>(path: string, init: RequestInit = {}): Promise<T> {\n  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers: { 'Content-Type': 'application/json', ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}), ...init.headers } });\n  if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);\n  if (response.status === 204) return undefined as T;\n  return response.json() as Promise<T>;\n}\n";
    }

    private String authContext() {
        return "import React, { createContext, useContext, useState } from 'react';\nimport { api, setAccessToken } from '../api/client';\n\ntype AuthContextValue = { authenticated: boolean; login: (email: string, password: string) => Promise<void>; register: (displayName: string, email: string, password: string) => Promise<void>; logout: () => void };\nconst AuthContext = createContext<AuthContextValue | null>(null);\nexport function AuthProvider({ children }: { children: React.ReactNode }) {\n  const [authenticated, setAuthenticated] = useState(false);\n  const login = async (email: string, password: string) => {\n    const result = await api<{ accessToken: string }>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });\n    setAccessToken(result.accessToken); setAuthenticated(true);\n  };\n  const register = async (displayName: string, email: string, password: string) => {\n    const result = await api<{ accessToken: string }>('/api/auth/register', { method: 'POST', body: JSON.stringify({ displayName, email, password }) });\n    setAccessToken(result.accessToken); setAuthenticated(true);\n  };\n  const logout = () => { setAccessToken(null); setAuthenticated(false); };\n  return <AuthContext.Provider value={{ authenticated, login, register, logout }}>{children}</AuthContext.Provider>;\n}\nexport function useAuth() { const value = useContext(AuthContext); if (!value) throw new Error('AuthProvider required'); return value; }\n";
    }

    private String loginScreen() {
        return "import React, { useState } from 'react';\nimport { ActivityIndicator, Button, StyleSheet, Text, TextInput, View } from 'react-native';\nimport { useAuth } from '../auth/AuthContext';\nexport function LoginScreen() {\n  const { login, register } = useAuth(); const [mode, setMode] = useState<'login'|'register'>('login'); const [displayName, setDisplayName] = useState(''); const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [error, setError] = useState<string>(); const [busy, setBusy] = useState(false);\n  const submit = async () => { setBusy(true); setError(undefined); try { mode === 'register' ? await register(displayName, email, password) : await login(email, password); } catch (reason) { setError(reason instanceof Error ? reason.message : 'No se pudo completar la solicitud'); } finally { setBusy(false); } };\n  return <View style={styles.container}><Text style={styles.title}>{mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}</Text>{mode === 'register' && <TextInput placeholder='Nombre' style={styles.input} value={displayName} onChangeText={setDisplayName}/>}<TextInput placeholder='Email' autoCapitalize='none' keyboardType='email-address' style={styles.input} value={email} onChangeText={setEmail}/><TextInput placeholder='Contraseña' secureTextEntry style={styles.input} value={password} onChangeText={setPassword}/>{error && <Text style={styles.error}>{error}</Text>}{busy ? <ActivityIndicator/> : <><Button title={mode === 'login' ? 'Entrar' : 'Crear cuenta'} onPress={submit}/><View style={styles.switch}><Button title={mode === 'login' ? 'Registrarme' : 'Ya tengo cuenta'} onPress={() => setMode(mode === 'login' ? 'register' : 'login')}/></View></>}</View>;\n}\nconst styles = StyleSheet.create({ container: { flex: 1, justifyContent: 'center', padding: 24 }, title: { fontSize: 24, fontWeight: '700', marginBottom: 24 }, input: { borderWidth: 1, borderColor: '#CBD5E1', borderRadius: 8, padding: 12, marginBottom: 12 }, error: { color: '#B91C1C', marginBottom: 12 }, switch: { marginTop: 12 } });\n";
    }

    private String app() {
        return "import React from 'react';\nimport { ActivityIndicator, NavigationContainer } from '@react-navigation/native';\nimport { createNativeStackNavigator } from '@react-navigation/native-stack';\nimport { AuthProvider, useAuth } from './src/auth/AuthContext';\nimport { LoginScreen } from './src/screens/LoginScreen';\nimport { AppNavigator } from './src/navigation';\nconst Stack = createNativeStackNavigator();\nfunction Root() { const { authenticated } = useAuth(); return <NavigationContainer>{authenticated ? <AppNavigator/> : <Stack.Navigator><Stack.Screen name='Login' component={LoginScreen} options={{ headerShown: false }}/></Stack.Navigator>}</NavigationContainer>; }\nexport default function App() { return <AuthProvider><Root/></AuthProvider>; }\n";
    }

    private String navigation(List<Table> tables) {
        StringBuilder screens = new StringBuilder();
        StringBuilder registrations = new StringBuilder();
        for (Table table : SpringTables.generated(tables)) {
            String resource = resourceName(table.name());
            String screen = screenName(table.name());
            screens.append("import { ").append(screen).append("Screen } from './screens/").append(screen).append("Screen';\n");
            registrations.append("      <Stack.Screen name='").append(screen).append("' component={").append(screen).append("Screen} />\n");
        }
        return screens + "import React from 'react';\nimport { createNativeStackNavigator } from '@react-navigation/native-stack';\nconst Stack = createNativeStackNavigator();\nexport function AppNavigator() { return <Stack.Navigator>\n" + registrations + "    </Stack.Navigator>; }\n";
    }

    private String crudScreen(Table table, String resource, String screen) {
        StringBuilder fields = new StringBuilder();
        StringBuilder state = new StringBuilder();
        StringBuilder inputs = new StringBuilder();
        for (Column column : table.columns()) {
            if (isPrimaryKey(table, column.name())) continue;
            String name = fieldName(column.name());
            state.append("  const [").append(name).append(", set").append(cap(name)).append("] = useState('');\n");
            inputs.append("<TextInput value={").append(name).append("} onChangeText={set").append(cap(name)).append("} placeholder='").append(column.name()).append("' style={styles.input}/>\n");
            if (fields.length() > 0) fields.append(',');
            fields.append(name).append(": ").append(name);
        }
        return "import React, { useEffect, useState } from 'react';\nimport { Alert, Button, FlatList, StyleSheet, Text, TextInput, View } from 'react-native';\nimport { api } from '../api/client';\ntype Item = Record<string, unknown> & { id?: string };\nexport function " + screen + "Screen() {\n  const [items, setItems] = useState<Item[]>([]);\n" + state + "  const load = async () => setItems(await api<Item[]>('/api/" + resource + "'));\n  useEffect(() => { void load(); }, []);\n  const create = async () => { await api('/api/" + resource + "', { method: 'POST', body: JSON.stringify({ " + fields + " }) }); await load(); "+stateKeyReset(state)+" };\n  const update = async (item: Item) => { await api(`/api/" + resource + "/${item.id}`, { method: 'PUT', body: JSON.stringify({ " + fields + " }) }); await load(); };\n  const remove = async (item: Item) => { await api(`/api/" + resource + "/${item.id}`, { method: 'DELETE' }); await load(); };\n  return <View style={styles.container}><Text style={styles.title}>" + screen + "</Text>" + inputs + "<Button title='Crear' onPress={() => void create().catch(reason => Alert.alert('Error', String(reason)))}/><FlatList data={items} keyExtractor={(item, index) => String(item.id ?? index)} renderItem={({ item }) => <View style={styles.row}><Text>{JSON.stringify(item)}</Text><Button title='Actualizar' onPress={() => void update(item).catch(reason => Alert.alert('Error', String(reason)))}/><Button title='Eliminar' onPress={() => void remove(item).catch(reason => Alert.alert('Error', String(reason)))}/></View>}/></View>;\n}\nconst styles = StyleSheet.create({ container: { flex: 1, padding: 16 }, title: { fontSize: 22, fontWeight: '700', marginBottom: 12 }, input: { borderWidth: 1, borderColor: '#CBD5E1', borderRadius: 8, padding: 10, marginBottom: 8 }, row: { borderBottomWidth: 1, borderColor: '#E2E8F0', paddingVertical: 10 } });\n";
    }

    private String readme(String appName, Traceability trace) {
        return "# " + appName + "\n\nFrontend React Native generado por CASEGen. Consume el backend Spring Boot generado desde el mismo LogicalModel.\n\n## Ejecución\n\n```powershell\nnpm install\nCopy-Item .env.example .env.local\nnpx expo start\n```\n\nConfigure `EXPO_PUBLIC_API_BASE_URL` con la URL del backend generado.\nLa app incluye login básico y CRUD REST para las tablas generadas.\n\nOrigen: modelo `" + trace.modelName() + "`, versión v" + trace.modelVersionNumber() + ".\n";
    }

    private static boolean isPrimaryKey(Table table, String columnName) {
        return table.primaryKey() != null && table.primaryKey().columns().contains(columnName);
    }

    private static String stateKeyReset(StringBuilder state) {
        String source = state.toString();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[([A-Za-z0-9]+), set[A-Za-z0-9]+\\]").matcher(source);
        StringBuilder reset = new StringBuilder();
        while (matcher.find()) reset.append("set").append(cap(matcher.group(1))).append("('');");
        return reset.toString();
    }

    private static String safeAppName(String value) { String result = value.replaceAll("[^A-Za-z0-9-_]", "-").toLowerCase(Locale.ROOT); return result.isBlank() ? "generated-mobile-app" : result; }
    private static String resourceName(String value) { return fieldName(value).replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT) + "s"; }
    private static String screenName(String value) { StringBuilder result = new StringBuilder(); for (String part : value.split("[-_]")) result.append(cap(part)); return result.toString(); }
    private static String fieldName(String value) { String name = screenName(value); return name.isEmpty() ? "value" : Character.toLowerCase(name.charAt(0)) + name.substring(1); }
    private static String cap(String value) { return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1); }
    private static void put(Path root, String relative, String content) throws IOException { Path path = root.resolve(relative).normalize(); if (!path.startsWith(root.normalize())) throw new IOException("Unsafe generated path"); Files.createDirectories(path.getParent()); Files.writeString(path, content, StandardCharsets.UTF_8); }

    private static final class SpringTables {
        private static List<Table> generated(List<Table> tables) { return tables.stream().filter(table -> !isPureJoinTable(table)).toList(); }
        private static boolean isPureJoinTable(Table table) {
            if (table.primaryKey() == null || table.primaryKey().columns().size() != 2 || table.foreignKeys().size() != 2) return false;
            java.util.HashSet<String> keys = new java.util.HashSet<>(table.primaryKey().columns());
            java.util.HashSet<String> fkColumns = new java.util.HashSet<>();
            table.foreignKeys().forEach(foreignKey -> foreignKey.columns().forEach(column -> fkColumns.add(column.column())));
            return keys.equals(fkColumns) && table.columns().size() == 2;
        }
    }
}
