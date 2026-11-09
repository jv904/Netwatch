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
| `/devices/:id` | Identificação, edição (nome, tipo, observações), conhecido/desconhecido, exclusão e **verificação de portas** |
| `/discovery` | Redes autorizadas, iniciar descoberta, acompanhamento em tempo real e histórico |
| `/ports` | Serviços mais expostos e portas de todos os dispositivos |

Monitoramento, Alertas, Relatórios e Configurações aparecem no menu como "Em breve" até as
respectivas fases serem implementadas — nenhuma tela usa dados fictícios.

## Estrutura

```
src/app/
├── core/       modelos da API, serviço HTTP, avisos (toasts), tratamento de erros, formatação pt-BR
├── layout/     menu lateral + barra superior (busca global e "Nova descoberta")
├── shared/     ícones, cards de indicador, chips de status, gráficos (área e barras), paginação
└── features/   dashboard, devices, discovery, ports
```

Componentes standalone com signals, sem zone.js e sem bibliotecas de UI. Os gráficos são SVG/HTML
próprios (linha de 2px, preenchimento leve, dica ao passar o mouse e navegação por teclado).
