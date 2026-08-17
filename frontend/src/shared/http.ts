// API 호출이 실패했을 때, 상태 코드와 오류 문구를 함께 전달할 Error 클래스다.
export class HttpError extends Error { // Error를 확장하므로 try/catch에서 일반 오류처럼 잡을 수 있다.
  constructor( // new HttpError(401, '로그인이 필요합니다.')처럼 객체를 만들 때 실행된다.
    public readonly status: number, // HTTP 상태 코드. 예: 401(로그인 필요), 400(잘못된 요청), 500(서버 오류).
    message: string, // 사용자에게 보여 줄 오류 문구.
  ) {
    super(message); // 부모 Error에 message를 전달해 error.message로 읽을 수 있게 한다.
    this.name = 'HttpError'; // 콘솔에 Error 대신 HttpError라는 이름으로 표시한다.
  }
}

// toHttpError의 to는 "~로 변환한다"는 뜻으로, 실패한 HTTP 응답(Response)을 HttpError로 바꾼다.
// 실패한 HTTP 응답(Response)을 HttpError로 바꾸는 이유: API 함수는 이 오류를 throw하고, 화면 훅은 catch한다.
// status가 401이면 세션 만료로 처리하고, 그 외에는 message를 화면에 표시한다.
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
    // 본문이 JSON이 아니거나 비어 있으면, 아래 fallback 메시지를 사용한다.
  }

  // 응답 JSON에 { message: string }이 없으면 fallback을 사용한다.
  // fallback은 원래 사용할 서버 메시지를 얻지 못했을 때 대신 쓰는 "대체값"이다.
  return new HttpError(res.status, fallback);
}
