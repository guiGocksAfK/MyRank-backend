# Validação dos fluxos da fase 1

Data: 30/09/2026. Testes executados no Chrome headless com Playwright instalado no runtime local, pois o controle integrado de navegador não respondeu. Backend na porta 8081, frontend na porta 5174 e conta fictícia no banco separado `myrank_phase1_ui_20260930`. Nenhuma migração aplicada ao banco de uso normal `myrank`.

## Confirmado pela interface e pelas respostas do backend

- Login da conta fictícia.
- Cinco tabelas padrão; Séries e Animes separados.
- Criação de tabela de filme com nome genérico, sem inferência pelo nome.
- Tipo da nova obra herdado do template da tabela.
- Validação de título/nota obrigatórios e nota entre 0 e 10.
- Criação e edição da obra; tempo e nota gravados corretamente.
- Renomeação da tabela e recarga preservando tipo e nota da obra.
- Troca explícita do template da tabela: obra antiga conserva o tipo; obra nova usa o novo template.
- Tabela personalizada com Anime e item genérico, cada um com seu tipo.
- Recarga preserva os tipos dos itens mistos.
- Criação e renomeação de subcategoria, vínculo da obra, filtros e persistência após recarga.
- Seleção de tabelas do ranking unificado persistida no backend e após recarga.
- Desempate manual no celular gravado no backend e confirmado após recarga.

O primeiro teste de desempate no celular recebeu HTTP 200 de `PUT /api/master-table-groups/1/order` e a tela mudou para a ordem solicitada. O script aguardava o endpoint errado e foi corrigido para a rodada seguinte; esse primeiro resultado sozinho não confirma a persistência após recarga.

## Retomada

O usuário precisou desligar o computador por falta de bateria. A rodada `ordering.cjs` confirmou o desempate após recarga e parou em um seletor ambíguo do script: `.mr-switch` encontrou três controles (inclusive os de abas ocultas). Corrigir para um seletor visível da aba Rankings antes de retomar. A captura `ordering.png` e os scripts estão em `C:/Users/guigo/Documents/myrank-phase1-qa`. O banco de teste foi preservado para retomar sem afetar os dados reais. Backend e frontend de teste foram encerrados.

Ainda é necessário completar/documentar a verificação da ponderação por tempo, lista/grid, classificação dos criadores e preservação de `details` ao editar pela interface. A comunicação com os provedores externos reais não foi validada neste teste.

A suíte automatizada anterior passou com 51 testes, e o build do frontend passou. A validação de interface não adicionou alterações ao código do produto.
