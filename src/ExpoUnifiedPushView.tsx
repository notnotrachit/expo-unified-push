import { requireNativeView } from 'expo';
import * as React from 'react';

import { ExpoUnifiedPushViewProps } from './ExpoUnifiedPush.types';

const NativeView: React.ComponentType<ExpoUnifiedPushViewProps> =
  requireNativeView('ExpoUnifiedPush');

export default function ExpoUnifiedPushView(props: ExpoUnifiedPushViewProps) {
  return <NativeView {...props} />;
}
