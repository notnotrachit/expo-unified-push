import ExpoUnifiedPush, {
  checkPermissions,
  NewEndpointPayload,
  requestPermissions,
  showLocalNotification,
} from "expo-unified-push";
import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  SafeAreaView,
  ScrollView,
  Text,
  View,
} from "react-native";

function registerDeviceInformation(data: NewEndpointPayload) {
  // TODO: register device information on your backend
}

// NOTE: this variable is read from the .env.local file, not included in the repository.
// Read more about this in the documentation for the `registerDevice` method.
const SERVER_VAPID_KEY = process.env.SERVER_VAPID_KEY;

export default function App() {
  const list = ExpoUnifiedPush.getDistributors();
  const [current, setCurrent] = useState(ExpoUnifiedPush.getSavedDistributor());

  useEffect(() => {
    ExpoUnifiedPush.subscribeDistributorMessages((msg) => {
      console.log("distributor msg: ", msg);
      if (msg.type === "newEndpoint") {
        registerDeviceInformation(msg.data);
      }
    });
  }, []);

  async function checkNotificationPermissions() {
    const granted = await checkPermissions();
    if (!granted) {
      const state = await requestPermissions();
      if (state === "granted") {
        Alert.alert("Notification permissions granted");
        return true;
      } else {
        Alert.alert("Notification permissions not granted");
        return false;
      }
    }
    return false;
  }

  async function register() {
    try {
      const granted = await checkNotificationPermissions();
      if (!granted) {
        return;
      }
      await ExpoUnifiedPush.registerDevice(SERVER_VAPID_KEY, null);
      setCurrent(ExpoUnifiedPush.getSavedDistributor());
    } catch (err) {
      console.error(err);
    }
  }

  async function unregister() {
    try {
      await ExpoUnifiedPush.unregisterDevice(null);
      setCurrent(null);
    } catch (err) {
      console.error(err);
    }
  }

  async function sendNotification() {
    await showLocalNotification({
      id: Date.now(),
      title: "Test Notification",
      body: "This is a test notification",
    });
  }

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Module API Example</Text>
        <Group name="Distributors">
          <Text>Options: {JSON.stringify(list)}</Text>
          <Text>Current distributor: {JSON.stringify(current)}</Text>
        </Group>
        <Group name="Actions">
          <View style={{ gap: 10 }}>
            <Button title="Register" onPress={register} />
            <Button title="Unregister" onPress={unregister} />
            <Button title="Send test Notification" onPress={sendNotification} />
          </View>
        </Group>
      </ScrollView>
    </SafeAreaView>
  );
}

function Group(props: { name: string; children: React.ReactNode }) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupHeader}>{props.name}</Text>
      {props.children}
    </View>
  );
}

const styles = {
  header: {
    fontSize: 30,
    margin: 20,
  },
  groupHeader: {
    fontSize: 20,
    marginBottom: 20,
  },
  group: {
    margin: 20,
    backgroundColor: "#fff",
    borderRadius: 10,
    padding: 20,
  },
  container: {
    flex: 1,
    backgroundColor: "#eee",
  },
  view: {
    flex: 1,
    height: 200,
  },
};
