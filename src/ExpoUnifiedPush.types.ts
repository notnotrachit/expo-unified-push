export type MessagePayload = {
  message: ArrayBuffer | string;
  decrypted: boolean;
  instance: string;
};

export type NewEndpointPayload = {
  url: string;
  pubKey: string;
  auth: string;
  instance: string;
};

export type RegistrationFailedPayload = {
  reason: string;
  instance: string;
};

export type UnregisteredPayload = {
  instance: string;
};

export type ErrorPayload = {
  message: string;
  stackTrace: string;
};

export type CallbackData =
  | {
      type: "message";
      data: MessagePayload;
    }
  | {
      type: "newEndpoint";
      data: NewEndpointPayload;
    }
  | {
      type: "registrationFailed";
      data: RegistrationFailedPayload;
    }
  | {
      type: "unregistered";
      data: UnregisteredPayload;
    }
  | {
      type: "error";
      data: ErrorPayload;
    };

export type Callback = (data: CallbackData) => void;

export type Notification = {
  id: number;
  url?: string;
  title?: string;
  body?: string;
  imageUrl?: string;
  number?: number;
  silent?: boolean;
};
