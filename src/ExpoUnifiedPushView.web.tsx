import * as React from 'react';

import { ExpoUnifiedPushViewProps } from './ExpoUnifiedPush.types';

export default function ExpoUnifiedPushView(props: ExpoUnifiedPushViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
