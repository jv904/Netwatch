# NetWatch

Plataforma de monitoramento e análise de segurança de redes. Ela reúne em um só lugar os dispositivos
conectados à rede, os serviços que eles expõem, o tráfego observado e, nas próximas fases, as
atividades suspeitas e os alertas.

> O NetWatch é uma ferramenta de **observação**, não um antivírus nem um firewall.
> Use-o apenas em redes que você tem autorização para analisar.

## Estrutura

| Pasta | Conteúdo |
|---|---|
| [`netwatch-api/`](netwatch-api) | Backend — Java 21, Spring Boot 4, PostgreSQL, Flyway |
| [`netwatch-web/`](netwatch-web) | Frontend — Angular 22 (tema escuro, sem bibliotecas de UI) |

## Executando

Pré-requisitos: Java 21, Node 24+ e Docker. Para o monitoramento de tráfego completo, também o
[Npcap](https://npcap.com/#download) no Windows (ou a libpcap no Linux/macOS). Sem ele, a tela Tráfego
funciona em modo limitado. Veja [docs/npcap.md](docs/npcap.md).

```bash
# 1. Banco de dados
cd netwatch-api
docker compose up -d

# 2. API (http://localhost:8080) — rodar direto na máquina, não em container,
#    para que a descoberta enxergue a rede local
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run

# 3. Frontend (http://localhost:4200), em outro terminal
cd netwatch-web
npm install
npm start
```

Detalhes de cada parte (rotas da API, configurações e telas) estão nos READMEs de
[`netwatch-api`](netwatch-api/README.md) e [`netwatch-web`](netwatch-web/README.md).

## Funcionalidades

- **Dashboard**: indicadores da rede, histórico de descobertas e principais fabricantes.
- **Dispositivos**: inventário com IP, MAC, fabricante, nome e situação, e marcação como conhecido ou desconhecido.
- **Descoberta**: varredura das redes privadas autorizadas (ping, TCP e tabela ARP) sem exigir privilégios de administrador.
- **Portas e serviços**: verificação de portas comuns ou específicas, com histórico e detecção de portas que passaram a aceitar conexões.
- **Tráfego**: captura de pacotes (volume, protocolos, broadcast/multicast e equipamentos vistos no tráfego), restrita às redes autorizadas e só com metadados. **Exige o Npcap (Windows) ou a libpcap.** Sem o driver, o sistema roda em modo limitado, com as conexões desta máquina lidas da tabela do sistema. Detalhes em [docs/npcap.md](docs/npcap.md).
- **Atividades**: histórico de tudo o que muda na rede (equipamentos novos, ausentes ou com outro IP, portas que passaram a aceitar conexões, serviços vistos no tráfego, descobertas e capturas), com linha do tempo, tipos, horários e dispositivos mais ativos.

## Roadmap

- [x] 1. Estrutura do projeto
- [x] 2. Cadastro e gerenciamento de dispositivos
- [x] 3. Descoberta de dispositivos
- [x] 4. Verificação de portas
- [x] 5. Captura de pacotes
- [x] 6. Análise de atividades e histórico de eventos
- [ ] 7. Motor de segurança
- [ ] 8. Gerenciamento de alertas
- [x] 9. Interface Angular (parcial: telas das fases 1 a 6; cada fase nova entra com a sua tela)
- [ ] 10. Relatórios
- [ ] 11. Configurações
- [ ] 12. Testes e documentação finais

## Testes

```bash
cd netwatch-api && ./mvnw test        # backend
cd netwatch-web && npm test           # frontend
```
