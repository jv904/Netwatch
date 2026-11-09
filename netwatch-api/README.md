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

O esquema do banco é versionado com Flyway em `src/main/resources/db/migration`.

## Estrutura

```
com.netwatch.netwatch_api
├── common/       erros padronizados (RFC 9457) e paginação
├── config/       CORS, Clock
├── device/       inventário de dispositivos (Fase 2)
├── discovery/    descoberta automática na rede autorizada (Fase 3)
├── ports/        verificação de portas e serviços (Fase 4)
└── dashboard/    visão geral para a tela inicial
```

Cada módulo funcional (dispositivos, portas, tráfego, alertas...) terá seu próprio pacote.

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
- [x] Fase 9 (parcial) — Interface Angular para as fases 1–4 (cada fase nova já entra com sua tela)
- [ ] Fase 5 — Captura de pacotes
- [ ] Fase 6 — Análise de atividades / histórico de eventos
- [ ] Fase 7 — Motor de segurança
- [ ] Fase 8 — Alertas
- [ ] Fase 10 — Relatórios
- [ ] Fase 11 — Configurações
- [ ] Fase 12 — Testes e documentação
