const assert = require('node:assert/strict');
const path = require('node:path');
const { test } = require('node:test');

const { assertValidCallerIdentities } = require(
  path.resolve(__dirname, '../build/callerIdentities')
);

const entry = (phoneNumber) => ({ phoneNumber, label: 'Taro Tanaka' });

test('E.164 numbers of either length pass, and an empty list is not an error', () => {
  assert.doesNotThrow(() => assertValidCallerIdentities([]));
  assert.doesNotThrow(() =>
    assertValidCallerIdentities([entry('+819012345678'), entry('+12025550123'), entry('+441632960961')])
  );
});

test('cosmetic separators are accepted, because both platforms ignore them', () => {
  for (const accepted of ['+81 90-1234-5678', '+1 (202) 555-0123', '+81.90.1234.5678']) {
    assert.doesNotThrow(
      () => assertValidCallerIdentities([entry(accepted)]),
      `expected ${JSON.stringify(accepted)} to be accepted`
    );
  }
});

test('domestic and loosely formatted numbers are rejected before anything is stored', () => {
  for (const rejected of [
    '09012345678', // the form iOS silently turns into a different number
    '819012345678', // E.164 digits without the leading `+`, which Android cannot parse
    '+0819012345678', // country code starting with zero
    '+8190', // too short
    '+8190123456789012', // too long
    '',
    'not a number',
  ]) {
    assert.throws(
      () => assertValidCallerIdentities([entry(rejected)]),
      /must be in E.164 form/,
      `expected ${JSON.stringify(rejected)} to be rejected`
    );
  }
});

test('the error names every offending index but never the numbers themselves', () => {
  const entries = [entry('+819012345678'), entry('09012345678'), entry('+12025550123'), entry('bad')];
  assert.throws(
    () => assertValidCallerIdentities(entries),
    (error) => {
      assert.match(error.message, /2 of 4 entries/);
      assert.match(error.message, /at index 1, 3/);
      assert.doesNotMatch(error.message, /09012345678/);
      assert.doesNotMatch(error.message, /bad/);
      return true;
    }
  );
});
