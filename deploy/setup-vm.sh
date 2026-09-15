#!/usr/bin/env bash
# Prepara uma VM Ubuntu nova da Oracle pra rodar o MyRank. Rode UMA vez:
#   bash deploy/setup-vm.sh
# Depois saia e entre de novo no SSH (pro grupo docker valer).
set -euo pipefail

# ── Docker ────────────────────────────────────────────────────────────────
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

# ── Firewall do próprio Ubuntu ────────────────────────────────────────────
# As imagens Ubuntu da Oracle vêm com iptables bloqueando tudo menos SSH.
# Liberar a porta só na "Security List" do painel NÃO basta — tem que ser aqui também.
open_port() {
  sudo iptables -C INPUT -p "$1" --dport "$2" -j ACCEPT 2>/dev/null ||
    sudo iptables -I INPUT -p "$1" --dport "$2" -j ACCEPT
}
open_port tcp 80
open_port tcp 443
open_port udp 443

sudo apt-get install -y iptables-persistent >/dev/null
sudo netfilter-persistent save

echo "Pronto. Saia do SSH e entre de novo antes de usar o docker."
