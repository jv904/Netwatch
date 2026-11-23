-- Dados FICTÍCIOS de demonstração, usados nas capturas de tela do README.
-- Rede 10.20.30.0/24, MACs localmente administrados (02:00:00:...), nomes e eventos inventados.
--
-- Uso (banco vazio, depois de a API subir uma vez e o Flyway criar o schema):
--   docker exec -i <container-do-banco> psql -U netwatch -d netwatch < docs/demo/seed-demo.sql
-- Rode a API com NETWATCH_CAPTURE_ENABLED=false e NETWATCH_DISCOVERY_SCHEDULE_ENABLED=false
-- e não inicie descobertas: assim nada da rede real entra no banco de demonstração.
-- Os relatórios são emitidos pela própria API (POST /api/reports) depois do seed.

BEGIN;

-- ---------------------------------------------------------------- dispositivos
CREATE TEMP TABLE demo_devices (
    n int, ip text, name text, vendor text, dtype text, recognized boolean,
    active boolean, first_days int, last_minutes int
) ON COMMIT DROP;

INSERT INTO demo_devices VALUES
    ( 1, '10.20.30.1',  'roteador-principal', 'TP-Link',                 'ROUTER',       true,  true,  60,   2),
    ( 2, '10.20.30.2',  'switch-escritorio',  'Ubiquiti',                'SWITCH',       true,  true,  60,   2),
    ( 3, '10.20.30.3',  'ap-sala',            'Ubiquiti',                'ACCESS_POINT', true,  true,  60,   2),
    ( 4, '10.20.30.10', 'nas-demo',           'Synology',                'SERVER',       true,  true,  58,   2),
    ( 5, '10.20.30.11', 'servidor-arquivos',  'Dell Inc.',               'SERVER',       true,  true,  58,   2),
    ( 6, '10.20.30.15', 'notebook-ana',       'Lenovo',                  'NOTEBOOK',     true,  true,  45,   2),
    ( 7, '10.20.30.16', 'notebook-bruno',     'Apple, Inc.',             'NOTEBOOK',     true,  true,  45,  17),
    ( 8, '10.20.30.17', 'desktop-recepcao',   'Dell Inc.',               'COMPUTER',     true,  true,  50,   2),
    ( 9, '10.20.30.18', 'desktop-financeiro', 'Lenovo',                  'COMPUTER',     true,  true,  50,   2),
    (10, '10.20.30.20', 'impressora-sala',    'Brother Industries',      'PRINTER',      true,  true,  55,   2),
    (11, '10.20.30.21', 'impressora-andar2',  'Seiko Epson',             'PRINTER',      true,  true,  55,   2),
    (12, '10.20.30.30', 'camera-entrada',     'Axis Communications',     'IOT',          true,  true,  40,   2),
    (13, '10.20.30.31', 'camera-garagem',     'Axis Communications',     'IOT',          true,  true,  40,   2),
    (14, '10.20.30.40', 'tv-sala',            'Samsung Electronics',     'OTHER',        true,  true,  35,  62),
    (15, '10.20.30.41', 'chromecast-reuniao', 'Google, Inc.',            'IOT',          true,  true,  35,   2),
    (16, '10.20.30.50', 'celular-carla',      'Apple, Inc.',             'SMARTPHONE',   true,  true,  30,  17),
    (17, '10.20.30.51', 'celular-diego',      'Samsung Electronics',     'SMARTPHONE',   true,  true,  30,   2),
    (18, '10.20.30.52', 'tablet-demo',        'Apple, Inc.',             'TABLET',       true,  false, 25, 4300),
    (19, '10.20.30.60', 'termostato',         'Raspberry Pi Trading',    'IOT',          true,  true,  20,   2),
    (20, '10.20.30.61', 'sensor-porta',       'Espressif Inc.',          'IOT',          true,  true,  20,   2),
    (21, '10.20.30.70', NULL,                 'Xiaomi Communications',   'UNKNOWN',      false, true,   3,   2),
    (22, '10.20.30.71', NULL,                 'Espressif Inc.',          'UNKNOWN',      false, true,   1,   2),
    (23, '10.20.30.72', NULL,                 'Realtek Semiconductor',   'UNKNOWN',      false, true,  12,  17),
    (24, '10.20.30.80', 'celular-visitante',  NULL,                      'SMARTPHONE',   false, false, 9, 8000);

INSERT INTO devices (id, ip_address, mac_address, hostname, vendor, custom_name, device_type, status, recognized,
                     notes, first_seen_at, last_seen_at, created_at, updated_at)
SELECT gen_random_uuid(), ip, format('02:00:00:00:00:%s', lpad(to_hex(n), 2, '0')),
       NULL, vendor, name, dtype, CASE WHEN active THEN 'ACTIVE' ELSE 'INACTIVE' END, recognized,
       NULL,
       now() - make_interval(days => first_days),
       now() - make_interval(mins => last_minutes),
       now() - make_interval(days => first_days),
       now() - make_interval(mins => last_minutes)
FROM demo_devices;

-- ---------------------------------------------------------------- descobertas (a cada 2 dias)
INSERT INTO discovery_runs (id, cidr, status, run_trigger, started_at, finished_at, hosts_scanned,
                            devices_found, new_devices, inactivated)
SELECT gen_random_uuid(), '10.20.30.0/24', 'COMPLETED', CASE WHEN g % 3 = 0 THEN 'MANUAL' ELSE 'SCHEDULED' END,
       date_trunc('hour', now()) - make_interval(days => g * 2, hours => 0) + interval '9 hours' - interval '1 day' * 0,
       date_trunc('hour', now()) - make_interval(days => g * 2) + interval '9 hours' + interval '8.4 seconds',
       254,
       (ARRAY[22, 21, 20, 21, 19, 20, 18, 19])[g + 1],
       (ARRAY[1, 1, 0, 1, 0, 1, 0, 0])[g + 1],
       (ARRAY[1, 0, 1, 0, 0, 0, 1, 0])[g + 1]
FROM generate_series(0, 7) g;

-- A mais recente termina há pouco tempo.
UPDATE discovery_runs SET started_at = now() - interval '2 hours 10 seconds',
                          finished_at = now() - interval '2 hours'
WHERE started_at = (SELECT max(started_at) FROM discovery_runs);

-- ---------------------------------------------------------------- portas abertas
CREATE TEMP TABLE demo_ports (ip text, port int, service text, since_days int) ON COMMIT DROP;
INSERT INTO demo_ports VALUES
    ('10.20.30.1',  53,   'DNS',      60), ('10.20.30.1',  80,   'HTTP',  60), ('10.20.30.1',  443,  'HTTPS', 60),
    ('10.20.30.2',  443,  'HTTPS',    60),
    ('10.20.30.3',  22,   'SSH',      60), ('10.20.30.3',  443,  'HTTPS', 60),
    ('10.20.30.10', 22,   'SSH',      58), ('10.20.30.10', 443,  'HTTPS', 58), ('10.20.30.10', 445, 'SMB', 58),
    ('10.20.30.10', 5001, NULL,       58),
    ('10.20.30.11', 22,   'SSH',      58), ('10.20.30.11', 3389, 'RDP',    6),
    ('10.20.30.20', 80,   'HTTP',     55), ('10.20.30.20', 631,  'IPP',   55), ('10.20.30.20', 9100, NULL, 55),
    ('10.20.30.21', 80,   'HTTP',     55), ('10.20.30.21', 631,  'IPP',   55),
    ('10.20.30.30', 80,   'HTTP',     40), ('10.20.30.30', 554,  'RTSP',  40),
    ('10.20.30.31', 80,   'HTTP',     40), ('10.20.30.31', 554,  'RTSP',  40),
    ('10.20.30.41', 8008, NULL,       35), ('10.20.30.41', 8009, NULL,    35),
    ('10.20.30.60', 22,   'SSH',      20);

INSERT INTO device_ports (device_id, port, state, service, open_since, last_checked_at, last_state_change_at)
SELECT d.id, p.port, 'OPEN', p.service, now() - make_interval(days => p.since_days), now() - interval '2 hours',
       now() - make_interval(days => p.since_days)
FROM demo_ports p JOIN devices d ON d.ip_address = p.ip;

-- Uma porta que fechou recentemente (aparece no histórico).
INSERT INTO device_ports (device_id, port, state, service, open_since, last_checked_at, last_state_change_at)
SELECT id, 23, 'CLOSED', 'Telnet', NULL, now() - interval '2 hours', now() - interval '9 days'
FROM devices WHERE ip_address = '10.20.30.20';

-- Uma verificação de portas concluída por dispositivo com portas.
INSERT INTO port_scans (id, device_id, ip_address, scan_type, requested_ports, port_count, status, started_at,
                        finished_at, open_count, closed_count, filtered_count)
SELECT gen_random_uuid(), d.id, d.ip_address, 'COMMON', 'comuns', 40, 'COMPLETED',
       now() - interval '2 hours 1 minute', now() - interval '2 hours',
       count(*) FILTER (WHERE dp.state = 'OPEN'), 40 - count(*) FILTER (WHERE dp.state = 'OPEN'), 0
FROM devices d JOIN device_ports dp ON dp.device_id = d.id
GROUP BY d.id, d.ip_address;

-- ---------------------------------------------------------------- alertas
CREATE TEMP TABLE demo_alerts (
    rule_code text, severity text, status text, title text, evidence text, ip text, dkey text,
    occ int, first_days numeric, last_hours numeric, note text, dismiss text
) ON COMMIT DROP;
INSERT INTO demo_alerts VALUES
    ('RISKY_SERVICE', 'HIGH', 'OPEN',
     'Serviço de risco exposto: 3389 (RDP) em servidor-arquivos (10.20.30.11)', 'A porta 3389 aceita conexões.',
     '10.20.30.11', '3389', 3, 6, 2, NULL, NULL),
    ('UNKNOWN_DEVICE', 'MEDIUM', 'OPEN',
     'Dispositivo desconhecido na rede: 10.20.30.71 (Espressif Inc.)', 'Visto na descoberta.',
     '10.20.30.71', NULL, 1, 1, 26, NULL, NULL),
    ('RISKY_SERVICE', 'HIGH', 'ACKNOWLEDGED',
     'Serviço de risco exposto: 445 (SMB) em nas-demo (10.20.30.10)', 'A porta 445 aceita conexões.',
     '10.20.30.10', '445', 5, 20, 2, 'Compartilhamento interno da equipe; acesso restrito à rede local.', NULL),
    ('UNKNOWN_DEVICE', 'MEDIUM', 'RESOLVED',
     'Dispositivo desconhecido na rede: 10.20.30.15 (Lenovo)', 'Visto na descoberta.',
     '10.20.30.15', NULL, 1, 44, 1000, 'Notebook novo da equipe, cadastrado como notebook-ana.', NULL),
    ('RISKY_SERVICE', 'HIGH', 'RESOLVED',
     'Serviço de risco exposto: 23 (Telnet) em impressora-sala (10.20.30.20)', 'A porta 23 aceita conexões.',
     '10.20.30.20', '23', 2, 15, 216, 'Telnet desativado no painel da impressora.', NULL),
    ('UNKNOWN_DEVICE', 'MEDIUM', 'DISMISSED',
     'Dispositivo desconhecido na rede: 10.20.30.80', 'Visto na descoberta.',
     '10.20.30.80', NULL, 1, 9, 210, 'Celular de visitante na rede de convidados.', 'ACCEPTED_RISK');

INSERT INTO security_alerts (id, rule_code, severity, status, title, evidence, device_id, ip_address, dedup_key,
                             occurrences, first_seen_at, last_seen_at, status_changed_at, resolution_note,
                             dismiss_reason, suppressed)
SELECT gen_random_uuid(), a.rule_code, a.severity, a.status, a.title, a.evidence, d.id, a.ip,
       a.rule_code || ':' || d.id || coalesce(':' || a.dkey, ''), a.occ,
       now() - make_interval(secs => (a.first_days * 86400)::double precision),
       now() - make_interval(secs => (a.last_hours * 3600)::double precision),
       now() - make_interval(secs => (least(a.last_hours, a.first_days * 24) * 3600)::double precision),
       a.note, a.dismiss, false
FROM demo_alerts a JOIN devices d ON d.ip_address = a.ip;

INSERT INTO alert_notes (id, alert_id, created_at, kind, from_status, to_status, text)
SELECT gen_random_uuid(), id, status_changed_at, 'STATUS', 'OPEN', status, resolution_note
FROM security_alerts WHERE status <> 'OPEN';

-- ---------------------------------------------------------------- histórico de atividades
CREATE TEMP TABLE demo_events (
    hours_ago numeric, etype text, category text, severity text, ip text, title text
) ON COMMIT DROP;
INSERT INTO demo_events VALUES
    (26,   'DEVICE_DISCOVERED',   'DEVICE',    'WARNING', '10.20.30.71', 'Novo dispositivo 10.20.30.71 visto na descoberta'),
    (26,   'ALERT_RAISED',        'SECURITY',  'WARNING', '10.20.30.71', 'Alerta médio: Dispositivo desconhecido na rede: 10.20.30.71 (Espressif Inc.)'),
    (74,   'DEVICE_DISCOVERED',   'DEVICE',    'WARNING', '10.20.30.70', 'Novo dispositivo 10.20.30.70 visto na descoberta'),
    (75,   'DEVICE_RETURNED',     'DEVICE',    'INFO',    '10.20.30.40', 'tv-sala voltou a ser visto na descoberta'),
    (98,   'DEVICE_INACTIVE',     'DEVICE',    'INFO',    '10.20.30.52', 'tablet-demo não respondeu na descoberta'),
    (144,  'PORT_OPENED',         'PORT',      'WARNING', '10.20.30.11', 'Porta 3389 passou a aceitar conexões em 10.20.30.11'),
    (144,  'ALERT_RAISED',        'SECURITY',  'WARNING', '10.20.30.11', 'Alerta alto: Serviço de risco exposto: 3389 (RDP) em servidor-arquivos (10.20.30.11)'),
    (144,  'PORT_SCAN_COMPLETED', 'PORT',      'INFO',    '10.20.30.11', 'Verificação de portas em 10.20.30.11 concluída: 2 abertas'),
    (170,  'CAPTURE_STARTED',     'TRAFFIC',   'INFO',    '10.20.30.15', 'Captura iniciada em 10.20.30.15'),
    (169,  'TRAFFIC_NEW_SERVICE', 'TRAFFIC',   'NOTICE',  '10.20.30.10', 'Serviço SMB (445/TCP) em uso em nas-demo'),
    (169,  'TRAFFIC_NEW_SERVICE', 'TRAFFIC',   'NOTICE',  '10.20.30.41', 'Serviço 8009/TCP em uso em chromecast-reuniao'),
    (169,  'CAPTURE_FINISHED',    'TRAFFIC',   'INFO',    '10.20.30.15', 'Captura encerrada após 1 h'),
    (210,  'DEVICE_DISCOVERED',   'DEVICE',    'WARNING', '10.20.30.80', 'Novo dispositivo celular-visitante visto na descoberta'),
    (209,  'ALERT_DISMISSED',     'SECURITY',  'INFO',    '10.20.30.80', 'Alerta descartado: Dispositivo desconhecido na rede: 10.20.30.80'),
    (216,  'PORT_CLOSED',         'PORT',      'NOTICE',  '10.20.30.20', 'Porta 23 deixou de aceitar conexões em 10.20.30.20'),
    (216,  'ALERT_RESOLVED',      'SECURITY',  'INFO',    '10.20.30.20', 'Alerta resolvido: Serviço de risco exposto: 23 (Telnet) em impressora-sala (10.20.30.20)'),
    (290,  'DEVICE_DISCOVERED',   'DEVICE',    'WARNING', '10.20.30.72', 'Novo dispositivo 10.20.30.72 visto na descoberta'),
    (340,  'SETTINGS_CHANGED',    'SYSTEM',    'NOTICE',  NULL,          'Configuração alterada: Intervalo da descoberta automática'),
    (360,  'PORT_OPENED',         'PORT',      'WARNING', '10.20.30.20', 'Porta 23 passou a aceitar conexões em 10.20.30.20'),
    (410,  'TRAFFIC_NEW_SERVICE', 'TRAFFIC',   'NOTICE',  '10.20.30.30', 'Serviço RTSP (554/TCP) em uso em camera-entrada'),
    (480,  'DEVICE_RECOGNIZED',   'DEVICE',    'INFO',    '10.20.30.60', 'termostato marcado como conhecido'),
    (530,  'DEVICE_IP_CHANGED',   'DEVICE',    'NOTICE',  '10.20.30.51', 'celular-diego mudou de IP: 10.20.30.57 → 10.20.30.51'),
    (600,  'DEVICE_RECOGNIZED',   'DEVICE',    'INFO',    '10.20.30.50', 'celular-carla marcado como conhecido');

-- Ancora cada evento num horário comercial (8h–18h) do dia correspondente, para o gráfico por hora ficar realista.
INSERT INTO network_events (id, occurred_at, event_type, category, severity, device_id, ip_address, title)
SELECT gen_random_uuid(),
       date_trunc('day', now() - make_interval(secs => (e.hours_ago * 3600)::double precision))
           + make_interval(hours => 8 + ((e.hours_ago::int * 7) % 10), mins => (e.hours_ago::int * 13) % 60),
       e.etype, e.category, e.severity, d.id, e.ip, e.title
FROM demo_events e LEFT JOIN devices d ON d.ip_address = e.ip;

-- Uma conclusão de descoberta por execução.
INSERT INTO network_events (id, occurred_at, event_type, category, severity, ip_address, title, reference_id)
SELECT gen_random_uuid(), finished_at, 'DISCOVERY_COMPLETED', 'DISCOVERY', 'INFO', NULL,
       'Descoberta em ' || cidr || ' concluída: ' || devices_found || ' dispositivos', id
FROM discovery_runs;

COMMIT;
