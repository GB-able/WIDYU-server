import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 50,           // 동시에 50명
  duration: '180s',   // 60초 동안 부하
  thresholds: {
    http_req_duration: ['p(95)<200'], // p95 < 200ms 목표
    http_req_failed: ['rate<0.01'],   // 실패율 1% 미만
  },
};

export default function () {
  const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
  const res = http.get(`${BASE_URL}/test`);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response body is ok': (r) => r.body === 'ok',
  });

  sleep(0.1);
}

// Summary handler
export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
  };
}

function textSummary(data, options) {
  const indent = options?.indent || '';
  const enableColors = options?.enableColors || false;

  const summary = [
    '',
    `${indent}============================================`,
    `${indent}K6 Load Test Results`,
    `${indent}============================================`,
    `${indent}VUs: ${data.metrics.vus?.values?.value || 'N/A'}`,
    `${indent}Duration: ${data.state?.testRunDurationMs ? (data.state.testRunDurationMs / 1000).toFixed(2) + 's' : 'N/A'}`,
    `${indent}`,
    `${indent}HTTP Requests:`,
    `${indent}  Total: ${data.metrics.http_reqs?.values?.count || 0}`,
    `${indent}  Rate: ${data.metrics.http_reqs?.values?.rate?.toFixed(2) || 0} req/s`,
    `${indent}`,
    `${indent}Response Time:`,
    `${indent}  Min: ${data.metrics.http_req_duration?.values?.min?.toFixed(2) || 0}ms`,
    `${indent}  Avg: ${data.metrics.http_req_duration?.values?.avg?.toFixed(2) || 0}ms`,
    `${indent}  Med: ${data.metrics.http_req_duration?.values?.med?.toFixed(2) || 0}ms`,
    `${indent}  Max: ${data.metrics.http_req_duration?.values?.max?.toFixed(2) || 0}ms`,
    `${indent}  P90: ${data.metrics.http_req_duration?.values?.['p(90)']?.toFixed(2) || 0}ms`,
    `${indent}  P95: ${data.metrics.http_req_duration?.values?.['p(95)']?.toFixed(2) || 0}ms`,
    `${indent}  P99: ${data.metrics.http_req_duration?.values?.['p(99)']?.toFixed(2) || 0}ms`,
    `${indent}`,
    `${indent}Failed Requests: ${data.metrics.http_req_failed?.values?.rate ? (data.metrics.http_req_failed.values.rate * 100).toFixed(2) : 0}%`,
    `${indent}============================================`,
    '',
  ];

  return summary.join('\n');
}