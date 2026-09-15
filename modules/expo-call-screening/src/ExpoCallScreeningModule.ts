import { NativeModule, requireNativeModule } from 'expo';

import type { CallerIdentity, CallerIdStatus } from './ExpoCallScreening.types';

declare class ExpoCallScreeningModule extends NativeModule {
  setCallerIdentities(entries: CallerIdentity[]): Promise<void>;
  reload(): Promise<void>;
  getStatus(): Promise<CallerIdStatus>;
  requestPermission(): Promise<void>;
  hasOverlayPermission(): Promise<boolean>;
  requestOverlayPermission(): Promise<void>;
}

export default requireNativeModule<ExpoCallScreeningModule>('ExpoCallScreening');
