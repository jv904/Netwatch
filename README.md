# NetWatch

Plataforma de monitoramento e análise de segurança de redes. Ela reúne em um só lugar os dispositivos
conectados à rede, os serviços que eles expõem, o tráfego observado, as atividades suspeitas e os alertas,
com relatórios para acompanhar tudo ao longo do tempo.

> O NetWatch é uma ferramenta de **observação**, não um antivírus nem um firewall.
> Use-o apenas em redes que você tem autorização para analisar.

## Estrutura

| Pasta | Conteúdo |
|---|---|
| [`netwatch-api/`](netwatch-api) | Backend — Java 21, Spring Boot 4, PostgreSQL, Flyway |
| [`netwatch-web/`](netwatch-web) | Frontend — Angular 22 (tema escuro, sem bibliotecas de UI) |
| [`docs/`](docs) | Documentação complementar ([captura de pacotes e Npcap](docs/npcap.md)) |

## Executando

Há duas formas. Para a descoberta e o tráfego enxergarem a rede local por completo, prefira a primeira.

### 1. Direto na máquina (recomendado)

Pré-requisitos: Java 21, Node 24+ e Docker (só para o banco). Para o monitoramento de tráfego completo,
também o [Npcap](https://npcap.com/#download) no Windows (ou a libpcap no Linux/macOS); sem ele, a tela
Tráfego funciona em modo limitado. Veja [docs/npcap.md](docs/npcap.md).

```powershell
# Terminal 1 — banco e API (http://localhost:8080)
cd netwatch-api
docker compose up -d
.\mvnw.cmd spring-boot:run          # Linux/macOS: ./mvnw spring-boot:run

# Terminal 2 — tela (http://localhost:4200), a partir da pasta raiz do repositório
cd netwatch-web
npm install
npm start
```

### 2. Tudo no Docker

Sobe banco, API e tela com um comando. Copie [`.env.example`](.env.example) para `.env` e informe a rede
autorizada (ou ajuste depois na tela Configurações):

```bash
docker compose up -d --build        # tela em http://localhost:4200
docker compose down                 # para (os dados ficam no volume netwatch-db)
```

Dentro de um container, a API não tem a tabela ARP nem a tabela de conexões do Windows da sua máquina, e a
captura de pacotes não vê a placa de rede real. A descoberta ainda encontra os equipamentos por ping/TCP, mas
**sem MAC e sem fabricante** (o que também enfraquece as regras de conflito de IP), e o tráfego fica indisponível. No **Linux**, a API pode usar a rede do próprio host, com captura de pacotes:

```bash
docker compose -f docker-compose.yml -f docker-compose.linux-host.yml up -d --build
```

O banco do Docker completo (volume `netwatch-db`) é separado do banco de desenvolvimento
(`netwatch-api/docker-compose.yml`).

Detalhes de cada parte (rotas da API, configurações e telas) estão nos READMEs de
[`netwatch-api`](netwatch-api/README.md) e [`netwatch-web`](netwatch-web/README.md).

## Funcionalidades

- **Dashboard**: indicadores da rede, histórico de descobertas, principais fabricantes, atividades recentes e situação de segurança.
- **Dispositivos**: inventário com IP, MAC, fabricante, nome e situação, marcação como conhecido ou desconhecido, e por dispositivo: portas, tráfego, alertas e histórico.
- **Descoberta**: varredura das redes privadas autorizadas (ping, TCP e tabela ARP) sem exigir privilégios de administrador, manual ou automática.
- **Portas e serviços**: verificação de portas comuns ou específicas, com histórico e detecção de portas que passaram a aceitar conexões.
- **Tráfego**: captura de pacotes (volume, protocolos, broadcast/multicast e equipamentos vistos no tráfego), restrita às redes autorizadas e só com metadados. **Exige o Npcap (Windows) ou a libpcap.** Sem o driver, o sistema roda em modo limitado, com as conexões desta máquina lidas da tabela do sistema. Detalhes em [docs/npcap.md](docs/npcap.md).
- **Atividades**: histórico de tudo o que muda na rede (equipamentos novos, ausentes ou com outro IP, portas que passaram a aceitar conexões, serviços vistos no tráfego, descobertas, capturas, alertas e configurações), com linha do tempo, tipos, horários e dispositivos mais ativos.
- **Motor de segurança**: 8 regras configuráveis (equipamento desconhecido, serviço de risco exposto, porta nova, conflito de IP/ARP spoofing, varredura, porta suspeita, acesso administrativo externo e atividade fora do horário) que geram alertas com evidência e recomendação.
- **Alertas**: reconhecer, resolver, descartar (falso positivo ou risco aceito, com opção de não alertar novamente) e reabrir, com comentários, linha do tempo, ações em lote, resolução automática quando o problema some, contador no menu e notificações do navegador.
- **Relatórios**: geral da rede, inventário, exposição de portas, segurança e tráfego, por período, na tela, em PDF e em CSV para o Excel, com histórico e emissão automática semanal e mensal.
- **Configurações**: redes autorizadas, descoberta automática, tempo de verificação de portas, observação de tráfego, relatórios automáticos e retenção de dados, alterados na tela e valendo na hora; informações do sistema e dos dados guardados.

## Roadmap

- [x] 1. Estrutura do projeto
- [x] 2. Cadastro e gerenciamento de dispositivos
- [x] 3. Descoberta de dispositivos
- [x] 4. Verificação de portas
- [x] 5. Captura de pacotes
- [x] 6. Análise de atividades e histórico de eventos
- [x] 7. Motor de segurança
- [x] 8. Gerenciamento de alertas
- [x] 9. Interface Angular (telas de todas as fases)
- [x] 10. Relatórios
- [x] 11. Configurações
- [x] 12. Testes, Docker e documentação finais

## Testes

```bash
cd netwatch-api && ./mvnw test        # backend (JUnit 5, H2 em memória)
cd netwatch-web && npm test           # frontend (Vitest)
```
