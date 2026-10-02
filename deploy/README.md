# Deploy na Oracle Cloud (Always Free)

API em Docker numa VM ARM da Oracle, atrás do **Caddy compartilhado** da VM
(fica em `~/infra`, atende todos os projetos e cuida do HTTPS).
O banco continua no **Neon**. O front continua na **Vercel**.

```
Vercel (front) ──HTTPS──▶ Caddy :443 ──▶ myrank-api :8080 ──▶ Neon (Postgres)
                          └────────── VM Oracle (rede `web`) ─┘
```

O Caddy acha a API pelo `container_name` (`myrank-api`), e os dois conversam
pela rede Docker `web`. Se a VM for recriada do zero, crie a rede antes de tudo:
`docker network create web`.

## 1. Conta na Oracle

- **Home region não muda depois.** Escolha a mesma região do Neon (ou a mais
  perto dela). A região do Neon aparece no host do `DB_URL`, por exemplo
  `...sa-east-1.aws.neon.tech` → São Paulo (`sa-saopaulo-1`).
- O cadastro pede cartão só pra verificação.
- **Recomendado: fazer upgrade pra "Pay As You Go"** (Billing → Upgrade).
  Continua de graça enquanto você fica dentro dos limites Always Free, e evita
  dois problemas das contas só-free:
  - a Oracle **recupera VMs Always Free ociosas** (CPU/rede/memória baixas por
    7 dias), e um site pequeno como o MyRank cai nesse critério;
  - o erro "Out of capacity" ao criar VM ARM é bem mais comum.
  - Crie um **Budget** com alerta em US$ 1 (Billing → Budgets) pra saber se
    algo começar a cobrar.

## 2. Criar a VM

Compute → Instances → Create instance:

- **Image:** Canonical Ubuntu 24.04 (a versão normal, não a "Minimal")
- **Shape:** Ampere → `VM.Standard.A1.Flex`, 4 OCPU e 24 GB (todo o limite grátis)
- **Networking:** crie uma VCN nova com subnet pública e marque "Assign public IPv4"
- **SSH keys:** baixe a chave privada e guarde bem
- **Boot volume:** o padrão (~47 GB) está dentro do grátis

Depois de criada, anote o **IP público**. Como deixar ele fixo:
Instance → Attached VNICs → IPv4 addresses → editar → Reserved public IP.

## 3. Liberar as portas 80 e 443

1. **No painel:** Networking → Virtual Cloud Networks → sua VCN → Security Lists →
   Default → Add Ingress Rules:
   - Source `0.0.0.0/0`, TCP, porta `80`
   - Source `0.0.0.0/0`, TCP, porta `443`
2. **Na VM:** o `setup-vm.sh` abre as portas no firewall do Ubuntu.
   É preciso fazer os dois.

## 4. Domínio

O Caddy precisa de um domínio pra emitir o certificado HTTPS (o front na Vercel
é HTTPS e não consegue chamar uma API em HTTP puro).

- **Grátis:** [DuckDNS](https://www.duckdns.org). Crie um subdomínio
  (ex.: `myrank-api`) apontando pro IP da VM.
- **Domínio próprio:** crie um registro `A` apontando pro IP da VM.

## 5. Subir a API

```bash
ssh -i sua-chave.key ubuntu@IP_DA_VM

git clone https://github.com/guiGocksAfK/MyRank-backend.git
cd MyRank-backend
bash deploy/setup-vm.sh
exit                       # entre de novo no SSH

docker network create web  # uma vez por VM
cd ~/infra                 # Caddy compartilhado (docker-compose.yml + Caddyfile)
docker compose up -d

cd ~/MyRank-backend/deploy
cp .env.example .env
nano .env                  # preencha tudo (valores atuais: painel do Render → Environment)
docker compose up -d --build
docker compose logs -f api # espere o "Started MyRankApplication"
```

O `~/infra/Caddyfile` precisa de um bloco pro domínio deste projeto:

```
myrank.duckdns.org {
	encode zstd gzip
	reverse_proxy myrank-api:8080
}
```

Teste: `https://SEU_DOMINIO/api/health` deve responder `{"status":"ok"}`.

## 6. Virar a chave

1. **Vercel** → projeto do front → Settings → Environment Variables:
   `VITE_API_URL=https://SEU_DOMINIO/api` → faça **Redeploy** (variável `VITE_`
   só entra no build).
2. Teste login (e-mail, Google e Discord), dashboard, chat e IA Insights.
3. Deu tudo certo → **suspenda** o serviço no Render (não apague ainda; é o
   plano B por uns dias).

## Atualizar depois

```bash
cd ~/MyRank-backend && git pull && cd deploy && docker compose up -d --build && docker image prune -f
```

## Atualizar a produção para esta versão

Esta versão consolida as 16 migrations antigas em V1–V8 (squash) e traz V9
(templates), V10 (campos próprios) e V11 (tutorial pós-cadastro). O banco de
produção já rodou as 16 antigas, então ele **não** reaplica nada: o Flyway só
passa a considerar que ele está na V8 (baseline) e segue da V9. Nenhum dado sai
do lugar.

Back e front sobem **juntos**: o front novo depende dos endpoints novos, e o
cadastro mudou de formato. Faça numa janela em que ninguém esteja usando.

### 1. Backup antes de tudo

- **Neon → Branches → Create branch** a partir de `main`, com um nome tipo
  `antes-do-squash`. É uma cópia instantânea do banco inteiro, e é pra onde se
  volta se algo der errado.
- Rode também o dump da VM na hora: `python3 ~/backup-bancos.py`.

### 2. Conferir que a produção está na versão 16 antiga

```sql
SELECT max(version::int) FROM flyway_schema_history WHERE success;
```

Precisa dar **16**. Se der menos, a produção ainda não tem alguma migration
antiga, e o baseline marcaria como aplicado algo que não existe. Nesse caso,
suba primeiro o último commit antes do squash (`4dcb46f`), deixe o Flyway
aplicar o que falta e só então continue.

### 3. Provar que o schema bate com V1–V8 (recomendado)

Gere a "impressão digital" do banco de produção e a de um Postgres descartável
com V1–V8, na **mesma versão do Neon** (18), e compare:

```bash
psql "<url do Neon>" -f deploy/schema-fingerprint.sql > prod.txt

docker run -d --name fp -e POSTGRES_PASSWORD=x postgres:18
for f in $(ls src/main/resources/db/migration/V[1-8]__*.sql | sort -V); do
  docker exec -i fp psql -q -v ON_ERROR_STOP=1 -U postgres < "$f"
done
docker exec -i fp psql -U postgres < deploy/schema-fingerprint.sql > novo.txt
docker rm -f fp

diff prod.txt novo.txt   # precisa sair vazio
```

Qualquer linha no `diff` é uma diferença real (algo criado à mão, por exemplo)
e precisa ser resolvida antes de seguir.

### 4. Trocar o histórico do Flyway

No banco de produção:

```sql
DROP TABLE flyway_schema_history;
```

### 5. Subir com o baseline ligado

No `deploy/.env` da VM:

- `FLYWAY_BASELINE=true` (só nesta subida);
- confira se existem `MAL_CLIENT_ID`, `BREVO_API_KEY`, `MAIL_FROM_EMAIL` e
  `FRONTEND_URL` (os códigos por email dependem do Brevo).

Com esta versão já mesclada na `master` (é a branch que a VM acompanha):

```bash
~/atualizar.sh myrank
docker compose -p myrank logs -f api
```

No log deve aparecer o baseline na versão 8 e depois a aplicação de V9, V10 e
V11. Contas que já existem ficam com o tutorial concluído, e quem estava logado
continua logado (tokens antigos, sem versão de sessão, contam como versão 0).

### 6. Desligar o baseline

Tire `FLYWAY_BASELINE` do `.env` e reinicie a API. Ligado pra sempre, ele marcaria
como "já na V8" qualquer banco com tabelas e sem histórico, sem conferir nada.

### 7. Publicar o front e testar

Publique o front (merge na `main` → Vercel) e teste: login, cadastro com código,
recuperação de senha, criar tabela de música, campos próprios, ranking unificado,
chat e IA.

### Se der errado

- **Antes do passo 4:** nada mudou no banco; é só voltar o commit na VM.
- **Depois do passo 4:** restaure o banco a partir do branch `antes-do-squash`
  no Neon, volte a VM pro commit anterior e suba de novo.

## Comandos úteis

| O quê | Comando |
|---|---|
| Ver logs | `docker compose logs -f api` |
| Reiniciar | `docker compose restart api` |
| Status | `docker compose ps` |
| Uso de memória/CPU | `docker stats` |
