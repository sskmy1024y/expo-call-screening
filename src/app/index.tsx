import { useCallback, useEffect, useState } from 'react';
import { AppState, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import {
  getStatus,
  hasOverlayPermission,
  reload,
  requestOverlayPermission,
  requestPermission,
  setCallerIdentities,
  type CallerIdStatus,
} from '../../modules/expo-call-screening';

const isIos = Platform.OS === 'ios';
const isAndroid = Platform.OS === 'android';

function describeError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

type FieldProps = {
  label: string;
  value: string;
  placeholder: string;
  keyboardType?: 'default' | 'phone-pad';
  onChangeText: (value: string) => void;
};

function Field({ label, value, placeholder, keyboardType = 'default', onChangeText }: FieldProps) {
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput
        style={styles.input}
        value={value}
        placeholder={placeholder}
        placeholderTextColor="#9aa0a6"
        keyboardType={keyboardType}
        autoCapitalize="none"
        autoCorrect={false}
        onChangeText={onChangeText}
      />
    </View>
  );
}

function Button({ title, onPress }: { title: string; onPress: () => void }) {
  return (
    <Pressable
      style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}
      onPress={onPress}>
      <Text style={styles.buttonText}>{title}</Text>
    </Pressable>
  );
}

export default function CallerIdScreen() {
  const [phoneNumber, setPhoneNumber] = useState('+819012345678');
  const [label, setLabel] = useState('田中 太郎 / Example Inc.');
  const [status, setStatus] = useState<CallerIdStatus>('unknown');
  const [hasOverlay, setHasOverlay] = useState(false);
  const [message, setMessage] = useState('');

  const refreshStatus = useCallback(async () => {
    try {
      setStatus(await getStatus());
    } catch (error) {
      setStatus('unknown');
      setMessage(`getStatus: ${describeError(error)}`);
    }
    try {
      setHasOverlay(await hasOverlayPermission());
    } catch (error) {
      setHasOverlay(false);
      setMessage(`hasOverlayPermission: ${describeError(error)}`);
    }
  }, []);

  useEffect(() => {
    void refreshStatus();
  }, [refreshStatus]);

  // The overlay permission is granted in system settings, so the app only
  // learns about it once the user comes back.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        void refreshStatus();
      }
    });
    return () => subscription.remove();
  }, [refreshStatus]);

  /** Runs a native call, reports its outcome as text and re-reads the status. */
  const run = useCallback(
    (name: string, action: () => Promise<void>) => () => {
      void (async () => {
        try {
          await action();
          setMessage(`${name}: ok`);
        } catch (error) {
          setMessage(`${name}: ${describeError(error)}`);
        }
        await refreshStatus();
      })();
    },
    [refreshStatus],
  );

  const save = run('Save caller', async () => {
    await setCallerIdentities([{ phoneNumber: phoneNumber.trim(), label: label.trim() }]);
    // iOS only picks up the new entries once the extension is reloaded.
    if (isIos) {
      await reload();
    }
  });

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.content}>
        <Text style={styles.title}>Caller ID PoC</Text>

        <Field
          label="Phone number"
          value={phoneNumber}
          placeholder="+819012345678"
          keyboardType="phone-pad"
          onChangeText={setPhoneNumber}
        />
        <Field
          label="Display name"
          value={label}
          placeholder="田中 太郎 / Example Inc."
          onChangeText={setLabel}
        />
        <Button title="Save Caller" onPress={save} />

        <View style={styles.divider} />

        <Text style={styles.sectionTitle}>Call Directory / Call Screening</Text>
        <Text style={styles.status}>Status: {status}</Text>

        {isIos && <Button title="Reload" onPress={run('Reload', reload)} />}
        <Button
          title={isIos ? 'Open Settings' : 'Request Role'}
          onPress={run('Request permission', requestPermission)}
        />

        {isAndroid && (
          <>
            <Text style={styles.status}>
              Overlay permission: {hasOverlay ? 'granted' : 'not granted'}
            </Text>
            <Button
              title="Grant Overlay Permission"
              onPress={run('Request overlay permission', requestOverlayPermission)}
            />
          </>
        )}

        <Text style={styles.message}>{message || 'No result yet.'}</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#ffffff',
  },
  content: {
    padding: 20,
    gap: 12,
  },
  title: {
    fontSize: 24,
    fontWeight: '600',
    color: '#11181c',
  },
  field: {
    gap: 4,
  },
  fieldLabel: {
    fontSize: 13,
    color: '#5f6368',
  },
  input: {
    borderWidth: 1,
    borderColor: '#d0d5dd',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    color: '#11181c',
  },
  button: {
    backgroundColor: '#208aef',
    borderRadius: 8,
    paddingVertical: 12,
    alignItems: 'center',
  },
  buttonPressed: {
    opacity: 0.7,
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600',
  },
  divider: {
    height: 1,
    backgroundColor: '#e4e7ec',
    marginVertical: 8,
  },
  sectionTitle: {
    fontSize: 17,
    fontWeight: '600',
    color: '#11181c',
  },
  status: {
    fontSize: 15,
    color: '#11181c',
  },
  message: {
    fontSize: 13,
    color: '#5f6368',
  },
});
