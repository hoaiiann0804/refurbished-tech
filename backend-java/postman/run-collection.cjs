// Newman reads test credentials from the child process environment, never CLI arguments/files.
const path = require('node:path');
const newman = require(path.resolve(__dirname, '../.run/postman-runner/node_modules/newman'));
for (const key of ['POSTMAN_BASE_URL', 'POSTMAN_EMAIL', 'POSTMAN_PASSWORD', 'POSTMAN_RUN_ID']) {
  if (!process.env[key]) throw new Error(`Missing ${key}`);
}
newman.run({
  collection: require('./operations.postman_collection.json'),
  environment: { values: [
    { key: 'baseUrl', value: process.env.POSTMAN_BASE_URL },
    { key: 'email', value: process.env.POSTMAN_EMAIL },
    { key: 'password', value: process.env.POSTMAN_PASSWORD },
    { key: 'fixtureRunId', value: process.env.POSTMAN_RUN_ID }
  ] },
  reporters: [], timeoutRequest: 10000
}, (error, summary) => {
  if (error) { console.error('Newman could not execute collection'); process.exitCode = 1; return; }
  const failures = summary.run.failures;
  console.log(JSON.stringify({requests: summary.run.stats.requests.total,
    assertions: summary.run.stats.assertions.total, failures: failures.length}));
  for (const failure of failures) console.error(failure.source.name + ': ' + failure.error.name);
  process.exitCode = failures.length ? 1 : 0;
});
