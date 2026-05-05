# Deploy Demo

Este caminho sobe a API em profile `prod` para uma demo pública controlada. O playground fica disponível, mas as demos ofensivas permanecem desligadas por `application-prod.yml`.

## Opção recomendada: Render Blueprint

O arquivo `render.yaml` provisiona:

- Web Service Docker da API.
- Render Postgres.
- Render Key Value compatível com Redis.
- Secrets obrigatórios para JWT via `sync: false`.

No Dashboard da Render:

1. Clique em **Blueprints**.
2. Clique em **New Blueprint Instance**.
3. Conecte o repositório `omatheusdutra/teste-api-spring-egsys`.
4. Confirme o Blueprint detectado em `render.yaml`.
5. Preencha `EGSYS_JWT_PRIVATE_KEY` e `EGSYS_JWT_PUBLIC_KEY` com PEMs usando `\n` escapado.
6. Depois do primeiro deploy, abra `/playground/config` e confirme `attackDemosEnabled=false`.

Se a URL gerada não for `https://egsys-tasks-api.onrender.com`, ajuste `EGSYS_ALLOWED_ORIGINS` no serviço web para a URL real.

## Opção local/VPS: Docker Compose prod

### 1. Preparar secrets

```bash
cp deploy/demo.env.example deploy/demo.env
```

Edite `deploy/demo.env` e troque todos os placeholders. Para gerar chaves RSA:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl rsa -pubout -in private.pem -out public.pem
```

Converta os PEMs para uma linha com `\n` escapado antes de colocar no env.

PowerShell:

```powershell
$priv = (Get-Content private.pem -Raw).Trim() -replace "`r?`n", "\n"
$pub = (Get-Content public.pem -Raw).Trim() -replace "`r?`n", "\n"
```

### 2. Subir

```bash
docker compose --env-file deploy/demo.env -f docker-compose.prod.yml up -d --build
```

Por padrão a API fica em `127.0.0.1:8080`. Use um reverse proxy com HTTPS, por exemplo Caddy ou Nginx, apontando para esse endereço.

### 3. Validar

```bash
curl -fsS http://127.0.0.1:8080/playground/config
curl -fsS http://127.0.0.1:8080/actuator/health
```

Checklist antes de divulgar:

- HTTPS ativo no domínio público.
- `EGSYS_ALLOWED_ORIGINS` com o domínio real, sem `*`.
- `/playground/config` retorna `attackDemosEnabled=false`.
- `/actuator/prometheus` exige autenticação.
- `deploy/demo.env` não foi commitado.
