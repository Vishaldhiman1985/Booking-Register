export const DEVICE_STATUS_ACTIVE = "ACTIVE" as const;
export const DEVICE_STATUS_LOGGED_OUT = "LOGGED_OUT" as const;
export const DEVICE_STATUS_REPLACED = "REPLACED" as const;
export const DEVICE_STATUS_LOST = "LOST" as const;

export type DeviceSessionStatus =
  | typeof DEVICE_STATUS_ACTIVE
  | typeof DEVICE_STATUS_LOGGED_OUT
  | typeof DEVICE_STATUS_REPLACED
  | typeof DEVICE_STATUS_LOST;

export type DeviceClaimDecision =
  | "ACTIVATE"
  | "REFRESH"
  | "BLOCKED";

export function normalizeDeviceId(value: unknown): string {
  const deviceId = String(value ?? "").trim();

  if (!deviceId) {
    throw new Error("deviceId is required.");
  }

  if (deviceId.length > 128 || deviceId.includes("/")) {
    throw new Error("deviceId is invalid.");
  }

  return deviceId;
}

export function decideDeviceClaim(
  activeDeviceId: unknown,
  requestedDeviceId: unknown
): DeviceClaimDecision {
  const requested = normalizeDeviceId(requestedDeviceId);
  const active = String(activeDeviceId ?? "").trim();

  if (!active) {
    return "ACTIVATE";
  }

  if (active === requested) {
    return "REFRESH";
  }

  return "BLOCKED";
}