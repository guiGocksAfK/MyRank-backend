# Integração de músicas e álbuns

A integração usa o `RestTemplate` existente, a API pública da Deezer e o iTunes como reserva. Todas as rotas abaixo usam autenticação e compartilham o limite existente de 40 requisições por minuto em `/api/external/`.

| Rota GET | Resposta |
| --- | --- |
| `/api/external/search/music?query=...` | Lista de `ExternalSearchResultDTO` |
| `/api/external/search/albums?query=...` | Lista de `ExternalSearchResultDTO` |
| `/api/external/music/{id}` | `ExternalWorkDetailsDTO` |
| `/api/external/albums/{id}` | `ExternalWorkDetailsDTO` |

As sugestões mantêm `externalId`, `title`, `posterUrl` e `releaseDate`. Os detalhes mantêm `title`, `imageUrl`, `creator` (artista), `releaseDate` e `timeMinutes`. Durações em segundos (Deezer) ou milissegundos (iTunes) são convertidas para minutos inteiros por arredondamento. A duração de um álbum do iTunes é a soma das suas faixas, arredondada ao final. Campos não informados pela base externa ficam nulos; duração ausente fica em zero. A busca da Deezer pode não informar a data de lançamento; os detalhes da faixa consultam também o álbum quando necessário.

## Cache

Usa Caffeine (dependência no `pom.xml`, versão gerenciada pelo Spring Boot):
expira 10 minutos depois da gravação e guarda no máximo 500 entradas, incluindo
respostas e os metadados usados pela reserva. Falhas e respostas inválidas não
são guardadas. Com mais de uma instância, o certo é um cache compartilhado (Redis).

## Card de Música e Álbum

- As sugestões trazem `subtitle` com o artista, porque muitas faixas têm o mesmo título.
- Os detalhes trazem `details`: a faixa informa `album` (de qual álbum é); o álbum
  informa `trackCount`. O front guarda isso em `works.details`.
- As capas do iTunes vêm em 100px; a URL é trocada pra pedir 600px.
- Música e álbum não usam ponderação por tempo (`TableTemplate.timeWeighted`).

## Reserva

As buscas usam `/search/track` e `/search/album` na Deezer. Falhas HTTP, de conexão, corpos inválidos e erros no JSON acionam `https://itunes.apple.com/search` com `entity=song` ou `entity=album`. Uma busca válida sem resultados permanece vazia. Se as duas bases falham, a busca retorna lista vazia, seguindo o padrão das outras integrações.

Resultados da Deezer mantêm IDs numéricos; resultados do iTunes usam `itunes:{id}`. O mesmo ID retornado na sugestão deve ser enviado ao endpoint de detalhes. Detalhes da reserva usam `/lookup`, conforme a [documentação oficial do iTunes](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/LookupExamples.html).

Se a Deezer cair depois da busca, os detalhes podem usar título e artista ainda presentes no cache para encontrar a mesma obra no iTunes. A correspondência exige título e artista iguais, ignorando maiúsculas. Sem identidade conhecida ou correspondência, o endpoint retorna 503 pelo tratamento existente. Um ID da Deezer nunca é usado como ID do iTunes.

## Validação

Testes unitários com `RestTemplate` mockado, sem chamadas às APIs reais nem banco:
busca, detalhes, reserva, duração, codificação das consultas, cache, contratos HTTP,
rate limit e os campos do card (`MusicCardDetailsTest`).

```bash
./mvnw test -Dtest='DeezerServiceTest,ItunesServiceTest,MusicSearchCacheTest,MusicCardDetailsTest,ExternalMusicSearchControllerTest,ExternalMusicRateLimitFilterTest'
```
