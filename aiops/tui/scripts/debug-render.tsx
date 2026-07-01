// Render thử cây UI mới (Header + Viewport) KHÔNG cần TTY. Ép bề rộng: npx tsx scripts/debug-render.tsx <cols>
process.stdout.columns = Number(process.argv[2]) || 80;
process.stdout.rows = Number(process.argv[3]) || 40;
import { render, Box } from 'ink';
import { Header } from '../src/components/Header.js';
import { Viewport } from '../src/components/Viewport.js';
import { StatusBar } from '../src/components/StatusBar.js';
import type { State } from '../src/store/useFeed.js';

const state: State = {
  models: { current: 'claude', available: ['claude', 'minimax'] },
  live: [
    // thứ tự THỜI GIAN: cũ→mới (mới ở cuối = sát đáy)
    {
      kind: 'investigation', id: 'i1', investigationId: 'inv-1', root: 'nginx',
      alerts: [{ id: 1, service: 'nginx', type: 'ERROR_SPIKE' }, { id: 2, service: 'api', type: 'LATENCY' }],
      status: 'diagnosed',
      turns: [
        { tools: [{ name: 'getServiceLogs', arguments: { service: 'nginx', minutes: 5 } }], at: Date.now() },
        { tools: [{ name: 'inspectContainer', arguments: { name: 'nginx' } }], at: Date.now() },
      ],
      incidents: [
        { id: 12, service: 'nginx', title: 'nginx 5xx surge', severity: 'P1', rootCause: 'upstream api timeout', status: 'NEW' },
        { id: 13, service: 'api', title: 'api p99 latency', severity: 'P3', rootCause: 'db pool exhausted', status: 'ACKNOWLEDGED' },
      ],
    },
    {
      kind: 'investigation', id: 'i2', investigationId: 'inv-2', root: 'redis',
      alerts: [{ id: 3, service: 'redis', type: 'OOM' }],
      status: 'analyzing',
      turns: [{ tools: [{ name: 'queryMetrics', arguments: { query: 'redis_memory_used_bytes' } }], at: Date.now() }],
      incidents: [],
    },
    { kind: 'chat', id: 'c0', role: 'user', text: 'why nginx slow?', turns: [] },
    {
      kind: 'chat', id: 'c1', role: 'assistant', text: 'nginx upstream timeout đang gây 5xx.', streaming: false,
      turns: [{ tools: [{ name: 'getIncident', arguments: { incidentId: 12 } }], at: Date.now() }],
    },
  ],
  snapshot: [
    { id: 8, service: 'db', title: 'connection spike', severity: 'P2', status: 'NEW' },
    { id: 7, service: 'node-api', severity: 'P3', status: 'ACKNOWLEDGED' },
  ],
};

const { unmount } = render(
  <Box flexDirection="column" height={process.stdout.rows}>
    <Header />
    <Viewport state={state} selectedId={13} expandedIds={new Set([13])} expandedTurns={new Set()} />
    <StatusBar status="connected" count={state.live.length + state.snapshot.length} mode="list" />
  </Box>,
);
setTimeout(() => unmount(), 90);
