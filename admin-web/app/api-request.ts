// Allow a sleeping deployment time to start, but always release the UI.
export async function apiFetch(url: string, init?: RequestInit, timeoutMs = 90_000): Promise<Response> {
  const controller = new AbortController();
  const source = init?.signal;
  const cancel = () => controller.abort(source?.reason);
  if (source?.aborted) cancel();
  else source?.addEventListener('abort', cancel, {once: true});
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  try {
    return await fetch(url, {...init, signal: controller.signal});
  } catch (error) {
    if (timedOut) throw new Error('连接超时，请稍后重试。');
    if (controller.signal.aborted) throw error;
    throw new Error('暂时无法连接服务，请检查网络后重试。');
  } finally {
    clearTimeout(timer);
    source?.removeEventListener('abort', cancel);
  }
}
