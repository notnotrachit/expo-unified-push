export type MessagePayload = {
  message: ArrayBuffer | string;
  decrypted: boolean;
  instance: string;
};

export type RegisteredPayload = {
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
      action: "message";
      data: MessagePayload;
    }
  | {
      action: "registered";
      data: RegisteredPayload;
    }
  | {
      action: "registrationFailed";
      data: RegistrationFailedPayload;
    }
  | {
      action: "unregistered";
      data: UnregisteredPayload;
    }
  | {
      action: "error";
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

export type Distributor = {
  id: string;
  name?: string;
  icon?: string;
  isInternal?: boolean;
  isSaved?: boolean;
  isConnected?: boolean;
};
