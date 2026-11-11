# NetWatch API

Backend da plataforma NetWatch — monitoramento e análise de segurança de redes.

## Executando localmente

Pré-requisitos: Java 21 e Docker.

```bash
docker compose up -d          # PostgreSQL 17 em localhost:5432 (netwatch/netwatch)
./mvnw spring-boot:run        # API em http://localhost:8080
./mvnw test                   # testes (usam H2 em memória, não precisam do Docker)
```

Variáveis de ambiente opcionais: `NETWATCH_DB_URL`, `NETWATCH_DB_USER`, `NETWATCH_DB_PASSWORD`,
`NETWATCH_PORT`, `NETWATCH_CORS_ORIGINS` (padrão `http://localhost:4200`, para o Angular).

O esquema do banco é versionado com Flyway em `src/main/resources/db/migration` (V1 a V10).

### Imagem Docker

O [`Dockerfile`](Dockerfile) compila e gera a imagem (Java 21 JRE, com libpcap e verificação de saúde):

```bash
docker build -t netwatch-api .
```

Para subir o sistema completo (banco + API + tela), use o `docker-compose.yml` da raiz do repositório.

## Estrutura

```
com.netwatch.netwatch_api
├── common/       erros padronizados (RFC 9457) e paginação
├── config/       CORS, Clock
├── device/       inventário de dispositivos (Fase 2)
├── discovery/    descoberta automática na rede autorizada (Fase 3)
├── ports/        verificação de portas e serviços (Fase 4)
├── traffic/      tráfego: sessões, fluxos e estatísticas (Fase 5)
├── activity/     histórico de eventos e análise de atividades (Fase 6)
├── security/     motor de segurança: regras e alertas (Fases 7 e 8)
├── reports/      relatórios: conteúdo, PDF, CSV e emissão automática (Fase 10)
├── settings/     configurações editáveis pela tela (Fase 11)
└── dashboard/    visão geral para a tela inicial
```

Cada módulo funcional tem seu próprio pacote. Os módulos se comunicam por eventos (`ActivityEvent`): a coleta
(dispositivos, portas, tráfego) publica, o histórico grava, o motor de segurança avalia e gera alertas.

## API — Dispositivos

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/devices` | Lista paginada. Filtros: `status`, `type`, `recognized`, `q` (busca em IP/MAC/nome/fabricante), `page`, `size`, `sort` |
| GET | `/api/devices/summary` | Indicadores para o Dashboard (total, ativos, inativos, desconhecidos) |
| GET | `/api/devices/{id}` | Detalhes |
| POST | `/api/devices` | Cadastro manual (já nasce reconhecido) |
| PUT | `/api/devices/{id}` | Atualiza nome, tipo, reconhecimento e observações |
| PATCH | `/api/devices/{id}/recognition` | Marca como conhecido/desconhecido |
| DELETE | `/api/devices/{id}` | Remove |

Erros seguem o formato Problem Details (`title`, `detail`, `status` e, para validação, `errors`).

### Integração com a coleta

`DeviceService.registerObservation(...)` é o ponto de entrada para a descoberta automática:
identifica o equipamento pelo MAC (ou pelo IP, se o MAC não for conhecido), atualiza a última
identificação e cria novos equipamentos como **não reconhecidos**. O retorno informa se o
equipamento é novo ou mudou de IP — base para os eventos e alertas das próximas fases.

## API — Descoberta de dispositivos

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/discovery/networks` | Redes que podem ser verificadas |
| POST | `/api/discovery/runs` | Inicia uma descoberta (corpo opcional `{"cidr":"192.168.0.0/24"}`) → 202 |
| GET | `/api/discovery/runs` | Histórico de execuções |
| GET | `/api/discovery/runs/current` | Execução em andamento (204 se nenhuma) |
| GET | `/api/discovery/runs/{id}` | Andamento e resultado de uma execução |

Como funciona: sonda cada IP da faixa (ping + tentativa de conexão TCP em portas comuns — conexão
recusada também indica que o equipamento existe), lê a tabela ARP do sistema (que revela até os
equipamentos que bloqueiam ping), identifica o fabricante pelo MAC (base pública do IEEE em
`src/main/resources/oui/oui.tsv`) e envia tudo para o inventário. Equipamentos novos entram como
**não reconhecidos**; os que não responderam na faixa passam a **inativos**.

Segurança e limites:
- Só redes **privadas** (10/8, 172.16/12, 192.168/16), contidas nas redes autorizadas e com até
  `max-hosts` endereços (padrão 1024). Sem configuração, as redes autorizadas são as sub-redes
  das interfaces desta máquina; para restringir: `NETWATCH_DISCOVERY_AUTHORIZED_NETWORKS=192.168.0.0/24`.
- Uma descoberta por vez. Descoberta periódica: `NETWATCH_DISCOVERY_SCHEDULE_ENABLED=true` (a cada 15 min).
- Não exige privilégios de administrador. A API precisa rodar **diretamente na máquina** (não em
  container), pois dentro do Docker/WSL a tabela ARP não enxerga a rede local.

## API — Portas e serviços

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/devices/{id}/port-scans` | Inicia verificação: `{"type":"COMMON"}` (31 portas comuns) ou `{"type":"CUSTOM","ports":"22,80,8000-8100"}` (até 1024) → 202 |
| GET | `/api/devices/{id}/port-scans` | Histórico de verificações do dispositivo |
| GET | `/api/devices/{id}/ports` | Situação atual das portas + última verificação + se há uma em andamento |
| GET | `/api/port-scans/{id}` | Resultado detalhado (com `previousState` e `newlyOpened`) |
| GET | `/api/ports` | Portas de todos os dispositivos. Filtros: `state` (OPEN/CLOSED/FILTERED), `port`, `q` |
| GET | `/api/ports/services` | Serviços mais expostos (portas abertas agrupadas) |

Cada porta é classificada por uma tentativa de conexão TCP (sem enviar dados): **OPEN** (aceitou),
**CLOSED** (recusou) ou **FILTERED** (sem resposta conclusiva). Só é possível verificar dispositivos do
inventário que estejam em redes autorizadas; uma verificação por dispositivo de cada vez. Uma porta que
estava fechada e passou a aceitar conexões é marcada `newlyOpened` — base da regra "serviço inesperado"
do motor de segurança.

## API — Tráfego

O tráfego tem duas fontes possíveis, escolhidas automaticamente (`netwatch.capture.mode=auto`):

| Modo | Quando | O que mostra |
|---|---|---|
| `PACKETS` | Npcap (Windows) ou libpcap instalado — funcionamento pretendido | Pacotes, volume, broadcast/multicast da rede e conexões desta máquina |
| `CONNECTIONS` | Sem driver — modo limitado, alternativa provisória | Conexões TCP/UDP desta máquina pela tabela do sistema (`netstat`): com quem conversa, portas, serviço, estado e programa. Sem volume nem pacotes |

No modo `CONNECTIONS` nada precisa ser instalado e não é preciso ser administrador. A tabela é lida a
cada `connection-poll-interval` (5 s). Conexões em LISTENING e de loopback ficam de fora, e a direção do
fluxo é deduzida das portas em escuta (entrada = alguém acessando um serviço desta máquina). Vizinhos da
sub-rede local que conversam com a máquina são identificados pelo MAC na tabela ARP e entram no inventário.
O gráfico mostra o pico de conexões simultâneas, e os rankings usam o número de fluxos.

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/capture/status` | `mode` em uso, fontes disponíveis, motivo/limitação, redes filtradas, sessão em andamento e última |
| GET | `/api/capture/interfaces` | Placas de rede para captura de pacotes (vazio no modo conexões); `eligible` = tem endereço em rede autorizada |
| POST | `/api/capture/sessions` | Inicia uma sessão: `{"interfaceName":"...","durationMinutes":60}` (ambos opcionais) → 202. 409 se já houver uma; 503 sem nenhuma fonte |
| POST | `/api/capture/sessions/{id}/stop` | Para a captura (termina em até meio segundo) |
| GET | `/api/capture/sessions` | Histórico de sessões |
| GET | `/api/capture/sessions/{id}` | Uma sessão |
| GET | `/api/traffic/flows` | Fluxos. Filtros: `ip` (origem ou destino), `deviceId`, `protocol`, `port`, `window` (1h/24h/7d) |
| GET | `/api/traffic/summary?window=1h` | Totais, série temporal, protocolos, hosts e serviços com mais tráfego |
| GET | `/api/devices/{id}/traffic?window=24h` | Enviado/recebido, com quem mais conversou e fluxos recentes |

**Captura de pacotes (recomendada; necessária para o monitoramento completo).** Veja [docs/npcap.md](../docs/npcap.md). Usa o pcap4j e precisa do driver **Npcap** no Windows
([npcap.com](https://npcap.com/#download); o uso corporativo pode exigir a licença OEM) ou da **libpcap**
no Linux/macOS. Com o driver instalado, o modo `auto` passa a usá-lo sem outra configuração. A captura
costuma exigir que a API rode como administrador (ou, no Npcap, que ele tenha sido instalado sem a opção
"restrict to administrators").

**Privacidade e escopo (modo pacotes).**
- Só os primeiros 128 bytes de cada pacote (`snaplen`) chegam à aplicação: Ethernet, IPv4, TCP/UDP e ARP.
  O conteúdo nunca é lido nem gravado.
- O filtro BPF montado a partir de `netwatch.discovery.authorized-networks` (ex.: `net 192.168.100.0/24`)
  roda no driver. A aplicação confere de novo e descarta o que não tem origem nem destino em rede autorizada.
- Os pacotes são agregados em **fluxos** (protocolo + IP/porta de origem e destino) e em totais por minuto,
  gravados a cada 10 s e apagados após `retention-days` (7).
- Os equipamentos da sub-rede local vistos no tráfego passam por `registerObservation`, como na descoberta.

**Limitação.** Numa rede com switch, uma máquina só vê o próprio tráfego e o de broadcast/multicast
(ARP, DHCP, mDNS, SSDP). Para observar a rede inteira é preciso espelhamento de porta (SPAN) no switch.

Configurações em `netwatch.capture.*`: `enabled` (`NETWATCH_CAPTURE_ENABLED`, padrão `true`),
`mode` (`NETWATCH_CAPTURE_MODE`: `auto`, `packets` ou `connections`), `connection-poll-interval`,
`default-interface` (`NETWATCH_CAPTURE_INTERFACE`), `snaplen`, `promiscuous`, `flush-interval`, `flow-timeout`,
`default-duration`, `max-duration`, `retention-days`.

## API — Atividades (histórico de eventos)

Os módulos publicam eventos (`ActivityEvent`) que são gravados no histórico (`network_events`) na mesma
transação da operação. Eles são a base do motor de segurança e dos alertas das próximas fases.

| Categoria | Eventos (importância) |
|---|---|
| Dispositivos | novo dispositivo (**importante**), IP alterado (atenção), voltou a aparecer, ficou inativo, cadastro manual, marcado como conhecido/desconhecido (atenção), removido |
| Portas | porta passou a aceitar conexões (**importante**), deixou de aceitar (atenção), verificação concluída, falha (atenção) |
| Descoberta | concluída, falha (atenção) |
| Tráfego | captura iniciada/encerrada, falha (atenção), serviço visto pela primeira vez em um dispositivo da rede (atenção) |

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/events` | Histórico paginado. Filtros: `type`, `category`, `severity`, `deviceId`, `q` (título, detalhes, IP), `window` (24h/7d/30d) |
| GET | `/api/events/summary?window=7d` | Totais por importância, linha do tempo, eventos por tipo, distribuição por hora do dia e dispositivos mais ativos |
| GET | `/api/devices/{id}/events` | Histórico de um dispositivo |

Eventos de dispositivos removidos continuam no histórico (sem o vínculo). Retenção: `netwatch.activity.retention-days` (90).

## API — Motor de segurança

Regras avaliam os eventos (depois que a operação que os gerou é gravada) e o tráfego (a cada minuto) e geram
**alertas**. Um alerta ainda ativo não é repetido: novas ocorrências somam em `occurrences`.

| Regra | Gravidade padrão | Dispara quando | Parâmetros |
|---|---|---|---|
| Dispositivo desconhecido | média | equipamento novo ainda não reconhecido | — |
| Serviço de risco exposto | alta | porta sensível aberta (verificação de portas ou tráfego) | `ports` |
| Porta nova em dispositivo conhecido | média | porta passou a aceitar conexões em equipamento reconhecido | — |
| Conflito de IP / ARP spoofing | alta (**crítica** se for IP crítico) | IP em uso aparece com outro MAC em até 30 min | `criticalIps` (vazio = finais .1 e .254) |
| Varredura de portas ou de rede | alta | origem tenta ≥ N portas de um destino ou ≥ N equipamentos da rede | `portThreshold`, `hostThreshold`, `windowMinutes` |
| Conexão em porta suspeita | alta | tráfego para portas de backdoor, IRC, Tor... | `ports` |
| Acesso administrativo vindo de fora | alta | IP fora das redes autorizadas acessa SSH/RDP/SMB/VNC | `ports` |
| Atividade fora do horário | média | dispositivo, porta ou serviço novo no período configurado | `start`, `end` (horas) |

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/security/rules` | Regras, configuração atual, valores de fábrica e alertas ativos de cada uma |
| PUT | `/api/security/rules/{code}` | `{"enabled":true,"severity":"HIGH","params":{"ports":"22,3389"}}`; parâmetros omitidos voltam ao padrão |
| GET | `/api/security/alerts` | Alertas. Filtros: `status`, `severity`, `rule`, `deviceId` |
| GET | `/api/security/summary` | Alertas ativos por gravidade e por regra, e os mais recentes |

Cada alerta traz a evidência e a recomendação da regra.

## API — Gerenciamento de alertas

Ciclo de vida: **aberto → reconhecido → resolvido**, ou **descartado** (falso positivo, risco aceito, outro motivo).
Resolvidos e descartados podem ser **reabertos**. Toda mudança e todo comentário ficam na linha do tempo do alerta,
e abrir, resolver e descartar também aparecem no histórico de atividades (categoria Segurança).

- **Não alertar novamente:** um alerta descartado com `suppress` absorve as novas ocorrências do mesmo problema
  (só soma em `occurrences`), em vez de abrir alerta novo.
- **Resolução automática:** reconhecer o dispositivo resolve "Dispositivo desconhecido"; uma porta que deixa de
  aceitar conexões resolve "Serviço de risco" e "Porta nova" daquela porta; remover o dispositivo resolve os
  alertas dele.

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/security/alerts` | Filtros: `status`, `state` (`active` = abertos + reconhecidos, `closed`), `severity`, `rule`, `deviceId`, `q` |
| GET | `/api/security/alerts/counts` | Totais por situação, ativos por gravidade e `lastRaisedAt` (contador do menu e avisos) |
| GET | `/api/security/alerts/{id}` | Alerta e linha do tempo |
| POST | `/api/security/alerts/{id}/acknowledge` | `{"note":"..."}` opcional |
| POST | `/api/security/alerts/{id}/resolve` | `{"note":"..."}` opcional |
| POST | `/api/security/alerts/{id}/dismiss` | `{"reason":"FALSE_POSITIVE","note":"...","suppress":true}` |
| POST | `/api/security/alerts/{id}/reopen` | `{"note":"..."}` opcional |
| POST | `/api/security/alerts/{id}/comments` | `{"text":"..."}` → 201 |
| POST | `/api/security/alerts/bulk` | `{"ids":[...],"action":"ACKNOWLEDGE|RESOLVE|DISMISS","note":"...","reason":"...","suppress":false}` → quantos mudaram e quantos foram ignorados |

Uma ação que a situação atual não permite (ex.: resolver um alerta já resolvido) responde **409**.

## API — Relatórios

Cinco tipos: **geral da rede** (`OVERVIEW`), **inventário** (`INVENTORY`), **exposição de portas** (`EXPOSURE`),
**segurança e alertas** (`SECURITY`) e **tráfego** (`TRAFFIC`). Cada relatório emitido guarda o conteúdo do momento
(seções com indicadores, gráfico e tabelas, em JSON), então o histórico não muda quando os dados mudam. A tela, o
PDF (OpenPDF) e o CSV são gerados a partir desse mesmo conteúdo.

Períodos: `LAST_24H`, `LAST_7D`, `LAST_30D`, `PREVIOUS_WEEK` (segunda a domingo), `PREVIOUS_MONTH` e `CUSTOM`
(`from`/`to`, datas locais, até 1 ano). O relatório geral é emitido **automaticamente** toda segunda às 6h (semana
anterior) e todo dia 1º às 6h30 (mês anterior), sem duplicar se a API reiniciar.

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/reports/types` | Tipos com título e descrição |
| POST | `/api/reports` | `{"type":"SECURITY","period":"PREVIOUS_WEEK"}` ou `{"type":"OVERVIEW","period":"CUSTOM","from":"2026-11-01","to":"2026-11-07"}` → 201 |
| GET | `/api/reports` | Histórico. Filtro: `type` |
| GET | `/api/reports/{id}` | Relatório e conteúdo |
| GET | `/api/reports/{id}/pdf` | PDF (A4; paisagem quando há tabelas largas) |
| GET | `/api/reports/{id}/csv` | CSV para o Excel (`;`, UTF-8 com BOM); `?table=N` exporta só a tabela N |
| DELETE | `/api/reports/{id}` | Exclui → 204 |

Configurações em `netwatch.reports.*`: `weekly` (`NETWATCH_REPORTS_WEEKLY`), `monthly` (`NETWATCH_REPORTS_MONTHLY`)
e `retention-days` (365).

## API — Configurações

Configurações editáveis pela tela, que **valem na hora**, sem reiniciar. O valor de fábrica vem do
`application.yaml` (ou da variável de ambiente); o que for alterado fica na tabela `app_settings`. Toda alteração
aparece no histórico de atividades (categoria Sistema).

| Grupo | Configurações (chave) |
|---|---|
| Descoberta e escopo | `discovery.authorized-networks` (faixas privadas; vazio = placas de rede), `discovery.schedule.enabled`, `discovery.schedule.interval-minutes` |
| Verificação de portas | `ports.timeout-ms` |
| Tráfego | `capture.enabled`, `capture.mode` (`auto`, `packets`, `connections`), `capture.connection-poll-seconds`, `capture.default-duration-minutes`, `capture.max-duration-minutes` |
| Relatórios automáticos | `reports.weekly`, `reports.monthly` |
| Retenção de dados | `capture.retention-days`, `activity.retention-days`, `reports.retention-days` |

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/settings` | Cada configuração: valor em uso, valor de fábrica, se foi alterada, limites e opções |
| PUT | `/api/settings` | `{"capture.mode":"connections","ports.timeout-ms":"3000"}`; valida tudo antes de gravar (tudo ou nada) |
| DELETE | `/api/settings/{chave}` | Volta ao valor do application.yaml |
| GET | `/api/settings/system` | Java, sistema, banco, fuso, tempo no ar, redes em uso, fonte de tráfego e quantidade de dados guardados |

As regras do motor de segurança têm sua própria configuração em `/api/security/rules`.

## API — Dashboard

`GET /api/dashboard/overview` — indicadores de dispositivos, portas abertas, última descoberta,
histórico das descobertas e principais fabricantes, em uma única chamada.

## Frontend

A interface Angular fica em [`../netwatch-web`](../netwatch-web).

## Roadmap

- [x] Fase 1 — Estrutura do projeto
- [x] Fase 2 — Cadastro e gerenciamento de dispositivos
- [x] Fase 3 — Descoberta de dispositivos
- [x] Fase 4 — Verificação de portas
- [x] Fase 5 — Tráfego: tabela de conexões (sem driver) ou captura de pacotes (Npcap/libpcap)
- [x] Fase 6 — Análise de atividades / histórico de eventos
- [x] Fase 7 — Motor de segurança
- [x] Fase 8 — Alertas
- [x] Fase 9 — Interface Angular (telas de todas as fases)
- [x] Fase 10 — Relatórios (tela, PDF, CSV e emissão automática)
- [x] Fase 11 — Configurações editáveis pela tela
- [x] Fase 12 — Testes, Docker e documentação
