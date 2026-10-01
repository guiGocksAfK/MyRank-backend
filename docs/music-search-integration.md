# Integração de músicas e álbuns

A integração usa o `RestTemplate` existente, a API pública da Deezer e o iTunes como reserva. Todas as rotas abaixo usam autenticação e compartilham o limite existente de 40 requisições por minuto em `/api/external/`.

| Rota GET | Resposta |
| --- | --- |
| `/api/external/search/music?query=...` | Lista de `ExternalSearchResultDTO` |
| `/api/external/search/albums?query=...` | Lista de `ExternalSearchResultDTO` |
| `/api/external/music/{id}` | `ExternalWorkDetailsDTO` |
| `/api/external/albums/{id}` | `ExternalWorkDetailsDTO` |

As sugestões mantêm `externalId`, `title`, `posterUrl` e `releaseDate`. Os detalhes mantêm `title`, `imageUrl`, `creator` (artista), `releaseDate` e `timeMinutes`. Durações em segundos (Deezer) ou milissegundos (iTunes) são convertidas para minutos inteiros por arredondamento. A duração de um álbum do iTunes é a soma das suas faixas, arredondada ao final. Campos não informados pela base externa ficam nulos; duração ausente fica em zero. A busca da Deezer pode não informar a data de lançamento; os detalhes da faixa consultam também o álbum quando necessário.

## Dependência pendente

Por decisão do usuário, o `pom.xml` permanece fora do escopo. O build padrão precisa receber esta dependência antes de compilar ou executar a integração. A versão já é gerenciada pelo Spring Boot do projeto.

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

O cache usa Caffeine, expira após 10 minutos da gravação e tem teto global de 500 entradas, incluindo respostas e metadados usados pela reserva. Com mais de uma instância, o certo é usar um cache compartilhado, como Redis. Falhas e respostas inválidas não são armazenadas.

## Reserva

As buscas usam `/search/track` e `/search/album` na Deezer. Falhas HTTP, de conexão, corpos inválidos e erros no JSON acionam `https://itunes.apple.com/search` com `entity=song` ou `entity=album`. Uma busca válida sem resultados permanece vazia. Se as duas bases falham, a busca retorna lista vazia, seguindo o padrão das outras integrações.

Resultados da Deezer mantêm IDs numéricos; resultados do iTunes usam `itunes:{id}`. O mesmo ID retornado na sugestão deve ser enviado ao endpoint de detalhes. Detalhes da reserva usam `/lookup`, conforme a [documentação oficial do iTunes](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/LookupExamples.html).

Se a Deezer cair depois da busca, os detalhes podem usar título e artista ainda presentes no cache para encontrar a mesma obra no iTunes. A correspondência exige título e artista iguais, ignorando maiúsculas. Sem identidade conhecida ou correspondência, o endpoint retorna 503 pelo tratamento existente. Um ID da Deezer nunca é usado como ID do iTunes.

## Validação

Os testes unitários usam `RestTemplate` mockado, sem chamadas às APIs reais ou banco. Cobrem busca, detalhes, reserva, duração, codificação de consultas, cache, contratos HTTP e rate limit. Como a dependência ficou pendente, a validação usa um descritor Maven temporário e ignorado em `target/music-validation-pom.xml`, com a dependência adicionada somente para os testes.

Resultado em 01/10/2026: **43 testes, nenhuma falha, nenhum erro e nenhum teste ignorado**, com `BUILD SUCCESS`. A compilação validou também os demais arquivos de produção e testes da cópia local. Os testes de integração que exigem Postgres não foram executados.

Para repetir a validação nesta cópia local com o descritor temporário já criado:

```powershell
.\mvnw.cmd -B -ntp -f target\music-validation-pom.xml '-Dtest=DeezerServiceTest,ItunesServiceTest,MusicSearchCacheTest,ExternalMusicSearchControllerTest,ExternalMusicRateLimitFilterTest' test
```

Após adicionar a dependência no build padrão:

```powershell
.\mvnw.cmd '-Dtest=DeezerServiceTest,ItunesServiceTest,MusicSearchCacheTest,ExternalMusicSearchControllerTest,ExternalMusicRateLimitFilterTest' test
```

## Divisão sugerida de commits

Nenhum commit foi executado. Os grupos abaixo têm até cinco arquivos cada.

1. `feat: integra busca musical` — `DeezerService.java`, `ItunesService.java`, `MusicSearchCache.java`, `MusicCatalogSupport.java` (4 arquivos).
2. `feat: expõe rotas musicais` — `ExternalSearchController.java`, `RateLimitFilter.java`, este documento (3 arquivos).
3. `test: cobre busca musical` — `DeezerServiceTest.java`, `ItunesServiceTest.java`, `MusicSearchCacheTest.java`, `ExternalMusicSearchControllerTest.java`, `ExternalMusicRateLimitFilterTest.java` (5 arquivos).
