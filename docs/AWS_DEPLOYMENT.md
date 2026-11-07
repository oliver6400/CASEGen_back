# CASEGen - AWS Deployment Guide & Production Architecture

This guide details how to deploy the entire CASEGen platform to Amazon Web Services (AWS).

---

## 1. Architectural Overview

```
                      +-----------------------------+
                      |   Cloudflare / Route 53     |
                      |   (DNS & SSL Termination)   |
                      +--------------+--------------+
                                     |
                       HTTPS (443) / WSS (WebSocket)
                                     v
                 +---------------------------------------+
                 |       AWS EC2 / Application LB       |
                 |  +---------------------------------+  |
                 |  |  Nginx Reverse Proxy            |  |
                 |  |  - /       -> Web Frontend (SPA)|  |
                 |  |  - /api/   -> Spring Boot Backend|  |
                 |  |  - /ws/    -> WebSocket Realtime |  |
                 |  +----------------+----------------+  |
                 |                   |                   |
                 |      Internal Docker Network          |
                 |   +---------------+---------------+   |
                 |   |               |               |   |
                 |   v               v               v   |
                 | Backend       PostgreSQL       Ollama |
                 | (Spring)     (or AWS RDS)     (AI LLM)|
                 +---------------------------------------+
```

---

## 2. Infrastructure Options

### Option A: Single EC2 Instance (Recommended for Prototyping / Evaluation)
- **Instance Type**: `t3.large` (2 vCPU, 8 GB RAM) or `g4dn.xlarge` (if GPU-accelerated Ollama is required).
- **Storage**: 40 GB gp3 EBS Volume.
- **OS**: Ubuntu 22.04 LTS / 24.04 LTS.
- **Orchestration**: `docker compose up -d` manages PostgreSQL, Backend, Frontend (Nginx), and Ollama.

### Option B: High-Availability AWS Native Stack
- **Compute**: AWS ECS (Elastic Container Service) with Fargate or EC2 Launch Type.
- **Database**: AWS RDS PostgreSQL 16/17 (Multi-AZ, db.t4g.medium).
- **Frontend & Static Assets**: AWS S3 + CloudFront CDN.
- **Secrets Management**: AWS Systems Manager Parameter Store or AWS Secrets Manager.

---

## 3. Step-by-Step EC2 Deployment Walkthrough

### Step 3.1. Provision the EC2 Instance
1. Log in to the AWS Management Console and navigate to **EC2**.
2. Click **Launch Instance**:
   - **Name**: `casegen-production`
   - **AMI**: Ubuntu Server 24.04 LTS (HVM), SSD Volume Type.
   - **Instance Type**: `t3.large` or `c6i.large`.
   - **Key Pair**: Select or create an `.pem` key pair (e.g., `casegen-key.pem`).
3. **Security Group Configuration**:
   Configure inbound security rules:
   | Type | Port | Source | Purpose |
   | :--- | :--- | :--- | :--- |
   | SSH | 22 | Your IP / 0.0.0.0/0 | Remote administration |
   | HTTP | 80 | 0.0.0.0/0 | Let's Encrypt / HTTP redirect |
   | HTTPS | 443 | 0.0.0.0/0 | Secure Web, API & WebSocket |
   | Custom TCP | 8080 (optional) | Restricted / Internal | Direct backend inspection |

---

### Step 3.2. Prepare the EC2 Server
Connect via SSH:
```bash
ssh -i "casegen-key.pem" ubuntu@<EC2-PUBLIC-IP>
```

Install Docker, Docker Compose, Git, and utilities:
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl wget git unzip htop

# Install Docker Engine & Compose plugin
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker ubuntu

# Apply user group changes
newgrp docker
```

---

### Step 3.3. Clone Repository & Set Environment Variables
```bash
git clone https://github.com/<YOUR_USER>/<YOUR_REPO>.git casegen
cd casegen

# Create production environment file
cp .env.production.example .env
nano .env
```
Fill in the database password, JWT secret, and your domain name.

---

### Step 3.4. Pull Ollama Model on EC2 (or Install Ollama natively)
If running Ollama in Docker or directly on the host:
```bash
# Option 1: Native Ollama on Host for performance
curl -fsSL https://ollama.com/install.sh | sh
ollama pull qwen3:4b
```

---

### Step 3.5. Launch with Docker Compose
```bash
docker compose up -d --build
```
Verify all containers are healthy:
```bash
docker compose ps
docker compose logs -f backend
```

---

### Step 3.6. Configure SSL Certificate with Let's Encrypt (Certbot)
To obtain a free production SSL certificate for your custom domain:
```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d yourdomain.com -d api.yourdomain.com
```
Certbot will automatically configure HTTPS certificates and set up auto-renewal.

---

## 4. Mobile APK Connection to Production

Once your AWS server has an active domain or Elastic IP:
1. Open `mobile/lib/core/config/api_config.dart`.
2. Update the production API endpoint:
   ```dart
   static const String _defaultProdBaseUrl = 'https://yourdomain.com';
   ```
3. Or supply it at build time:
   ```bash
   flutter build apk --release --dart-define=API_URL=https://yourdomain.com
   ```
4. The generated APK communicates securely via HTTPS/WSS with the AWS instance.
