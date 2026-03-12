import ExpoQqLocationModule from './ExpoQqLocationModule';

// 导出类型
export * from './ExpoQqLocation.types';

export type LocationListenerSubscription = {
  remove(): void;
};

// 导出模块
export default ExpoQqLocationModule;

// 便捷方法
export function setUserAgreePrivacy(isAgree: boolean): void {
  return ExpoQqLocationModule.setUserAgreePrivacy(isAgree);
}

export function setDeviceID(deviceId: string): void {
  return ExpoQqLocationModule.setDeviceID(deviceId);
}

export function startLocationUpdates(request: import('./ExpoQqLocation.types').LocationRequest = {}): Promise<number> {
  return ExpoQqLocationModule.startLocationUpdates(request);
}

export function stopLocationUpdates(): void {
  return ExpoQqLocationModule.stopLocationUpdates();
}

export function isLocationRunning(): boolean {
  return ExpoQqLocationModule.isLocationRunning();
}

export function requestSingleFreshLocation(
  request: import('./ExpoQqLocation.types').LocationRequest = {},
): Promise<import('./ExpoQqLocation.types').SingleLocationResult> {
  return ExpoQqLocationModule.requestSingleFreshLocation(request);
}

export function hasLocationPermission(): Promise<boolean> {
  return ExpoQqLocationModule.hasLocationPermission();
}

export function requestLocationPermission(): Promise<boolean> {
  return ExpoQqLocationModule.requestLocationPermission();
}

export function getApiKey(): string {
  return ExpoQqLocationModule.getApiKey();
}

// 事件监听便捷方法
export function addLocationListener(
  listener: (event: import('./ExpoQqLocation.types').LocationChangedEvent) => void
): LocationListenerSubscription {
  return ExpoQqLocationModule.addListener('onLocationChanged', listener);
}

export function addLocationErrorListener(
  listener: (event: import('./ExpoQqLocation.types').LocationErrorEvent) => void
): LocationListenerSubscription {
  return ExpoQqLocationModule.addListener('onLocationError', listener);
}

export function addStatusUpdateListener(
  listener: (event: import('./ExpoQqLocation.types').LocationStatusEvent) => void
): LocationListenerSubscription {
  return ExpoQqLocationModule.addListener('onStatusUpdate', listener);
}

/**
 * @deprecated 仅用于全局兜底清理，不建议在普通页面调用。
 * 请优先使用 addXxxListener 返回的 subscription.remove()。
 */
export function removeAllLocationListeners() {
  ExpoQqLocationModule.removeAllListeners('onLocationChanged');
  ExpoQqLocationModule.removeAllListeners('onLocationError');
  ExpoQqLocationModule.removeAllListeners('onStatusUpdate');
}
