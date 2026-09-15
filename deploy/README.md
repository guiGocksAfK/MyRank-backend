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

## Comandos úteis

| O quê | Comando |
|---|---|
| Ver logs | `docker compose logs -f api` |
| Reiniciar | `docker compose restart api` |
| Status | `docker compose ps` |
| Uso de memória/CPU | `docker stats` |
