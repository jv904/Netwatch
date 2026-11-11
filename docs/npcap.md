# Captura de pacotes e o Npcap

O objetivo do NetWatch é **observar o tráfego da rede**: quem conversa com quem, quanto, com quais protocolos,
e quais equipamentos aparecem na rede. Para fazer isso de verdade, o sistema precisa **capturar pacotes**, e
para isso precisa de um driver de captura:

| Sistema | Driver |
|---|---|
| Windows | [Npcap](https://npcap.com/#download) |
| Linux / macOS | libpcap (normalmente já vem instalada) |

> **Sem o driver, o NetWatch funciona em modo limitado.** A tela Tráfego continua útil, mas não cumpre o
> objetivo completo do projeto. O monitoramento de tráfego completo exige o Npcap (ou a libpcap).

## Os dois modos

O NetWatch escolhe sozinho a fonte de dados (`netwatch.capture.mode=auto`):

| | `CONNECTIONS`, sem driver (limitado) | `PACKETS`, com Npcap (completo) |
|---|---|---|
| Como funciona | Lê a tabela de conexões do Windows (`netstat`) a cada 5 s | Captura os cabeçalhos dos pacotes na placa de rede |
| Conexões desta máquina | ✅ com programa e estado | ✅ |
| Volume de dados (bytes) | ❌ | ✅ |
| Número de pacotes | ❌ | ✅ |
| Protocolos sem conexão (ARP, ICMP, IGMP) | ❌ | ✅ |
| Broadcast/multicast da rede (DHCP, mDNS, SSDP) | ❌ | ✅ |
| Equipamentos que só aparecem no tráfego | Só os que conversam com esta máquina | ✅ todos os do segmento |
| Instalação | Nenhuma | Driver + API como administrador |

O modo `CONNECTIONS` existe para o sistema rodar onde não é possível instalar um driver. Ele é uma
**alternativa provisória**, não o funcionamento pretendido.

## O código já está preparado

Não é preciso alterar nada no código para usar a captura de pacotes:

- A dependência **pcap4j** já faz parte do backend
  ([`Pcap4jPacketSource`](../netwatch-api/src/main/java/com/netwatch/netwatch_api/traffic/capture/Pcap4jPacketSource.java)).
  Ela encontra o Npcap em `C:\Windows\System32\Npcap` sem configuração extra.
- No modo `auto`, assim que o driver carrega, as novas sessões passam a usar `PACKETS`.
- O filtro BPF restrito às redes autorizadas, a leitura de só 128 bytes por pacote (`snaplen`), a agregação
  em fluxos e a escolha da placa de rede na tela Tráfego já estão implementados e testados.
- Sem o driver, a API sobe normalmente e `GET /api/capture/status` informa o motivo.

## Como ativar

1. Baixe o instalador **apenas** do site oficial: <https://npcap.com/#download>.
2. Na instalação, marque **"Restrict Npcap driver's access to Administrators only"** (recomendado).
   A opção "WinPcap API-compatible mode" não é necessária.
3. Reinicie a API. Se marcou a restrição, rode-a como administrador.
4. Confira o modo:
   ```bash
   curl http://localhost:8080/api/capture/status
   # "mode": "PACKETS", "packetCaptureAvailable": true, "libraryVersion": "Npcap version ..."
   ```
5. Na tela **Tráfego**, escolha a placa de rede que está na rede autorizada e inicie a captura.

Para forçar um modo, defina `NETWATCH_CAPTURE_MODE=packets` ou `NETWATCH_CAPTURE_MODE=connections`.
Para desligar a observação de tráfego, defina `NETWATCH_CAPTURE_ENABLED=false`.

## Para ver a rede inteira

Mesmo com o Npcap, numa rede com **switch** cada máquina recebe só o próprio tráfego e o de
broadcast/multicast. Para observar a comunicação entre todos os equipamentos:

- configure **espelhamento de porta (SPAN/port mirroring)** no switch, apontando para a porta da máquina
  que roda o NetWatch; ou
- rode o NetWatch num equipamento por onde o tráfego passa (gateway, firewall, ponto de acesso).

## Segurança

O Npcap é mantido pelo projeto Nmap, tem código aberto, é assinado digitalmente e é o mesmo driver que o
Wireshark instala no Windows. Como todo driver de rede, ele roda no núcleo do sistema e permite que
programas com acesso a ele leiam o tráfego da placa. Por isso:

- baixe só do site oficial;
- restrinja o acesso a administradores na instalação;
- mantenha-o atualizado e desinstale-o (Painel de Controle) se não for mais usar.

O NetWatch, por sua vez:

- lê só os cabeçalhos (endereços, portas, protocolo). O conteúdo dos pacotes nunca chega à aplicação;
- aplica um filtro no próprio driver com as redes de `netwatch.discovery.authorized-networks` e descarta
  de novo, na aplicação, o que estiver fora delas;
- guarda apenas fluxos agregados, por 7 dias (`netwatch.capture.retention-days`).

> O Npcap é gratuito para uso pessoal. Em redes de empresa, instale e capture somente com autorização
> da equipe responsável. Ferramentas de captura costumam ser detectadas pelas soluções de segurança.
