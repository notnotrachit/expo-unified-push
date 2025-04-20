import { registerWebModule, NativeModule } from 'expo';

import { ExpoUnifiedPushModuleEvents } from './ExpoUnifiedPush.types';

class ExpoUnifiedPushModule extends NativeModule<ExpoUnifiedPushModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
}

export default registerWebModule(ExpoUnifiedPushModule);
