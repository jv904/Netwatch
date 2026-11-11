# NetWatch Web

Frontend Angular da plataforma NetWatch — tema escuro estilo central de segurança, consumindo a
[NetWatch API](../netwatch-api) com dados reais.

## Executando

Pré-requisitos: Node 24+ e a API rodando em `http://localhost:8080` (veja o README da API).

```bash
npm install
npm start          # http://localhost:4200 — /api e /actuator são encaminhados para a API (proxy.conf.json)
npm test           # testes unitários (Vitest)
npm run build      # build de produção em dist/
```

Se a porta 4200 estiver ocupada: `npx ng serve --port 4300`.

## Telas

| Rota | Tela |
|---|---|
| `/dashboard` | Indicadores, histórico de descobertas, principais fabricantes, desconhecidos recentes |
| `/devices` | Inventário com busca, filtros (situação, reconhecimento, tipo), ordenação e paginação — filtros ficam na URL |
| `/devices/:id` | Identificação, edição (nome, tipo, observações), conhecido/desconhecido, exclusão , **verificação de portas**, **tráfego** e **histórico** |
| `/discovery` | Redes autorizadas, iniciar descoberta, acompanhamento em tempo real e histórico |
| `/ports` | Serviços mais expostos e portas de todos os dispositivos |
| `/security` | Motor de segurança: alertas ativos, detecções recentes, regras (ligar/desligar, gravidade e parâmetros) e alertas gerados |
| `/alerts` | Tratamento de alertas: abas por situação, filtros, seleção e ações em lote, painel de detalhe com linha do tempo, comentários, descarte com motivo e notificações do navegador (`?id=` abre um alerta) |
| `/activity` | Histórico de eventos com filtros, linha do tempo, eventos por tipo, horário das atividades e dispositivos mais ativos |
| `/traffic` | Observação de tráfego (iniciar/parar): conexões ou volume no tempo, hosts e serviços mais ativos, fluxos com programa e estado, sessões |

O item Alertas do menu mostra o número de alertas ativos (vermelho se houver crítico) e o sistema avisa quando
surge alerta alto ou crítico. Relatórios e Configurações aparecem como "Em breve" até as respectivas fases serem
implementadas — nenhuma tela usa dados fictícios.

## Estrutura

```
src/app/
├── core/       modelos da API, serviço HTTP, avisos (toasts), tratamento de erros, formatação pt-BR
├── layout/     menu lateral + barra superior (busca global e "Nova descoberta")
├── shared/     ícones, cards de indicador, chips de status, gráficos (área e barras), paginação
└── features/   dashboard, devices, discovery, ports, traffic, activity, security, alerts
```

Componentes standalone com signals, sem zone.js e sem bibliotecas de UI. Os gráficos são SVG/HTML
próprios (linha de 2px, preenchimento leve, dica ao passar o mouse e navegação por teclado).
