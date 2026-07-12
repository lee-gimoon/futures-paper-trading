export class HttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'HttpError';
  }
}

export async function toHttpError(res: Response, fallback: string): Promise<HttpError> {
  try {
    const body: unknown = await res.json();
    if (
      typeof body === 'object' &&
      body !== null &&
      'message' in body &&
      typeof body.message === 'string'
    ) {
      return new HttpError(res.status, body.message);
    }
  } catch {
    // 오류 본문이 JSON이 아니면 호출부의 기본 메시지를 사용한다.
  }

  return new HttpError(res.status, fallback);
}
