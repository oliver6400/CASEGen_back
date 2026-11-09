# CASEGen - Guía de Despliegue en Microsoft Azure

Esta guía explica la arquitectura y los pasos para desplegar el sistema completo de **CASEGen** (Backend Spring Boot, Frontend Web React/Vite, Base de Datos PostgreSQL, IA Ollama/Gemini y la APK Móvil) en la nube de **Microsoft Azure**.

---

## 1. Requerimientos del Sistema en Azure

Para ejecutar todo el ecosistema de CASEGen en Azure se requiere:

1. **Suscripción de Azure activa**:
   - Cuenta estándar, cuenta de evaluación gratuita (\$200 USD de crédito) o suscripción **Azure for Students** (\$100 USD anuales sin tarjeta de crédito).
2. **Capacidad de Cómputo**:
   - **Con IA Local (Ollama `qwen3:4b`)**: Mínimo **4 vCPUs y 16 GB de RAM** (ejemplo: `Standard_B4ms` o `Standard_D4s_v5`).
   - **Con IA Cloud (Google Gemini API)**: Basta con **2 vCPUs y 4-8 GB de RAM** (ejemplo: `Standard_B2s` o `Standard_B2ms`), lo cual reduce drásticamente el costo.
3. **Almacenamiento**:
   - Disco SSD administrado (Premium SSD o Standard SSD) de al menos **40-64 GB** para el sistema operativo, imágenes Docker y los pesos del modelo LLM.
4. **Red y Puertos Abiertos (NSG - Network Security Group)**:
   - `22` (SSH): Administración remota.
   - `80` (HTTP): Redirección y certificados SSL con Let's Encrypt.
   - `443` (HTTPS / WSS): Acceso seguro web, API y WebSocket en tiempo real.

---

## 2. Arquitectura Recomendada: Azure VM con Docker Compose

La arquitectura más eficiente, económica y fácil de mantener para CASEGen es una **Máquina Virtual Linux (Ubuntu 24.04 LTS)** ejecutando la pila completa orquestada con el `docker-compose.yml` que ya dejamos preparado en el proyecto:

```
                      Internet / Clientes (Web y Móvil)
                                     |
                                HTTPS (443)
                                     v
                 +---------------------------------------+
                 |       Azure Virtual Machine (Linux)  |
                 |                                       |
                 |  +---------------------------------+  |
                 |  | Nginx Reverse Proxy (Frontend)  |  |
                 |  | - /      -> React 19 SPA        |  |
                 |  | - /api/  -> Spring Boot Backend |  |
                 |  | - /ws/   -> WebSocket STOMP     |  |
                 |  +----------------+----------------+  |
                 |                   | (Red interna)     |
                 |   +---------------+---------------+   |
                 |   |               |               |   |
                 |   v               v               v   |
                 | Backend       PostgreSQL       Ollama |
                 | (Java 21)     (Postgres 17)   (Qwen3) |
                 +---------------------------------------+
```

---

## 3. Paso a Paso para Desplegar en Azure (Desde el Portal Web)

### Paso 1: Crear la Máquina Virtual en Azure Portal
1. Entra a [https://portal.azure.com/](https://portal.azure.com/).
2. En la barra superior busca **"Virtual machines"** (Máquinas virtuales) y pulsa **Crear > Máquina virtual de Azure**.
3. Configura los datos básicos:
   - **Grupo de recursos**: Crear nuevo, ej. `rg-casegen-prod`.
   - **Nombre de máquina virtual**: `vm-casegen`.
   - **Región**: `East US` (o la más cercana con mejor latencia).
   - **Imagen**: **Ubuntu Server 24.04 LTS - x64 Gen2**.
   - **Tamaño (Size)**:
     - Si usarás Ollama local: selecciona **Standard_B4ms** (4 vCPUs, 16 GiB RAM).
     - Si usarás Gemini cloud: selecciona **Standard_B2ms** (2 vCPUs, 8 GiB RAM).
4. Cuenta de administrador:
   - Tipo de autenticación: **Clave pública SSH** o **Contraseña**.
   - Nombre de usuario: `azureuser`.
   - Guarda tu clave `.pem` si usas clave SSH.
5. Reglas de puerto de entrada:
   - Marca: **SSH (22)**, **HTTP (80)** y **HTTPS (443)**.
6. En la pestaña **Discos (Disks)**:
   - Cambia el tamaño del disco del SO a **64 GB** (para alojar contenedores y modelos).
7. Pulsa **Revisar y crear** y luego **Crear**.

---

### Paso 2: Asignar un Nombre DNS Gratuito en Azure
Azure te permite tener un dominio público gratuito sin necesidad de comprar uno:
1. Una vez creada la VM, entra en el recurso `vm-casegen`.
2. En el panel izquierdo ve a **Información general (Overview)** y busca **Dirección IP pública**.
3. Haz clic en **Configurar** o pulsa sobre la IP:
   - En **Etiqueta de nombre DNS (DNS name label)** escribe: `casegen-valeria` (o el nombre que elijas).
   - Guarda los cambios. Tu dominio será: `casegen-valeria.eastus.cloudapp.azure.com`.

---

### Paso 3: Conectarse e Instalar Docker en la VM
Abre tu terminal (PowerShell o Git Bash) y conéctate por SSH:
```bash
ssh -i "tu-clave.pem" azureuser@casegen-valeria.eastus.cloudapp.azure.com
```

Dentro del servidor Ubuntu, instala Docker y Git:
```bash
# Actualizar el sistema
sudo apt update && sudo apt upgrade -y

# Instalar utilidades y Docker
sudo apt install -y git curl wget htop ufw
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker azureuser

# Aplicar permisos
newgrp docker
```

---

### Paso 4: Clonar el Repositorio y Configurar Variables
```bash
# Clonar rama valeria
git clone -b valeria https://github.com/oliver6400/CASEGen_back.git casegen
cd casegen

# Crear archivo de entorno de producción
cp .env.production.example .env
nano .env
```
En el archivo `.env`:
- Define una contraseña segura para `DB_PASSWORD`.
- Genera un `JWT_SECRET` seguro.
- En `PUBLIC_DOMAIN`, coloca: `casegen-valeria.eastus.cloudapp.azure.com`.
- Si usas Gemini, coloca tu `GEMINI_API_KEY`. Guarda con `Ctrl+O` y sal con `Ctrl+X`.

---

### Paso 5: Levantar el Sistema con Docker Compose
```bash
docker compose up -d --build
```
Para verificar que todos los servicios estén activos:
```bash
docker compose ps
docker compose logs -f backend
```
El backend aplicará automáticamente las migraciones de Flyway (incluyendo los **10 usuarios de producción** con la migración `V16`).

---

### Paso 6: Certificado SSL Gratuito (HTTPS) con Certbot
Para que los navegadores y teléfonos móviles se conecten con candado verde seguro:
```bash
sudo apt install -y certbot
# Obtener certificado para tu subdominio de Azure
sudo certbot certonly --standalone -d casegen-valeria.eastus.cloudapp.azure.com
```

---

## 4. Conectar la APK Móvil a Azure

Una vez desplegado tu servidor en Azure:
1. En el proyecto móvil, edita [`mobile/lib/core/config/api_config.dart`](file:///c:/UNIVERSIDAD/SW1/2do%20parcial/diagramador_version_2/CASEGen_back/mobile/lib/core/config/api_config.dart):
   ```dart
   static const String _defaultProdBaseUrl = 'https://casegen-valeria.eastus.cloudapp.azure.com';
   ```
2. O compila la APK pasando la variable de entorno:
   ```bash
   cd mobile
   flutter build apk --release --dart-define=API_URL=https://casegen-valeria.eastus.cloudapp.azure.com
   ```
3. Instala `app-release.apk` en los teléfonos físicos. La app consumirá la API y los WebSockets de Azure de forma transparente.
