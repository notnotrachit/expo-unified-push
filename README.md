# expo-unified-push

Expo integration of the android Unified Push library.

This library is still under development and testing, so **breaking changes** are expected.

> [!WARNING]  
> This library is only supported on Android at the moment. For iOS suport, we recommend using the [RN Push Notifications](https://github.com/react-native-push-notification/ios) library.

## API documentation

Main documentation is available at [ExpoUnifiedPushModule](https://juandjara.github.io/expo-unified-push/classes/ExpoUnifiedPushModule.html) typedoc pages.

## Installation in managed Expo projects

For [managed](https://docs.expo.dev/archive/managed-vs-bare/) Expo projects, please follow the installation instructions in the [API documentation](#api-documentation).

## Installation in bare React Native projects

For bare React Native projects, you must ensure that you have [installed and configured the `expo` package](https://docs.expo.dev/bare/installing-expo-modules/) before continuing.

### Add the package to your npm dependencies

```
npm install expo-unified-push
```

## Example integration into your app

To see an example implementation of the library, you can check the [App.tsx](./example/App.tsx) file in the [example](./example) folder.

## Contributing

Contributions are very welcome! Just make sure to keep the code style consistent with the rest of the codebase and ask before adding any new dependencies.
